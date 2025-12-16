package com.adt.resource;

import jakarta.inject.Inject;

import com.adt.entity.dto.QueryPerformanceDTO;
import com.adt.service.DatabaseMetricsService;

import io.micrometer.core.annotation.Timed;

/**
 * Implementierung der REST-Endpunkte zur Ausführung der Datenbankmessungen.
 */
public class DatabaseMetricsResourceImpl implements DatabaseMetricsResource {

        @Inject
        DatabaseMetricsService metricsService;

        /**
         * Führt eine indexbasierte Abfrage aus und gibt die Laufzeit zurück.
         */
        @Override
        @Timed(value = "adt.http.db.indexed", description = "Laufzeit des Endpunkts für indexierte Abfragen")
        public QueryPerformanceDTO runIndexedQuery(int movieId) {
                return metricsService.measureIndexedLookup(movieId);
        }

        /**
         * Führt eine Suche ohne passenden Index aus und misst die Dauer.
         */
        @Override
        @Timed(value = "adt.http.db.full-scan", description = "Laufzeit des Endpunkts für Abfragen ohne Index")
        public QueryPerformanceDTO runUnindexedQuery(String term) {
                return metricsService.measureUnindexedSearch(term);
        }

        @Override
        @Timed(value = "adt.http.db.year-extraction", description = "Laufzeit einer Abfrage mit Funktionsaufruf auf release_date")
        public QueryPerformanceDTO runYearExtractionQuery(int year) {
                return metricsService.measureYearExtractionSearch(year);
        }

        @Override
        @Timed(value = "adt.http.db.release-range", description = "Laufzeit der indexfreundlichen Jahresbereichs-Abfrage")
        public QueryPerformanceDTO runReleaseRangeQuery(int startYear, int endYear, int limit) {
                return metricsService.measureReleaseRange(startYear, endYear, limit);
        }

        @Override
        @Timed(value = "adt.http.db.overview-scan", description = "Laufzeit der Volltextsuche über Overview")
        public QueryPerformanceDTO runOverviewScanQuery(String term) {
                return metricsService.measureOverviewScan(term);
        }

        @Override
        @Timed(value = "adt.http.db.top-rated", description = "Laufzeit der bestbewerteten Filme über kombinierten Index")
        public QueryPerformanceDTO runTopRatedQuery(int minVotes, int limit) {
                return metricsService.measureTopRated(minVotes, limit);
        }

        @Override
        @Timed(value = "adt.http.db.random-sort", description = "Laufzeit einer Zufallssortierung ohne Index")
        public QueryPerformanceDTO runRandomSortQuery(int limit) {
                return metricsService.measureRandomSort(limit);
        }

        @Override
        @Timed(value = "adt.http.db.wildcard-original-title", description = "Laufzeit einer führenden Wildcard auf Originaltitel")
        public QueryPerformanceDTO runWildcardOriginalTitleQuery(String term, int limit) {
                return metricsService.measureWildcardOriginalTitle(term, limit);
        }

        @Override
        @Timed(value = "adt.http.db.language-filter", description = "Laufzeit einer indexgestützten Filterung nach Sprache")
        public QueryPerformanceDTO runLanguageFilterQuery(String language, int limit) {
                return metricsService.measureLanguageFilter(language, limit);
        }

        @Override
        @Timed(value = "adt.http.db.recent-popular", description = "Laufzeit einer kombinierten Popularitäts-/Release-Abfrage")
        public QueryPerformanceDTO runRecentPopularQuery(int startYear, int limit) {
                return metricsService.measureRecentPopular(startYear, limit);
        }

        @Override
        @Timed(value = "adt.http.db.load-test", description = "Mehrere Queries zum gezielten Belastungstest")
        public QueryPerformanceDTO runLoadTest(int iterations, int limit) {
                return metricsService.runLoadScenario(iterations, limit);
        }
}
