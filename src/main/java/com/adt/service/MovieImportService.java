package com.adt.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.adt.entity.dto.ImportStatsDTO;

import okhttp3.HttpUrl;

/**
 * Zentraler Service, der Filme und verwandte Daten aus der TMDB-API abruft und in der Datenbank persistiert.
 */
@ApplicationScoped
public class MovieImportService {

        @Inject
        MoviePersistenceService persistenceService;

        @Inject
        TmdbClient tmdbClient;

        @ConfigProperty(name = "adt.import.max-concurrency", defaultValue = "10")
        int maxConcurrentImports;
        private Semaphore importSemaphore;

        @PostConstruct
        void initSemaphore() {
                        int permits = Math.max(1, maxConcurrentImports);
                        importSemaphore = new Semaphore(permits);
        }

        /**
         * Importiert eine durch TMDB-IDs definierte Reihe von Filmen, sammelt dabei Erfolgs- und Fehlerzähler und misst die
         * Laufzeit.
         */
        public ImportStatsDTO importMovieRangeWithStats(int startId, int endId) {
                if (endId < startId) {
                        throw new IllegalArgumentException("Parameter 'endId' must be >= 'startId'");
                }

                tmdbClient.refreshApiRateLimit();
                persistenceService.refreshMovieGenres();

                AtomicInteger imported = new AtomicInteger();
                AtomicInteger failed = new AtomicInteger();
                long start = System.currentTimeMillis();

                try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                        List<? extends Future<?>> tasks = IntStream.rangeClosed(startId, endId)
                                        .mapToObj(id -> submitImportTask(id, executor, imported, failed))
                                        .toList();
                        waitForTasks((Collection<Future<?>>) tasks);
                }

                long duration = System.currentTimeMillis() - start;
                return new ImportStatsDTO(imported.get(), failed.get(), duration);
        }

        /**
         * Durchsucht die TMDB-Discover-API nach Filmen in einem Veröffentlichungsjahresbereich und importiert alle Treffer.
         */
        public ImportStatsDTO importMoviesForYearRange(int startYear, int endYear) {
                if (startYear <= 0 || endYear <= 0) {
                        throw new IllegalArgumentException("Parameters 'startYear' and 'endYear' must be positive");
                }
                if (endYear < startYear) {
                        throw new IllegalArgumentException("Parameter 'endYear' must be >= 'startYear'");
                }

                int currentYear = LocalDate.now().getYear();
                int effectiveEndYear = Math.min(endYear, currentYear);
                int effectiveStartYear = Math.max(startYear, 1874);

                if (effectiveStartYear > effectiveEndYear) {
                        throw new IllegalArgumentException(
                                        "Requested year range is outside the supported interval (>= 1874 and <= current year)");
                }

                tmdbClient.refreshApiRateLimit();
                persistenceService.refreshMovieGenres();

                AtomicInteger imported = new AtomicInteger();
                AtomicInteger failed = new AtomicInteger();
                long start = System.currentTimeMillis();

                try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                        List<Future<?>> tasks = new ArrayList<>();

                        for (int year = startYear; year <= endYear; year++) {
                                int page = 1;
                                int totalPages = 1;
                                do {
                                        try {
                                                HttpUrl url = Objects
                                                                .requireNonNull(HttpUrl.parse("https://api.themoviedb.org/3/discover/movie"))
                                                                .newBuilder()
                                                                .addQueryParameter("language", "en-US")
                                                                .addQueryParameter("sort_by", "primary_release_date.asc")
                                                                .addQueryParameter("include_adult", "false")
                                                                .addQueryParameter("include_video", "false")
                                                                .addQueryParameter("with_release_type", "1|2|3|4|5|6|7")
                                                                .addQueryParameter("primary_release_date.gte", year + "-01-01")
                                                                .addQueryParameter("primary_release_date.lte", year + "-12-31")
                                                                .addQueryParameter("page", String.valueOf(page))
                                                                .build();

                                                JsonObject response = tmdbClient.getJson(url.toString());
                                                if (response == null) {
                                                        break;
                                                }

                                                totalPages = response.getInt("total_pages", 1);
                                                JsonArray results = response.getJsonArray("results");
                                                if (results != null) {
                                                        for (JsonValue value : results) {
                                                                JsonObject movie = value.asJsonObject();
                                                                int tmdbId = movie.getInt("id");
                                                                tasks.add(submitImportTask(tmdbId, executor, imported, failed));
                                                        }
                                                }
                                        } catch (Exception e) {
                                                failed.incrementAndGet();
                                                System.err.println(
                                                                "❌ Discover request failed for year " + year + ", page " + page + ": "
                                                                                + e.getMessage());
                                        }
                                        page++;
                                } while (page <= totalPages);
                        }

                        waitForTasks(tasks);
                }

                long duration = System.currentTimeMillis() - start;
                return new ImportStatsDTO(imported.get(), failed.get(), duration);
        }

        private Future<?> submitImportTask(int tmdbId, ExecutorService executor, AtomicInteger imported,
                        AtomicInteger failed) {
                return executor.submit(() -> {
                        boolean permitAcquired = false;
                        try {
                                importSemaphore.acquire();
                                permitAcquired = true;
                                if (importOne(tmdbId))
                                        imported.incrementAndGet();
                                else
                                        failed.incrementAndGet();
                        } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                failed.incrementAndGet();
                                System.err.println("❌ Import failed for TMDB id " + tmdbId + ": " + e.getMessage());
                        } catch (Exception e) {
                                failed.incrementAndGet();
                                System.err.println("❌ Import failed for TMDB id " + tmdbId + ": " + e.getMessage());
                        } finally {
                                if (permitAcquired)
                                        importSemaphore.release();
                        }
                });
        }

        private void waitForTasks(Collection<Future<?>> tasks) {
                for (Future<?> task : tasks) {
                        try {
                                task.get();
                        } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException("Interrupted while waiting for imports to finish", e);
                        } catch (Exception e) {
                                throw new RuntimeException("Import task failed", e);
                        }
                }
        }

        private boolean importOne(int tmdbId) throws Exception {
                JsonObject json = tmdbClient.fetchMovieDetails(tmdbId);
                if (json == null)
                        return false;

                return persistenceService.persistMovieImport(json);
        }
}
