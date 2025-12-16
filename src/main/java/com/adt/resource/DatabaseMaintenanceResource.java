package com.adt.resource;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import com.adt.entity.dto.ExplainPlanDTO;
import com.adt.entity.dto.MaintenanceActionDTO;

/**
 * REST-Resource für Wartungs- und Diagnoseaktionen (ANALYZE, EXPLAIN).
 */
@Path("/db/maintenance")
@Produces(MediaType.APPLICATION_JSON)
public interface DatabaseMaintenanceResource {

        /**
         * Aktualisiert die Planner-Statistiken via ANALYZE.
         */
        @GET
        @Path("/analyze")
        MaintenanceActionDTO analyze();

        /**
         * Liefert einen Explain-Plan für die indexfreundliche Range-Abfrage.
         */
        @GET
        @Path("/explain/release-range")
        ExplainPlanDTO explainReleaseRange(
                        @QueryParam("startYear") @DefaultValue("2000") int startYear,
                        @QueryParam("endYear") @DefaultValue("2020") int endYear,
                        @QueryParam("limit") @DefaultValue("100") int limit);

        /**
         * Liefert einen Explain-Plan für die unperformante Jahres-Extraktion.
         */
        @GET
        @Path("/explain/year-extraction")
        ExplainPlanDTO explainYearExtraction(
                        @QueryParam("year") @DefaultValue("2010") int year,
                        @QueryParam("limit") @DefaultValue("100") int limit);
}
