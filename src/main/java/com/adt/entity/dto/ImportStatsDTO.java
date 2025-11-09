package com.adt.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Hält interne Import-Statistiken über den ausgeführten TMDB-Import. Wird von MovieImportService zurückgegeben.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportStatsDTO {

	/** Anzahl erfolgreich importierter Filme */
	private int imported;

	/** Anzahl fehlgeschlagener oder übersprungener Filme */
	private int failed;

	/** Gesamtdauer des Imports in Millisekunden */
	private long durationMillis;
}
