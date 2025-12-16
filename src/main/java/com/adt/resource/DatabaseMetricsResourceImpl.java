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
}
