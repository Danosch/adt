package com.adt.service;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonObject;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Kapselt alle HTTP-Aufrufe zur TMDB-API inklusive Ratenlimit-Handling.
 */
@ApplicationScoped
public class TmdbClient {

        private static final long DEFAULT_CALL_INTERVAL_NANOS = 1_000_000_000L / 50; // 50 calls per second
        private static final Duration MAX_RETRY_WAIT = Duration.ofSeconds(10);

        private final OkHttpClient http = new OkHttpClient.Builder()
                        .callTimeout(MAX_RETRY_WAIT)
                        .build();

        private final AtomicLong callIntervalNanos = new AtomicLong(DEFAULT_CALL_INTERVAL_NANOS);
        private final Object rateLimitLock = new Object();
        private long lastApiCallTime = 0L;

        /**
         * Führt einen GET-Request aus, berücksichtigt die Rate-Limits und gibt den JSON-Body zurück.
         */
        public JsonObject getJson(String url) throws Exception {
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

                                assert resp.body() != null;
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

        /**
         * Ruft die Konfigurations-Route auf, um aktuelle Rate-Limit-Header einzulesen und das lokale Limit anzupassen.
         */
        public void refreshApiRateLimit() {
                HttpUrl url = Objects.requireNonNull(HttpUrl.parse("https://api.themoviedb.org/3/configuration")).newBuilder()
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
         * Lädt Detaildaten einer Person aus TMDB.
         */
        public JsonObject fetchPersonDetails(int tmdbId) throws Exception {
                HttpUrl url = Objects.requireNonNull(HttpUrl.parse("https://api.themoviedb.org/3/person/" + tmdbId))
                                .newBuilder()
                                .addQueryParameter("language", "en-US")
                                .build();
                return getJson(url.toString());
        }

        /**
         * Lädt Detaildaten eines Films inklusive aller benötigten Erweiterungen.
         */
        public JsonObject fetchMovieDetails(int tmdbId) throws Exception {
                HttpUrl url = Objects.requireNonNull(HttpUrl.parse("https://api.themoviedb.org/3/movie/" + tmdbId))
                                .newBuilder()
                                .addQueryParameter("language", "en-US")
                                .addQueryParameter("append_to_response", "alternative_titles,credits,watch/providers")
                                .build();
                return getJson(url.toString());
        }

        private String token() {
                String t = System.getenv("TMDB_API_TOKEN");
                if (t == null || t.isBlank()) {
                        throw new IllegalStateException("TMDB_API_TOKEN not set");
                }
                return t;
        }

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
}
