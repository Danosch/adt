package com.adt.entity.dto;

/**
 * Transport-Objekt für die Laufzeitmessung einer konkreten Datenbankabfrage.
 */
public record QueryPerformanceDTO(
                String queryType,
                long durationMillis,
                int rowsReturned,
                String description) {
}
