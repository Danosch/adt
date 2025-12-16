package com.adt.resource;

import java.time.LocalDate;

import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

import com.adt.entity.dto.ImportResultDTO;
import com.adt.entity.dto.ImportYearResultDTO;
import com.adt.service.MovieImportService;

/**
 * Implementierung der REST-Endpunkte, die den Import-Service aufrufen und eingehende Parameter validieren.
 */
public class MovieImportResourceImpl implements MovieImportResource {

	@Inject
	MovieImportService importService;

	/**
	 * Validiert den ID-Bereich und stößt anschließend den Import an.
	 */
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

	/**
	 * Validiert den Jahrgangsbereich, begrenzt ihn auf den erlaubten Zeitraum und stößt den Jahresimport an.
	 */
	@Override
	public ImportYearResultDTO importMoviesFromYears(int startYear, int endYear) {
		if (startYear <= 0 || endYear <= 0) {
			throw new BadRequestException("Parameters 'startYear' and 'endYear' must be positive");
		}
		if (endYear < startYear) {
			throw new BadRequestException("Parameter 'endYear' must be >= 'startYear'");
		}

		int currentYear = LocalDate.now().getYear();
		int effectiveEndYear = Math.min(endYear, currentYear);
		int effectiveStartYear = Math.max(startYear, 1874);

		if (effectiveStartYear > effectiveEndYear) {
			throw new BadRequestException(
					"Requested year range is outside the supported interval (>= 1874 and <= current year)");
		}

		var stats = importService.importMoviesForYearRange(effectiveStartYear, effectiveEndYear);

		return new ImportYearResultDTO(
				effectiveStartYear,
				effectiveEndYear,
				stats.getImported(),
				stats.getFailed(),
				stats.getDurationMillis(),
				"Import finished");
	}

}
