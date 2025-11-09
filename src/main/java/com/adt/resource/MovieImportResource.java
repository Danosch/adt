package com.adt.resource;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import com.adt.entity.dto.ImportResultDTO;
import com.adt.entity.dto.ImportYearResultDTO;

@Path("/import")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface MovieImportResource {

    @POST
    @Path("/movies")
    ImportResultDTO importMovies(@QueryParam("start") int startId, @QueryParam("end") int endId);

    @POST
    @Path("/movies/years")
    ImportYearResultDTO importMoviesFromYears(
                    @QueryParam("startYear") int startYear,
                    @QueryParam("endYear") int endYear);

}
