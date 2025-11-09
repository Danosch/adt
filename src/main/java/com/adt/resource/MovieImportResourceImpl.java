package com.adt.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

import java.time.LocalDate;

import com.adt.entity.dto.ImportResultDTO;
import com.adt.entity.dto.ImportYearResultDTO;
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

        @Override
        public ImportYearResultDTO importMoviesFromYears(int referenceYear) {
                if (referenceYear <= 0) {
                        throw new BadRequestException("Parameter 'year' must be positive");
                }

                var stats = importService.importMoviesForLastFortyYears(referenceYear);
                int currentYear = Math.min(referenceYear, LocalDate.now().getYear());
                int startYear = Math.min(Math.max(currentYear - 39, 1874), currentYear);

                return new ImportYearResultDTO(
                                startYear,
                                currentYear,
                                stats.getImported(),
                                stats.getFailed(),
                                stats.getDurationMillis(),
                                "Import finished");
        }
}
