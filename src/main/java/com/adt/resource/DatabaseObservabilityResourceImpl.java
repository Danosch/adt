package com.adt.resource;

import jakarta.inject.Inject;

import com.adt.entity.dto.DatabaseObservabilitySnapshotDTO;
import com.adt.service.DatabaseObservabilityService;

import io.micrometer.core.annotation.Timed;

/**
 * Implementierung der Observability-Endpoints für DB- und Pool-Metriken.
 */
public class DatabaseObservabilityResourceImpl implements DatabaseObservabilityResource {

        @Inject
        DatabaseObservabilityService observabilityService;

        @Override
        @Timed(value = "adt.http.db.observability", description = "Snapshot für Pool- und Postgres-Metriken")
        public DatabaseObservabilitySnapshotDTO snapshot() {
                return observabilityService.snapshot();
        }
}
