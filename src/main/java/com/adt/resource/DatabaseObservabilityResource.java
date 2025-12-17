package com.adt.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import com.adt.entity.dto.DatabaseObservabilitySnapshotDTO;

/**
 * Liefert Live-Metriken für Pool- und Runtime-Statistiken.
 */
@Path("/db/observability")
@Produces(MediaType.APPLICATION_JSON)
public interface DatabaseObservabilityResource {

        /**
         * Fasst Connection-Pool-Auslastung und Postgres-Runtime-Statistiken zusammen.
         */
        @GET
        DatabaseObservabilitySnapshotDTO snapshot();
}
