package com.adt.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.sql.DataSource;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import com.adt.entity.Movie;
import com.adt.entity.dto.ConcurrentLoadResultDTO;
import com.adt.entity.dto.QueryPerformanceDTO;
import com.adt.repository.BaseRepository;

/**
 * Service, der gezielte Datenbankabfragen ausführt und deren Laufzeit als Micrometer-Metrik meldet.
 */
@ApplicationScoped
public class DatabaseMetricsService extends BaseRepository {

        @Inject
        MeterRegistry meterRegistry;

        @Inject
        DataSource dataSource;

        /**
         * Führt eine index-basierte Suche über den Primärschlüssel aus.
         */
        @Timed(value = "adt.db.query.indexed", description = "Laufzeit einer über einen Index ausgeführten Abfrage")
        public QueryPerformanceDTO measureIndexedLookup(int movieId) {
                return runTimedQuery(
                                "indexed",
                                "adt.db.query.indexed",
                                "Primärschlüssel-Suche mit vorhandenem Index",
                                () -> em.createQuery("select m from Movie m where m.id=:id", Movie.class)
                                                .setParameter("id", movieId)
                                                .getResultList()
                                                .size());
        }

        /**
         * Führt eine Volltextsuche über Titel aus, die typischerweise keinen passenden Index besitzt.
         */
        @Timed(value = "adt.db.query.full-scan", description = "Laufzeit einer Abfrage ohne unterstützenden Index")
        public QueryPerformanceDTO measureUnindexedSearch(String titleFragment) {
                String term = titleFragment == null ? "" : titleFragment.toLowerCase();
                return runTimedQuery(
                                "full-scan",
                                "adt.db.query.full-scan",
                                "Titel-Scan ohne dedizierten Index",
                                () -> em.createQuery(
                                                "select m from Movie m where lower(m.title) like concat('%', :term, '%') order by m.releaseDate",
                                                Movie.class)
                                                .setParameter("term", term)
                                                .setMaxResults(200)
                                                .getResultList()
                                                .size());
        }

        /**
         * Führt eine Abfrage mit Jahres-Extraktion aus, die typischerweise keinen Index nutzt.
         */
        @Timed(value = "adt.db.query.year-extraction", description = "Jahresfilter per Funktion, die Indexe umgehen kann")
        public QueryPerformanceDTO measureYearExtractionSearch(int year) {
                return runTimedQuery(
                                "year-extraction",
                                "adt.db.query.year-extraction",
                                "Jahresfilter via year()-Funktion auf release_date (nicht indexfreundlich)",
                                () -> em.createQuery(
                                                "select m from Movie m where year(m.releaseDate) = :year order by m.releaseDate",
                                                Movie.class)
                                                .setParameter("year", year)
                                                .setMaxResults(1000)
                                                .getResultList()
                                                .size());
        }

        /**
         * Führt eine indexfreundliche Range-Abfrage auf Basis von release_date aus.
         */
        @Timed(value = "adt.db.query.release-range", description = "Range-Query über release_date, profitiert von Index")
        public QueryPerformanceDTO measureReleaseRange(int startYear, int endYear, int limit) {
                int effectiveStartYear = Math.min(startYear, endYear);
                int effectiveEndYear = Math.max(startYear, endYear);
                LocalDate startDate = LocalDate.of(effectiveStartYear, 1, 1);
                LocalDate endDate = LocalDate.of(effectiveEndYear, 12, 31);

                return runTimedQuery(
                                "release-range",
                                "adt.db.query.release-range",
                                "Jahresbereich mit BETWEEN auf release_date und Limit",
                                () -> em.createQuery(
                                                "select m from Movie m where m.releaseDate between :start and :end order by m.releaseDate",
                                                Movie.class)
                                                .setParameter("start", startDate)
                                                .setParameter("end", endDate)
                                                .setMaxResults(Math.max(1, limit))
                                                .getResultList()
                                                .size());
        }

        /**
         * Führt eine textbasierte Suche über die Overview-Spalte aus (bewusst ohne Index).
         */
        @Timed(value = "adt.db.query.overview-scan", description = "Textscan über Overview ohne Volltextindex")
        public QueryPerformanceDTO measureOverviewScan(String term) {
                String searchTerm = term == null ? "" : term.toLowerCase();
                return runTimedQuery(
                                "overview-scan",
                                "adt.db.query.overview-scan",
                                "Volltextähnliche Suche in overview via LIKE", () -> em.createQuery(
                                                "select m from Movie m where lower(m.overview) like concat('%', :term, '%') order by m.id",
                                                Movie.class)
                                                .setParameter("term", searchTerm)
                                                .setMaxResults(300)
                                                .getResultList()
                                                .size());
        }

        /**
         * Nutzt einen kombinierten Index für eine performante Top-Rated-Abfrage.
         */
        @Timed(value = "adt.db.query.top-rated", description = "Bestbewertete Filme nach VoteAverage/VoteCount mit Limit")
        public QueryPerformanceDTO measureTopRated(int minVotes, int limit) {
                int effectiveLimit = Math.max(1, limit);
                return runTimedQuery(
                                "top-rated",
                                "adt.db.query.top-rated",
                                "Sortierung nach vote_average/vote_count mit kombinierten Index",
                                () -> em.createQuery(
                                                "select m from Movie m where m.voteCount >= :minVotes order by m.voteAverage desc, m.voteCount desc",
                                                Movie.class)
                                                .setParameter("minVotes", Math.max(0, minVotes))
                                                .setMaxResults(effectiveLimit)
                                                .getResultList()
                                                .size());
        }

        /**
         * Nutzt eine Zufallssortierung, die einen vollständigen Scan erzwingt.
         */
        @Timed(value = "adt.db.query.random-sort", description = "Zufällige Sortierung ohne Indexnutzung")
        public QueryPerformanceDTO measureRandomSort(int limit) {
                int effectiveLimit = Math.max(1, limit);
                return runTimedQuery(
                                "random-sort",
                                "adt.db.query.random-sort",
                                "ORDER BY random() erzwingt einen teuren Sort-Node",
                                () -> em.createQuery("select m from Movie m order by function('random')", Movie.class)
                                                .setMaxResults(effectiveLimit)
                                                .getResultList()
                                                .size());
        }

        /**
         * Führt eine führende Wildcard-Suche auf dem Originaltitel aus, die keinen Index nutzt.
         */
        @Timed(value = "adt.db.query.wildcard-original-title", description = "Führende Wildcard auf original_title")
        public QueryPerformanceDTO measureWildcardOriginalTitle(String term, int limit) {
                int effectiveLimit = Math.max(1, limit);
                String searchTerm = term == null ? "" : term.toLowerCase();
                return runTimedQuery(
                                "wildcard-original-title",
                                "adt.db.query.wildcard-original-title",
                                "Leading-Wildcard auf original_title um Index zu umgehen",
                                () -> em.createQuery(
                                                "select m from Movie m where lower(m.originalTitle) like concat('%', :term, '%')"
                                                                + " order by m.originalTitle", Movie.class)
                                                .setParameter("term", searchTerm)
                                                .setMaxResults(effectiveLimit)
                                                .getResultList()
                                                .size());
        }

        /**
         * Nutzt einen Index auf original_language für eine effiziente Filterung.
         */
        @Timed(value = "adt.db.query.language-filter", description = "Filterung nach original_language mit Index")
        public QueryPerformanceDTO measureLanguageFilter(String language, int limit) {
                int effectiveLimit = Math.max(1, limit);
                String lang = language == null ? "" : language.toLowerCase();
                return runTimedQuery(
                                "language-filter",
                                "adt.db.query.language-filter",
                                "Filterung auf original_language mit Limit und Sortierung",
                                () -> em.createQuery(
                                                "select m from Movie m where lower(m.originalLanguage) = :lang order by m.releaseDate desc",
                                                Movie.class)
                                                .setParameter("lang", lang)
                                                .setMaxResults(effectiveLimit)
                                                .getResultList()
                                                .size());
        }

        /**
         * Kombiniert einen Jahresfilter mit Popularität, profitiert von neuen Indexen.
         */
        @Timed(value = "adt.db.query.recent-popular", description = "Neuere Filme nach Popularität sortiert")
        public QueryPerformanceDTO measureRecentPopular(int startYear, int limit) {
                int effectiveStartYear = Math.max(1900, startYear);
                int effectiveLimit = Math.max(1, limit);
                LocalDate startDate = LocalDate.of(effectiveStartYear, 1, 1);

                return runTimedQuery(
                                "recent-popular",
                                "adt.db.query.recent-popular",
                                "Neuere Filme mit Popularitätssortierung und Limit",
                                () -> em.createQuery(
                                                "select m from Movie m where m.releaseDate >= :start order by m.releaseDate desc, m.popularity desc",
                                                Movie.class)
                                                .setParameter("start", startDate)
                                                .setMaxResults(effectiveLimit)
                                                .getResultList()
                                                .size());
        }

        /**
         * Führt mehrere Abfragen sequenziell aus, um Last auf die Datenbank zu legen.
         */
        @Timed(value = "adt.db.query.load-test", description = "Sequenzielle Last für Performance-Vergleiche")
        public QueryPerformanceDTO runLoadScenario(int iterations, int limit) {
                int effectiveIterations = Math.max(1, iterations);
                int effectiveLimit = Math.max(1, limit);
                Timer.Sample sample = Timer.start(meterRegistry);
                int totalRows = 0;

                for (int i = 0; i < effectiveIterations; i++) {
                        totalRows += measureUnindexedSearch("the").rowsReturned();
                        totalRows += measureRandomSort(effectiveLimit).rowsReturned();
                        totalRows += measureWildcardOriginalTitle("man", effectiveLimit).rowsReturned();
                        totalRows += measureLanguageFilter("en", effectiveLimit).rowsReturned();
                        totalRows += measureRecentPopular(2015, effectiveLimit).rowsReturned();
                }

                long durationNanos = sample.stop(Timer.builder("adt.db.query.load-test")
                                .description("Mehrfachaufruf diverser Queries zur Erzeugung von Last")
                                .register(meterRegistry));
                long durationMillis = Duration.ofNanos(durationNanos).toMillis();
                String description = "Sequenzieller Lasttest mit " + effectiveIterations + " Durchläufen à " + effectiveLimit
                                + " Ergebnissen pro Query";
                return new QueryPerformanceDTO("load-test", durationMillis, totalRows, description);
        }

        /**
         * Simuliert parallele Datenbankzugriffe mit frei wählbarer Nutzerzahl.
         */
        @Timed(value = "adt.db.query.concurrent-load", description = "Parallelisierung vieler kleiner DB-Reads")
        public ConcurrentLoadResultDTO runConcurrentLoad(int virtualUsers, int limitPerUser) {
                int effectiveUsers = Math.max(1, virtualUsers);
                int effectiveLimit = Math.max(1, limitPerUser);

                ExecutorService executor = Executors.newFixedThreadPool(Math.min(effectiveUsers, 2000));
                CountDownLatch latch = new CountDownLatch(effectiveUsers);
                AtomicInteger successfulQueries = new AtomicInteger();
                AtomicInteger failedQueries = new AtomicInteger();
                AtomicLong rowsRead = new AtomicLong();

                Timer.Sample sample = Timer.start(meterRegistry);

                for (int i = 0; i < effectiveUsers; i++) {
                        executor.submit(() -> {
                                try (Connection connection = dataSource.getConnection();
                                                PreparedStatement stmt = connection.prepareStatement(
                                                                "select id from movie order by id desc limit ?")) {
                                        stmt.setInt(1, effectiveLimit);
                                        try (ResultSet rs = stmt.executeQuery()) {
                                                while (rs.next()) {
                                                        rowsRead.incrementAndGet();
                                                }
                                        }
                                        successfulQueries.incrementAndGet();
                                } catch (Exception e) {
                                        failedQueries.incrementAndGet();
                                } finally {
                                        latch.countDown();
                                }
                        });
                }

                try {
                        latch.await();
                } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                } finally {
                        executor.shutdown();
                        try {
                                executor.awaitTermination(30, TimeUnit.SECONDS);
                        } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                        }
                }

                long durationNanos = sample.stop(Timer.builder("adt.db.query.concurrent-load")
                                .description("Paralleltest mit " + effectiveUsers + " virtuellen Nutzern")
                                .register(meterRegistry));
                long durationMillis = Duration.ofNanos(durationNanos).toMillis();
                String description = "Paralleler Lasttest mit " + effectiveUsers + " Nutzern und Limit " + effectiveLimit
                                + " pro Anfrage";

                return new ConcurrentLoadResultDTO(effectiveUsers, durationMillis, rowsRead.get(),
                                successfulQueries.get(), failedQueries.get(), description);
        }

        private QueryPerformanceDTO runTimedQuery(
                        String queryType,
                        String timerName,
                        String description,
                        Supplier<Integer> queryRunner) {
                Timer.Sample sample = Timer.start(meterRegistry);
                int rows = queryRunner.get();
                long durationNanos = sample.stop(Timer.builder(timerName).description(description).register(meterRegistry));
                long durationMillis = Duration.ofNanos(durationNanos).toMillis();
                return new QueryPerformanceDTO(queryType, durationMillis, rows, description);
        }
}
