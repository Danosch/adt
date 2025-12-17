package com.adt.resource;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import com.adt.entity.dto.ConcurrentLoadResultDTO;
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

	/**
	 * Nutzt eine Jahr-Extraktion, die typischerweise einen Index auf release_date umgeht.
	 */
	@GET
	@Path("/year-extraction")
	QueryPerformanceDTO runYearExtractionQuery(@QueryParam("year") @DefaultValue("2010") int year);

	/**
	 * Führt eine range-basierte Abfrage über release_date aus, die von einem Index profitiert.
	 */
	@GET
	@Path("/release-range")
	QueryPerformanceDTO runReleaseRangeQuery(
			@QueryParam("startYear") @DefaultValue("2000") int startYear,
			@QueryParam("endYear") @DefaultValue("2020") int endYear,
			@QueryParam("limit") @DefaultValue("200") int limit);

	/**
	 * Misst eine unperformante Textsuche über Film-Overviews.
	 */
	@GET
	@Path("/overview-scan")
	QueryPerformanceDTO runOverviewScanQuery(@QueryParam("term") @DefaultValue("love") String term);

	/**
	 * Liefert schnell die bestbewerteten Filme über einen kombinierten Index.
	 */
	@GET
	@Path("/top-rated")
	QueryPerformanceDTO runTopRatedQuery(
			@QueryParam("minVotes") @DefaultValue("500") int minVotes,
			@QueryParam("limit") @DefaultValue("50") int limit);

	/**
	 * Führt eine Zufallssortierung aus, die einen vollständigen Table-Scan erzwingt.
	 */
	@GET
	@Path("/random-sort")
	QueryPerformanceDTO runRandomSortQuery(@QueryParam("limit") @DefaultValue("150") int limit);

	/**
	 * Führt eine führende Wildcard-Suche auf dem Originaltitel aus (nicht indexfreundlich).
	 */
	@GET
	@Path("/wildcard-original-title")
	QueryPerformanceDTO runWildcardOriginalTitleQuery(
			@QueryParam("term") @DefaultValue("man") String term,
			@QueryParam("limit") @DefaultValue("120") int limit);

	/**
	 * Misst eine indexgestützte Filterung nach Originalsprache.
	 */
	@GET
	@Path("/vlanguage-filter")
	QueryPerformanceDTO runLanguageFilterQuery(
			@QueryParam("language") @DefaultValue("en") String language,
			@QueryParam("limit") @DefaultValue("200") int limit);

	/**
	 * Misst eine performante Kombination aus Veröffentlichungsjahr und Popularität.
	 */
	@GET
	@Path("/recent-popular")
	QueryPerformanceDTO runRecentPopularQuery(
			@QueryParam("startYear") @DefaultValue("2015") int startYear,
			@QueryParam("limit") @DefaultValue("100") int limit);

	/**
	 * Führt mehrere Abfragen sequenziell aus, um die Datenbank spürbar zu belasten.
	 */
        @GET
        @Path("/load-test")
        QueryPerformanceDTO runLoadTest(
                        @QueryParam("iterations") @DefaultValue("5") int iterations,
                        @QueryParam("limit") @DefaultValue("150") int limit);

        /**
         * Simuliert viele gleichzeitige Zugriffe auf die Datenbank, um den Connection Pool und Locks zu belasten.
         */
        @GET
        @Path("/concurrent-load")
        ConcurrentLoadResultDTO runConcurrentLoad(
                        @QueryParam("virtualUsers") @DefaultValue("1000") int virtualUsers,
                        @QueryParam("limit") @DefaultValue("25") int limitPerUser);
}
