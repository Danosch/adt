package com.adt.resource;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import com.adt.entity.dto.QueryPerformanceDTO;

/**
 * REST-Resource für die Ausführung von Messabfragen, die in {@link DatabaseMetricsResourceImpl} implementiert sind.
 */
@Path("/db/metrics")
@Produces(MediaType.APPLICATION_JSON)
public interface DatabaseMetricsResource {

        /**
         * Führt eine indexbasierte Abfrage aus und gibt die Laufzeit zurück.
         */
        @GET
        @Path("/indexed")
        QueryPerformanceDTO runIndexedQuery(@QueryParam("id") @DefaultValue("1") int movieId);

        /**
         * Führt eine Suche ohne passenden Index aus und misst die Dauer.
         */
        @GET
        @Path("/full-scan")
        QueryPerformanceDTO runUnindexedQuery(@QueryParam("term") @DefaultValue("the") String term);
}
