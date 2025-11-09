package com.adt.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API-Antwort für den Jahresimport über die TMDB-Schnittstelle.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportYearResultDTO {

        /** Erstes Jahr im betrachteten Zeitraum */
        private int startYear;

        /** Letztes Jahr im betrachteten Zeitraum */
        private int endYear;

        /** Anzahl erfolgreich importierter Filme */
        private int importedCount;

        /** Anzahl fehlgeschlagener oder übersprungener Filme */
        private int failedCount;

        /** Gesamtdauer des Imports in Millisekunden */
        private long durationMillis;

        /** Freitext-Statusnachricht */
        private String message;
}
