package com.adt.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

import com.adt.entity.dto.ImportResultDTO;
import com.adt.service.MovieImportService;

public class MovieImportResourceImpl implements MovieImportResource {

	@Inject
	MovieImportService importService;

	@Override
	public ImportResultDTO importMovies(int startId, int endId) {
		if (endId < startId) {
			throw new BadRequestException("Parameter 'end' must be >= 'start'");
		}

		var result = importService.importMovieRangeWithStats(startId, endId);

		return new ImportResultDTO(
				startId,
				endId,
				result.getImported(),
				result.getFailed(),
				result.getDurationMillis(),
				"Import finished");

	}
}
