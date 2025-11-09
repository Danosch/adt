package com.adt.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO für die API-Antwort beim Importvorgang. Wird von MovieImportResourceImpl zurückgegeben.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportResultDTO {

	/** Erste TMDB-ID im Importbereich */
	private int startId;

	/** Letzte TMDB-ID im Importbereich */
	private int endId;

	/** Anzahl erfolgreich importierter Filme */
	private int importedCount;

	/** Anzahl fehlgeschlagener oder übersprungener Filme */
	private int failedCount;

	/** Optionale Laufzeit in Millisekunden */
	private long durationMillis;

	/** Freitext-Statusnachricht */
	private String message;
}
