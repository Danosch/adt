package com.adt.resource;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import com.adt.entity.dto.ImportResultDTO;
import com.adt.entity.dto.ImportYearResultDTO;

/**
 * REST-Resource für das manuelle Anstoßen von Film-Importen aus der TMDB-API.
 * Die Endpunkte werden von {@link MovieImportResourceImpl} implementiert.
 */
@Path("/import")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface MovieImportResource {

    /**
     * Startet einen Import auf Basis eines TMDB-ID-Bereichs.
     *
     * @param startId Erste TMDB-ID (inklusive), die abgerufen werden soll
     * @param endId   Letzte TMDB-ID (inklusive), die abgerufen werden soll
     * @return Statistik des Imports samt Erfolgs- und Fehlerzähler
     */
    @POST
    @Path("/movies")
    ImportResultDTO importMovies(@QueryParam("start") int startId, @QueryParam("end") int endId);

    /**
     * Startet einen Import für alle Filme innerhalb eines Veröffentlichungsjahres-Bereichs.
     *
     * @param startYear Untere Schranke des Jahrgangs (inklusive)
     * @param endYear   Obere Schranke des Jahrgangs (inklusive)
     * @return Statistik des Imports samt Erfolgs- und Fehlerzähler
     */
    @POST
    @Path("/movies/years")
    ImportYearResultDTO importMoviesFromYears(
                    @QueryParam("startYear") int startYear,
                    @QueryParam("endYear") int endYear);
}
