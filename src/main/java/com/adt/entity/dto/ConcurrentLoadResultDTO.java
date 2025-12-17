package com.adt.entity.dto;

/**
 * Ergebnis eines parallelen Lasttests gegen die Datenbank.
 */
public record ConcurrentLoadResultDTO(
                int virtualUsers,
                long durationMillis,
                long rowsRead,
                int successfulQueries,
                int failedQueries,
                String description) {
}
