package com.adt.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import javax.sql.DataSource;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.adt.entity.dto.ImportStatsDTO;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Zentraler Service, der Filme und verwandte Daten aus der TMDB-API abruft und in der Datenbank persistiert.
 */
@ApplicationScoped
public class MovieImportService {

        @Inject
        DataSource ds;

        private static final Duration MAX_RETRY_WAIT = Duration.ofSeconds(10);

        private final OkHttpClient http = new OkHttpClient.Builder()
                        .callTimeout(MAX_RETRY_WAIT)
                        .build();

        @ConfigProperty(name = "adt.import.max-concurrency", defaultValue = "10")
        private int maxConcurrentImports;
        private Semaphore importSemaphore;

        @PostConstruct
        void initSemaphore() {
                int permits = Math.max(1, maxConcurrentImports);
                importSemaphore = new Semaphore(permits);
        }

        private static final long DEFAULT_CALL_INTERVAL_NANOS = 1_000_000_000L / 50; // 50 calls per second
        private final java.util.concurrent.atomic.AtomicLong callIntervalNanos = new java.util.concurrent.atomic.AtomicLong(
                        DEFAULT_CALL_INTERVAL_NANOS);
        private final Object rateLimitLock = new Object();
        private long lastApiCallTime = 0L;

        /**
         * Wartet bei Bedarf, um die aus den TMDB-Headern abgeleiteten
         * Ratenlimit-Vorgaben einzuhalten.
         */
        private void awaitRateLimit() {
                synchronized (rateLimitLock) {
                        long now = System.nanoTime();
                        long earliestNextCall = lastApiCallTime + callIntervalNanos.get();
                        if (earliestNextCall > now) {
                                long waitNanos = earliestNextCall - now;
                                long millis = waitNanos / 1_000_000L;
                                int nanos = (int) (waitNanos % 1_000_000L);
                                try {
                                        Thread.sleep(millis, nanos);
                                } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        throw new RuntimeException("Interrupted while waiting for API rate limit", e);
                                }
                                now = System.nanoTime();
                        }
                        lastApiCallTime = Math.max(now, earliestNextCall);
                }
        }

        /**
         * Liefert das für API-Aufrufe benötigte Token aus den Umgebungsvariablen
         * oder wirft eine Exception, wenn es fehlt.
         */
        private String token() {
                String t = System.getenv("TMDB_API_TOKEN");
                if (t == null || t.isBlank()) {
                        throw new IllegalStateException("TMDB_API_TOKEN not set");
                }
                return t;
        }

        // ============================================================
        // Hauptmethoden (werden vom Resource-Endpoint aufgerufen)
        // ============================================================
        /**
         * Importiert eine durch TMDB-IDs definierte Reihe von Filmen, sammelt
         * dabei Erfolgs- und Fehlerzähler und misst die Laufzeit.
         */
        public ImportStatsDTO importMovieRangeWithStats(int startId, int endId) {
                if (endId < startId) {
                        throw new IllegalArgumentException("Parameter 'endId' must be >= 'startId'");
                }

                refreshApiRateLimit();
                refreshMovieGenres();

                AtomicInteger imported = new AtomicInteger();
                AtomicInteger failed = new AtomicInteger();
                long start = System.currentTimeMillis();

                try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                        List<Future<?>> tasks = IntStream.rangeClosed(startId, endId)
                                        .mapToObj(id -> submitImportTask(id, executor, imported, failed))
                                        .toList();
                        waitForTasks(tasks);
                }

                long duration = System.currentTimeMillis() - start;
                return new ImportStatsDTO(imported.get(), failed.get(), duration);
        }

        /**
         * Durchsucht die TMDB-Discover-API nach Filmen in einem
         * Veröffentlichungsjahresbereich und importiert alle Treffer.
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

                refreshApiRateLimit();
                refreshMovieGenres();

                AtomicInteger imported = new AtomicInteger();
                AtomicInteger failed = new AtomicInteger();
                long start = System.currentTimeMillis();

                try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                        List<Future<?>> tasks = new ArrayList<>();

                        for (int year = startYear; year <= endYear; year++) {
                                int page = 1;
                                int totalPages = 1;
                                boolean warnedAboutPageLimit = false;
                                do {
                                        try {
                                                HttpUrl url = HttpUrl.parse("https://api.themoviedb.org/3/discover/movie")
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

                                                JsonObject response = getJson(url.toString());
                                                if (response == null) {
                                                        break;
                                                }

                                                int reportedTotalPages = response.getInt("total_pages", 1);
                                                if (!warnedAboutPageLimit && reportedTotalPages > 500) {
                                                        warnedAboutPageLimit = true;
                                                        System.out.println("ℹ️  TMDB meldet " + reportedTotalPages
                                                                        + " Seiten für " + year
                                                                        + "; Import wird auf die ersten 500 Seiten begrenzt.");
                                                }

                                                totalPages = Math.min(reportedTotalPages, 500);
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

        /**
         * Startet einen Import-Task für eine TMDB-ID auf einem Virtual Thread und
         * aktualisiert die Erfolgs- bzw. Fehlerzähler.
         */
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

        /**
         * Wartet auf die Fertigstellung aller gestarteten Import-Tasks und leitet
         * Interrupts kontrolliert weiter.
         */
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

        // ============================================================
        // HTTP-Helfer
        // ============================================================
        /**
         * Führt einen GET-Request aus, berücksichtigt die Rate-Limits und gibt den
         * JSON-Body zurück.
         */
        private JsonObject getJson(String url) throws Exception {
                Request req = new Request.Builder()
                                .url(url)
                                .get()
                                .addHeader("accept", "application/json")
                                .addHeader("Authorization", "Bearer " + token())
                                .build();

                long deadline = System.nanoTime() + MAX_RETRY_WAIT.toNanos();
                int attempt = 0;

                while (true) {
                        awaitRateLimit();

                        try (Response resp = http.newCall(req).execute()) {
                                updateRateLimitFromResponse(resp);
                                if (resp.code() == 404)
                                        return null;

                                if (isTransientStatus(resp.code()) && System.nanoTime() < deadline) {
                                        sleepForRetry(attempt++, deadline);
                                        continue;
                                }

                                if (!resp.isSuccessful())
                                        throw new RuntimeException("HTTP " + resp.code() + " for URL " + url);

                                String body = resp.body().string();
                                return Json.createReader(new java.io.StringReader(body)).readObject();
                        } catch (IOException e) {
                                if (System.nanoTime() >= deadline) {
                                        throw new RuntimeException("TMDB request failed after waiting for a response", e);
                                }
                                sleepForRetry(attempt++, deadline);
                        }
                }
        }

        private boolean isTransientStatus(int statusCode) {
                return statusCode == 429 || statusCode == 500 || statusCode == 502 || statusCode == 503 || statusCode == 504;
        }

        private void sleepForRetry(int attempt, long deadlineNanos) {
                long remainingMillis = Math.max(0, TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime()));
                if (remainingMillis == 0)
                        return;

                long delayMillis = Math.min(remainingMillis, 500L + Math.min(attempt, 5) * 200L);
                try {
                        Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while waiting for TMDB response", e);
                }
        }

        /**
         * Ruft die Konfigurations-Route auf, um aktuelle Rate-Limit-Header
         * einzulesen und das lokale Limit anzupassen.
         */
        private void refreshApiRateLimit() {
                HttpUrl url = HttpUrl.parse("https://api.themoviedb.org/3/configuration").newBuilder()
                                .addQueryParameter("language", "en-US")
                                .build();

                Request req = new Request.Builder()
                                .url(url)
                                .get()
                                .addHeader("accept", "application/json")
                                .addHeader("Authorization", "Bearer " + token())
                                .build();

                try (Response resp = http.newCall(req).execute()) {
                        updateRateLimitFromResponse(resp);
                } catch (Exception e) {
                        callIntervalNanos.set(DEFAULT_CALL_INTERVAL_NANOS);
                }
        }

        /**
         * Passt das interne Ratenlimit anhand des API-Response-Headers an.
         */
        private void updateRateLimitFromResponse(Response response) {
                String limitHeader = response.header("X-RateLimit-Limit");
                if (limitHeader == null)
                        return;
                try {
                        long perSecondLimit = Long.parseLong(limitHeader);
                        if (perSecondLimit > 0) {
                                callIntervalNanos.set(1_000_000_000L / perSecondLimit);
                        }
                } catch (NumberFormatException ignored) {
                        callIntervalNanos.set(DEFAULT_CALL_INTERVAL_NANOS);
                }
        }

        /**
         * Synchronisiert die bekannte Genre-Liste mit TMDB und legt fehlende
         * Einträge in der Datenbank an.
         */
        private void refreshMovieGenres() {
                try {
                        HttpUrl url = HttpUrl.parse("https://api.themoviedb.org/3/genre/movie/list").newBuilder()
                                        .addQueryParameter("language", "en-US")
                                        .build();

                        JsonObject response = getJson(url.toString());
                        if (response == null || !response.containsKey("genres"))
                                return;

                        JsonArray genres = response.getJsonArray("genres");
                        if (genres == null || genres.isEmpty())
                                return;

                        try (Connection c = ds.getConnection()) {
                                for (JsonValue value : genres) {
                                        JsonObject genre = value.asJsonObject();
                                        upsertGenre(c, genre.getInt("id"), genre.getString("name", null));
                                }
                        }
                } catch (Exception e) {
                        System.err.println("❌ Failed to refresh genre list: " + e.getMessage());
                }
        }

        /**
         * Lädt Detaildaten einer Person aus TMDB.
         */
        private JsonObject fetchPersonDetails(int tmdbId) throws Exception {
                HttpUrl url = HttpUrl.parse("https://api.themoviedb.org/3/person/" + tmdbId).newBuilder()
                                .addQueryParameter("language", "en-US")
                                .build();
                return getJson(url.toString());
        }

        // ============================================================
        // Einzelimport eines Movies
        // ============================================================
        /**
         * Lädt einen einzelnen Film inklusive Credits, alternativer Titel und
         * Watch-Provider und schreibt alle verknüpften Entitäten in die Datenbank.
         * 
         * @param tmdbId TMDB-ID des Films
         * @return {@code true}, wenn Daten gefunden wurden und gespeichert werden konnten
         */
        private boolean importOne(int tmdbId) throws Exception {
                HttpUrl url = HttpUrl.parse("https://api.themoviedb.org/3/movie/" + tmdbId).newBuilder()
                                .addQueryParameter("language", "en-US")
                                .addQueryParameter("append_to_response", "alternative_titles,credits,watch/providers")
                                .build();

                JsonObject json = getJson(url.toString());
                if (json == null)
                        return false;

                JsonObject alternativeTitles = json.containsKey("alternative_titles")
                                ? json.getJsonObject("alternative_titles")
                                : null;
                JsonObject credits = json.containsKey("credits") ? json.getJsonObject("credits") : null;
                JsonObject watchProviders = json.containsKey("watch/providers")
                                ? json.getJsonObject("watch/providers")
                                : null;

                try (Connection c = ds.getConnection()) {
                        c.setAutoCommit(false);
                        try {
                                if (json.containsKey("original_language")) {
                                        String iso = json.getString("original_language", null);
                                        if (iso != null)
                                                upsertLanguage(c, iso, null, null);
                                }

                                if (json.containsKey("genres")) {
                                        JsonArray arr = json.getJsonArray("genres");
                                        for (JsonValue v : arr) {
                                                JsonObject g = v.asJsonObject();
                                                upsertGenre(c, g.getInt("id"), g.getString("name", null));
                                        }
                                }

                                Long productionTypeId = upsertCountryType(c, "production", "Production country");
                                if (json.containsKey("production_countries")) {
                                        JsonArray arr = json.getJsonArray("production_countries");
                                        for (JsonValue v : arr) {
                                                JsonObject pc = v.asJsonObject();
                                                String iso = normalizeIso2(pc.getString("iso_3166_1", null));
                                                String name = pc.getString("name", null);
                                                if (iso != null)
                                                        upsertCountry(c, iso, (name == null || name.isBlank()) ? iso : name);
                                        }
                                }

                                if (json.containsKey("production_companies")) {
                                        JsonArray arr = json.getJsonArray("production_companies");
                                        for (JsonValue v : arr) {
                                                JsonObject pc = v.asJsonObject();
                                                int pcTmdb = pc.getInt("id");
                                                String n = pc.getString("name", null);
                                                String oc = blankToNull(pc.getString("origin_country", null));
                                                upsertProductionCompany(c, pcTmdb, n, oc);
                                        }
                                }

                                if (json.containsKey("spoken_languages")) {
                                        JsonArray arr = json.getJsonArray("spoken_languages");
                                        for (JsonValue v : arr) {
                                                JsonObject l = v.asJsonObject();
                                                String iso = l.getString("iso_639_1", null);
                                                String en = l.getString("english_name", null);
                                                String name = l.getString("name", null);
                                                if (iso != null)
                                                        upsertLanguage(c, iso, en, name);
                                        }
                                }

                                Long moviePk = upsertMovie(c, json);
                                clearMovieRelations(c, moviePk);

                                linkMovieGenres(c, moviePk, json.getJsonArray("genres"));
                                linkMovieSpokenLanguages(c, moviePk, json.getJsonArray("spoken_languages"));
                                linkMovieCountries(c, moviePk, productionTypeId, json.getJsonArray("production_countries"));
                                linkMovieProductionCompanies(c, moviePk, json.getJsonArray("production_companies"));

                                replaceMovieTitles(c, moviePk, alternativeTitles);
                                replaceMovieWatchProviders(c, moviePk, watchProviders);

                                Map<Integer, Long> personCache = new HashMap<>();
                                if (credits != null) {
                                        JsonArray cast = credits.getJsonArray("cast");
                                        if (cast != null)
                                                replaceMovieCast(c, moviePk, cast, personCache);
                                        JsonArray crew = credits.getJsonArray("crew");
                                        if (crew != null)
                                                replaceMovieCrew(c, moviePk, crew, personCache);
                                }

                                c.commit();
                                return true;
                        } catch (Exception e) {
                                c.rollback();
                                throw e;
                        } finally {
                                c.setAutoCommit(true);
                        }
                }
        }

        // ============================================================
        // UPSERT-Helfer
        // ============================================================
        /**
         * Legt ein Genre an oder aktualisiert dessen Namen anhand der TMDB-ID.
         */
        private void upsertGenre(Connection c, int tmdbId, String name) throws SQLException {
                try (PreparedStatement ps = c.prepareStatement(
                                "INSERT INTO genre (tmdb_id, name) VALUES (?, ?) "
                                                + "ON CONFLICT (tmdb_id) DO UPDATE SET name = EXCLUDED.name")) {
                        ps.setInt(1, tmdbId);
                        ps.setString(2, name);
                        ps.executeUpdate();
                }
        }

        /**
         * Legt eine Sprache an oder aktualisiert vorhandene Felder anhand des ISO-639-1-Codes.
         */
        private void upsertLanguage(Connection c, String iso639_1, String englishName, String name) throws SQLException {
                try (PreparedStatement ps = c.prepareStatement(
                                "INSERT INTO language (iso_639_1, english_name, name) VALUES (?, ?, ?) "
                                                + "ON CONFLICT (iso_639_1) DO UPDATE SET "
                                                + "english_name = COALESCE(EXCLUDED.english_name, language.english_name), "
                                                + "name = COALESCE(EXCLUDED.name, language.name)")) {
                        ps.setString(1, iso639_1);
                        ps.setString(2, englishName);
                        ps.setString(3, name);
                        ps.executeUpdate();
                }
        }

        /**
         * Legt ein Land an oder aktualisiert den Anzeigenamen anhand des ISO-3166-1-Codes.
         */
        private void upsertCountry(Connection c, String iso3166_1, String name) throws SQLException {
                try (PreparedStatement ps = c.prepareStatement(
                                "INSERT INTO country (iso_3166_1, name) VALUES (?, ?) "
                                                + "ON CONFLICT (iso_3166_1) DO UPDATE SET name = EXCLUDED.name")) {
                        ps.setString(1, iso3166_1);
                        ps.setString(2, name);
                        ps.executeUpdate();
                }
        }

        /**
         * Stellt sicher, dass ein Country-Type existiert und liefert seine ID.
         */
        private Long upsertCountryType(Connection c, String code, String desc) throws SQLException {
                try (PreparedStatement ps = c.prepareStatement(
                                "INSERT INTO country_type (code, description) VALUES (?, ?) "
                                                + "ON CONFLICT (code) DO UPDATE SET description = EXCLUDED.description "
                                                + "RETURNING id")) {
                        ps.setString(1, code);
                        ps.setString(2, desc);
                        try (ResultSet rs = ps.executeQuery()) {
                                rs.next();
                                return rs.getLong(1);
                        }
                }
        }

        /**
         * Legt ein Produktionsstudio an oder aktualisiert die bestehenden Daten.
         */
        private void upsertProductionCompany(Connection c, int tmdbId, String name, String originCountry)
                        throws SQLException {
                String oc = normalizeIso2(originCountry);

                if (oc != null) {
                        upsertCountry(c, oc, oc);
                }

                try (PreparedStatement ps = c.prepareStatement(
                                "INSERT INTO production_company (tmdb_id, name, origin_country) VALUES (?, ?, ?) "
                                                + "ON CONFLICT (tmdb_id) DO UPDATE SET name = EXCLUDED.name, origin_country = EXCLUDED.origin_country")) {
                        ps.setInt(1, tmdbId);
                        ps.setString(2, name);
                        if (oc != null)
                                ps.setString(3, oc);
                        else
                                ps.setNull(3, Types.VARCHAR);
                        ps.executeUpdate();
                }
        }

        /**
         * Sorgt dafür, dass eine Department-Zeile existiert und gibt die ID zurück.
         */
        private Long upsertDepartment(Connection c, String name) throws SQLException {
                if (name == null)
                        return null;
                try (PreparedStatement ps = c.prepareStatement(
                                "INSERT INTO department (name) VALUES (?) ON CONFLICT (name) DO NOTHING")) {
                        ps.setString(1, name);
                        ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("SELECT id FROM department WHERE name = ?")) {
                        ps.setString(1, name);
                        try (ResultSet rs = ps.executeQuery()) {
                                if (rs.next())
                                        return rs.getLong(1);
                        }
                }
                return null;
        }

        /**
         * Stellt sicher, dass ein Job innerhalb eines Departments existiert.
         */
        private Long upsertJob(Connection c, Long departmentId, String name) throws SQLException {
                if (departmentId == null || name == null)
                        return null;
                try (PreparedStatement ps = c.prepareStatement(
                                "INSERT INTO job (department_id, name) VALUES (?, ?) ON CONFLICT (department_id, name) DO NOTHING")) {
                        ps.setLong(1, departmentId);
                        ps.setString(2, name);
                        ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                                "SELECT id FROM job WHERE department_id = ? AND name = ?")) {
                        ps.setLong(1, departmentId);
                        ps.setString(2, name);
                        try (ResultSet rs = ps.executeQuery()) {
                                if (rs.next())
                                        return rs.getLong(1);
                        }
                }
                return null;
        }

        /**
         * Legt eine Person an oder aktualisiert sie mit allen verfügbaren Stammdaten.
         */
        private Long upsertPerson(Connection c, int tmdbId, String imdbId, String name, Integer gender,
                        Long knownForDepartmentId, String biography, Date birthday, Date deathday, String placeOfBirth,
                        String homepage, Boolean adult, BigDecimal popularity) throws SQLException {
                try (PreparedStatement ps = c.prepareStatement(
                                "INSERT INTO person (tmdb_id, imdb_id, name, gender, known_for_department, biography, birthday, deathday, place_of_birth, homepage, adult, popularity) "
                                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                                                + "ON CONFLICT (tmdb_id) DO UPDATE SET "
                                                + "imdb_id = EXCLUDED.imdb_id, name = EXCLUDED.name, gender = EXCLUDED.gender, "
                                                + "known_for_department = EXCLUDED.known_for_department, biography = EXCLUDED.biography, "
                                                + "birthday = EXCLUDED.birthday, deathday = EXCLUDED.deathday, place_of_birth = EXCLUDED.place_of_birth, "
                                                + "homepage = EXCLUDED.homepage, adult = EXCLUDED.adult, popularity = EXCLUDED.popularity RETURNING id")) {
                        ps.setInt(1, tmdbId);
                        if (imdbId != null)
                                ps.setString(2, imdbId);
                        else
                                ps.setNull(2, Types.VARCHAR);
                        ps.setString(3, name);
                        if (gender != null)
                                ps.setInt(4, gender);
                        else
                                ps.setNull(4, Types.INTEGER);
                        if (knownForDepartmentId != null)
                                ps.setLong(5, knownForDepartmentId);
                        else
                                ps.setNull(5, Types.INTEGER);
                        if (biography != null)
                                ps.setString(6, biography);
                        else
                                ps.setNull(6, Types.CLOB);
                        if (birthday != null)
                                ps.setDate(7, birthday);
                        else
                                ps.setNull(7, Types.DATE);
                        if (deathday != null)
                                ps.setDate(8, deathday);
                        else
                                ps.setNull(8, Types.DATE);
                        if (placeOfBirth != null)
                                ps.setString(9, placeOfBirth);
                        else
                                ps.setNull(9, Types.VARCHAR);
                        if (homepage != null)
                                ps.setString(10, homepage);
                        else
                                ps.setNull(10, Types.VARCHAR);
                        if (adult != null)
                                ps.setBoolean(11, adult);
                        else
                                ps.setNull(11, Types.BOOLEAN);
                        if (popularity != null)
                                ps.setBigDecimal(12, popularity);
                        else
                                ps.setNull(12, Types.NUMERIC);

                        try (ResultSet rs = ps.executeQuery()) {
                                rs.next();
                                return rs.getLong(1);
                        }
                }
        }

        /**
         * Legt einen Watch-Provider für eine Region an oder aktualisiert ihn.
         */
        private Long upsertWatchProvider(Connection c, JsonObject provider, String region) throws SQLException {
                if (region == null)
                        return null;
                try (PreparedStatement ps = c.prepareStatement(
                                "INSERT INTO watch_provider (tmdb_id, name, logo_path, display_priority, region) VALUES (?, ?, ?, ?, ?) "
                                                + "ON CONFLICT (tmdb_id, region) DO UPDATE SET name = EXCLUDED.name, logo_path = EXCLUDED.logo_path, display_priority = EXCLUDED.display_priority RETURNING id")) {
                        ps.setInt(1, provider.getInt("provider_id"));
                        ps.setString(2, provider.getString("provider_name", null));
                        String logo = blankToNull(provider.getString("logo_path", null));
                        if (logo != null)
                                ps.setString(3, logo);
                        else
                                ps.setNull(3, Types.VARCHAR);
                        if (provider.containsKey("display_priority") && !provider.isNull("display_priority"))
                                ps.setInt(4, provider.getInt("display_priority"));
                        else
                                ps.setNull(4, Types.INTEGER);
                        ps.setString(5, region);

                        try (ResultSet rs = ps.executeQuery()) {
                                rs.next();
                                return rs.getLong(1);
                        }
                }
        }

        /**
         * Ersetzt die Alias-Liste einer Person vollständig durch die Angaben aus dem Detail-Response.
         */
        private void replacePersonAliases(Connection c, Long personId, JsonObject detail) throws SQLException {
                try (PreparedStatement delete = c.prepareStatement("DELETE FROM person_alias WHERE person_id = ?")) {
                        delete.setLong(1, personId);
                        delete.executeUpdate();
                }

                if (detail == null || !detail.containsKey("also_known_as"))
                        return;

                JsonArray aliases = detail.getJsonArray("also_known_as");
                if (aliases == null || aliases.isEmpty())
                        return;

                try (PreparedStatement insert = c.prepareStatement(
                                "INSERT INTO person_alias (person_id, alias) VALUES (?, ?)")) {
                        for (JsonValue value : aliases) {
                                if (value.getValueType() != JsonValue.ValueType.STRING)
                                        continue;
                                String alias = blankToNull(((JsonString) value).getString());
                                if (alias == null)
                                        continue;
                                insert.setLong(1, personId);
                                insert.setString(2, alias);
                                insert.addBatch();
                        }
                        insert.executeBatch();
                }
        }

        // ============================================================
        // Verknüpfungen (Relations)
        // ============================================================
        /**
         * Verknüpft einen Film mit allen Genres aus dem JSON-Response.
         */
        private void linkMovieGenres(Connection c, Long movieId, JsonArray genres) throws SQLException {
                if (genres == null || genres.isEmpty())
                        return;
                try (PreparedStatement ps = c.prepareStatement(
                                "INSERT INTO movie_genre (movie_id, genre_id) VALUES (?, ?) "
                                                + "ON CONFLICT (movie_id, genre_id) DO NOTHING")) {
                        for (JsonValue v : genres) {
                                JsonObject g = v.asJsonObject();
                                Long genreId = findIdByTmdb(c, "genre", g.getInt("id"));
                                if (genreId == null)
                                        continue;
                                ps.setLong(1, movieId);
                                ps.setLong(2, genreId);
                                ps.addBatch();
                        }
                        ps.executeBatch();
                }
        }

        /**
         * Verknüpft Sprachen mit einem Film, wobei doppelte ISO-Codes vermieden werden.
         */
        private void linkMovieSpokenLanguages(Connection c, Long movieId, JsonArray languages) throws SQLException {
                if (languages == null || languages.isEmpty())
                        return;
                Set<String> inserted = new HashSet<>();
                try (PreparedStatement ps = c.prepareStatement(
                                "INSERT INTO movie_spoken_language (movie_id, iso_639_1) VALUES (?, ?) "
                                                + "ON CONFLICT (movie_id, iso_639_1) DO NOTHING")) {
                        for (JsonValue v : languages) {
                                JsonObject l = v.asJsonObject();
                                String iso = l.getString("iso_639_1", null);
                                if (iso == null || !inserted.add(iso))
                                        continue;
                                ps.setLong(1, movieId);
                                ps.setString(2, iso);
                                ps.addBatch();
                        }
                        ps.executeBatch();
                }
        }

        /**
         * Speichert Produktionsländer für einen Film.
         */
        private void linkMovieCountries(Connection c, Long movieId, Long countryTypeId, JsonArray countries)
                        throws SQLException {
                if (countryTypeId == null || countries == null || countries.isEmpty())
                        return;
                Set<String> inserted = new HashSet<>();
                try (PreparedStatement ps = c.prepareStatement(
                                "INSERT INTO movie_country (movie_id, iso_3166_1, country_type_id) VALUES (?, ?, ?) "
                                                + "ON CONFLICT (movie_id, iso_3166_1, country_type_id) DO NOTHING")) {
                        for (JsonValue v : countries) {
                                JsonObject pc = v.asJsonObject();
                                String iso = normalizeIso2(pc.getString("iso_3166_1", null));
                                if (iso == null || !inserted.add(iso))
                                        continue;
                                ps.setLong(1, movieId);
                                ps.setString(2, iso);
                                ps.setLong(3, countryTypeId);
                                ps.addBatch();
                        }
                        ps.executeBatch();
                }
        }

        /**
         * Verknüpft Produktionsfirmen mit einem Film.
         */
        private void linkMovieProductionCompanies(Connection c, Long movieId, JsonArray companies) throws SQLException {
                if (companies == null || companies.isEmpty())
                        return;
                Set<Long> inserted = new HashSet<>();
                try (PreparedStatement ps = c.prepareStatement(
                                "INSERT INTO movie_production_company (movie_id, production_company_id) VALUES (?, ?) "
                                                + "ON CONFLICT (movie_id, production_company_id) DO NOTHING")) {
                        for (JsonValue v : companies) {
                                JsonObject pc = v.asJsonObject();
                                Long pcId = findIdByTmdb(c, "production_company", pc.getInt("id"));
                                if (pcId == null || !inserted.add(pcId))
                                        continue;
                                ps.setLong(1, movieId);
                                ps.setLong(2, pcId);
                                ps.addBatch();
                        }
                        ps.executeBatch();
                }
        }

        /**
         * Fügt eine konkrete Anbieter-Zeile in der Join-Tabelle hinzu.
         */
        private void linkMovieWatchProvider(Connection c, PreparedStatement ps, Long movieId, Long providerId,
                        String type, String link) throws SQLException {
                ps.setLong(1, movieId);
                ps.setLong(2, providerId);
                ps.setString(3, type);
                if (link != null)
                        ps.setString(4, link);
                else
                        ps.setNull(4, Types.VARCHAR);
                ps.addBatch();
        }

        /**
         * Ersetzt alle alternativen Titel eines Films durch die in TMDB gelieferten.
         */
        private void replaceMovieTitles(Connection c, Long movieId, JsonObject alternativeTitles) throws SQLException {
                clearMovieRelation(c, "movie_title", movieId);
                if (alternativeTitles == null || !alternativeTitles.containsKey("titles"))
                        return;
                JsonArray titles = alternativeTitles.getJsonArray("titles");
                if (titles == null || titles.isEmpty())
                        return;
                try (PreparedStatement ps = c.prepareStatement(
                                "INSERT INTO movie_title (movie_id, iso_3166_1, title, type) VALUES (?, ?, ?, ?)")) {
                        for (JsonValue value : titles) {
                                JsonObject t = value.asJsonObject();
                                String iso = normalizeIso2(t.getString("iso_3166_1", null));
                                if (iso == null)
                                        continue;
                                upsertCountry(c, iso, iso);
                                String title = blankToNull(t.getString("title", null));
                                String type = blankToNull(t.getString("type", null));
                                ps.setLong(1, movieId);
                                ps.setString(2, iso);
                                if (title != null)
                                        ps.setString(3, title);
                                else
                                        ps.setNull(3, Types.VARCHAR);
                                if (type != null)
                                        ps.setString(4, type);
                                else
                                        ps.setNull(4, Types.VARCHAR);
                                ps.addBatch();
                        }
                        ps.executeBatch();
                }
        }

        /**
         * Ersetzt alle Watch-Provider-Einträge eines Films in allen Regionen.
         */
        private void replaceMovieWatchProviders(Connection c, Long movieId, JsonObject watchProviders) throws Exception {
                clearMovieRelation(c, "movie_watch_provider", movieId);
                if (watchProviders == null || !watchProviders.containsKey("results"))
                        return;
                JsonObject results = watchProviders.getJsonObject("results");
                if (results == null)
                        return;
                try (PreparedStatement ps = c.prepareStatement(
                                "INSERT INTO movie_watch_provider (movie_id, provider_id, type, link) VALUES (?, ?, ?, ?) "
                                                + "ON CONFLICT (movie_id, provider_id, type) DO UPDATE SET link = EXCLUDED.link")) {
                        for (String regionCode : results.keySet()) {
                                String iso = normalizeIso2(regionCode);
                                if (iso == null)
                                        continue;
                                upsertCountry(c, iso, iso);
                                JsonObject region = results.getJsonObject(regionCode);
                                if (region == null)
                                        continue;
                                String link = blankToNull(region.getString("link", null));
                                processWatchProviderType(c, ps, movieId, iso, link, region, "flatrate");
                                processWatchProviderType(c, ps, movieId, iso, link, region, "buy");
                                processWatchProviderType(c, ps, movieId, iso, link, region, "rent");
                                processWatchProviderType(c, ps, movieId, iso, link, region, "ads");
                                processWatchProviderType(c, ps, movieId, iso, link, region, "free");
                        }
                        ps.executeBatch();
                }
        }

        /**
         * Verarbeitet eine einzelne Anbieter-Kategorie (z. B. Kauf oder Flatrate).
         */
        private void processWatchProviderType(Connection c, PreparedStatement ps, Long movieId, String region, String link,
                        JsonObject regionData, String type) throws Exception {
                if (!regionData.containsKey(type))
                        return;
                JsonArray providers = regionData.getJsonArray(type);
                if (providers == null)
                        return;
                for (JsonValue value : providers) {
                        JsonObject provider = value.asJsonObject();
                        Long providerId = upsertWatchProvider(c, provider, region);
                        if (providerId != null)
                                linkMovieWatchProvider(c, ps, movieId, providerId, type, link);
                }
        }

        /**
         * Ersetzt die gesamte Besetzung eines Films anhand des Credits-Arrays.
         */
        private void replaceMovieCast(Connection c, Long movieId, JsonArray cast, Map<Integer, Long> personCache)
                        throws Exception {
                clearMovieRelation(c, "movie_cast", movieId);
                if (cast == null)
                        return;
                try (PreparedStatement ps = c.prepareStatement(
                                "INSERT INTO movie_cast (movie_id, person_id, character_name, cast_order) VALUES (?, ?, ?, ?)")) {
                        for (JsonValue value : cast) {
                                JsonObject member = value.asJsonObject();
                                Long personId = ensurePerson(c, member, personCache);
                                if (personId == null)
                                        continue;
                                String character = blankToNull(member.getString("character", null));
                                Integer order = member.containsKey("order") && !member.isNull("order")
                                                ? member.getInt("order")
                                                : null;
                                ps.setLong(1, movieId);
                                ps.setLong(2, personId);
                                if (character != null)
                                        ps.setString(3, character);
                                else
                                        ps.setNull(3, Types.VARCHAR);
                                if (order != null)
                                        ps.setInt(4, order);
                                else
                                        ps.setNull(4, Types.INTEGER);
                                ps.addBatch();
                        }
                        ps.executeBatch();
                }
        }

        /**
         * Ersetzt die gesamte Crew eines Films inklusive Job/Department-Verknüpfung.
         */
        private void replaceMovieCrew(Connection c, Long movieId, JsonArray crew, Map<Integer, Long> personCache)
                        throws Exception {
                clearMovieRelation(c, "movie_crew", movieId);
                if (crew == null)
                        return;
                Set<String> inserted = new HashSet<>();
                try (PreparedStatement ps = c.prepareStatement(
                                "INSERT INTO movie_crew (movie_id, person_id, job_id) VALUES (?, ?, ?)")) {
                        for (JsonValue value : crew) {
                                JsonObject member = value.asJsonObject();
                                String departmentName = blankToNull(member.getString("department", null));
                                String jobName = blankToNull(member.getString("job", null));
                                if (departmentName == null || jobName == null)
                                        continue;
                                Long departmentId = upsertDepartment(c, departmentName);
                                Long jobId = upsertJob(c, departmentId, jobName);
                                if (jobId == null)
                                        continue;
                                Long personId = ensurePerson(c, member, personCache);
                                if (personId == null)
                                        continue;
                                String key = movieId + ":" + personId + ":" + jobId;
                                if (!inserted.add(key))
                                        continue;
                                ps.setLong(1, movieId);
                                ps.setLong(2, personId);
                                ps.setLong(3, jobId);
                                ps.addBatch();
                        }
                        ps.executeBatch();
                }
        }

        /**
         * Sorgt dafür, dass eine Person aus den Credit-Daten in der Datenbank existiert
         * und liefert ihre ID.
         */
        private Long ensurePerson(Connection c, JsonObject creditData, Map<Integer, Long> personCache) throws Exception {
                int tmdbId = creditData.getInt("id");
                if (personCache.containsKey(tmdbId))
                        return personCache.get(tmdbId);

                JsonObject detail = fetchPersonDetails(tmdbId);

                String knownFor = detail != null ? blankToNull(detail.getString("known_for_department", null)) : null;
                if (knownFor == null)
                        knownFor = blankToNull(creditData.getString("known_for_department", null));
                Long knownForDeptId = upsertDepartment(c, knownFor);

                String imdbId = detail != null ? blankToNull(detail.getString("imdb_id", null)) : null;
                String name = detail != null ? blankToNull(detail.getString("name", null)) : null;
                if (name == null)
                        name = blankToNull(creditData.getString("name", null));
                if (name == null)
                        name = "Unknown";

                Integer gender = null;
                if (detail != null && detail.containsKey("gender") && !detail.isNull("gender"))
                        gender = detail.getInt("gender");
                else if (creditData.containsKey("gender") && !creditData.isNull("gender"))
                        gender = creditData.getInt("gender");

                String biography = detail != null ? blankToNull(detail.getString("biography", null)) : null;
                Date birthday = detail != null ? toSqlDate(detail.getString("birthday", null)) : null;
                Date deathday = detail != null ? toSqlDate(detail.getString("deathday", null)) : null;
                String placeOfBirth = detail != null ? blankToNull(detail.getString("place_of_birth", null)) : null;
                String homepage = detail != null ? blankToNull(detail.getString("homepage", null)) : null;
                Boolean adult = null;
                if (detail != null && detail.containsKey("adult") && !detail.isNull("adult"))
                        adult = detail.getBoolean("adult");
                else if (creditData.containsKey("adult") && !creditData.isNull("adult"))
                        adult = creditData.getBoolean("adult");

                BigDecimal popularity = detail != null ? toBigDecimal(detail, "popularity") : toBigDecimal(creditData,
                                "popularity");

                Long personId = upsertPerson(c, tmdbId, imdbId, name, gender, knownForDeptId, biography, birthday,
                                deathday, placeOfBirth, homepage, adult, popularity);
                if (detail != null)
                        replacePersonAliases(c, personId, detail);
                personCache.put(tmdbId, personId);
                return personId;
        }

        /**
         * Löscht alle Zeilen einer movie-bezogenen Tabelle für eine konkrete Film-ID.
         */
        private void clearMovieRelation(Connection c, String table, Long movieId) throws SQLException {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + table + " WHERE movie_id = ?")) {
                        ps.setLong(1, movieId);
                        ps.executeUpdate();
                }
        }

        /**
         * Löscht alle abhängigen Relationen eines Films, bevor neue Daten geschrieben werden.
         */
        private void clearMovieRelations(Connection c, Long movieId) throws SQLException {
                clearMovieRelation(c, "movie_genre", movieId);
                clearMovieRelation(c, "movie_spoken_language", movieId);
                clearMovieRelation(c, "movie_country", movieId);
                clearMovieRelation(c, "movie_production_company", movieId);
                clearMovieRelation(c, "movie_title", movieId);
                clearMovieRelation(c, "movie_cast", movieId);
                clearMovieRelation(c, "movie_crew", movieId);
                clearMovieRelation(c, "movie_watch_provider", movieId);
        }

        /**
         * Konvertiert ein Datums-String in ein SQL-Datum, falls möglich.
         */
        private static Date toSqlDate(String s) {
                if (s == null || s.isBlank())
                        return null;
                try {
                        return Date.valueOf(java.time.LocalDate.parse(s));
                } catch (Exception e) {
                        return null;
                }
        }

        /**
         * Liest eine Dezimalzahl aus einem JSON-Objekt aus.
         */
        private BigDecimal toBigDecimal(JsonObject json, String key) {
                if (json == null || !json.containsKey(key) || json.isNull(key))
                        return null;
                return new BigDecimal(json.getJsonNumber(key).toString());
        }

        /**
         * Legt den Movie-Datensatz an oder aktualisiert ihn mit allen Stammdaten.
         */
        private Long upsertMovie(Connection c, jakarta.json.JsonObject j) throws SQLException {
                try (PreparedStatement ps = c.prepareStatement(
                                "INSERT INTO movie (tmdb_id, imdb_id, title, original_title, original_language, adult, video, status, "
                                                + "release_date, budget, revenue, runtime, homepage, overview, popularity, vote_average, vote_count, tagline) "
                                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                                                + "ON CONFLICT (tmdb_id) DO UPDATE SET "
                                                + "imdb_id=EXCLUDED.imdb_id, title=EXCLUDED.title, original_title=EXCLUDED.original_title, "
                                                + "original_language=EXCLUDED.original_language, adult=EXCLUDED.adult, video=EXCLUDED.video, "
                                                + "status=EXCLUDED.status, release_date=EXCLUDED.release_date, budget=EXCLUDED.budget, "
                                                + "revenue=EXCLUDED.revenue, runtime=EXCLUDED.runtime, homepage=EXCLUDED.homepage, "
                                                + "overview=EXCLUDED.overview, popularity=EXCLUDED.popularity, vote_average=EXCLUDED.vote_average, "
                                                + "vote_count=EXCLUDED.vote_count, tagline=EXCLUDED.tagline RETURNING id")) {
                        ps.setInt(1, j.getInt("id"));
                        ps.setString(2, j.getString("imdb_id", null));
                        ps.setString(3, j.getString("title", null));
                        ps.setString(4, j.getString("original_title", null));
                        ps.setString(5, j.getString("original_language", null));
                        ps.setObject(6, j.getBoolean("adult", false));
                        ps.setObject(7, j.getBoolean("video", false));
                        ps.setString(8, j.getString("status", null));

                        Date rd = toSqlDate(j.getString("release_date", null));
                        if (rd == null)
                                ps.setNull(9, Types.DATE);
                        else
                                ps.setDate(9, rd);

                        ps.setObject(10, j.isNull("budget") ? null : j.getInt("budget"));
                        ps.setObject(11, j.isNull("revenue") ? null : j.getJsonNumber("revenue").longValue());
                        ps.setObject(12, j.isNull("runtime") ? null : j.getInt("runtime"));
                        ps.setString(13, j.getString("homepage", null));
                        ps.setString(14, j.getString("overview", null));
                        ps.setObject(15,
                                        j.isNull("popularity") ? null : new java.math.BigDecimal(j.getJsonNumber("popularity").toString()));
                        ps.setObject(16, j.isNull("vote_average") ? null
                                        : new java.math.BigDecimal(j.getJsonNumber("vote_average").toString()));
                        ps.setObject(17, j.isNull("vote_count") ? null : j.getInt("vote_count"));
                        ps.setString(18, j.getString("tagline", null));

                        try (ResultSet rs = ps.executeQuery()) {
                                rs.next();
                                return rs.getLong(1);
                        }
                }
        }

        /**
         * Normalisiert ISO-Codes, indem Leerzeichen entfernt und leere Strings unterdrückt werden.
         */
        private static String normalizeIso2(String s) {
                if (s == null)
                        return null;
                String t = s.trim();
                return t.isEmpty() ? null : t;
        }

        /**
         * Wandelt leere Strings in {@code null} um.
         */
        private static String blankToNull(String s) {
                if (s == null)
                        return null;
                return s.isBlank() ? null : s;
        }

        /**
         * Liefert die Primärschlüssel-ID zu einer TMDB-ID aus einer beliebigen Tabelle.
         */
        private Long findIdByTmdb(Connection c, String table, int tmdbId) throws SQLException {
                try (PreparedStatement ps = c.prepareStatement("SELECT id FROM " + table + " WHERE tmdb_id = ?")) {
                        ps.setInt(1, tmdbId);
                        try (ResultSet rs = ps.executeQuery()) {
                                if (rs.next())
                                        return rs.getLong(1);
                                else
                                        return null;
                        }
                }
        }
}
