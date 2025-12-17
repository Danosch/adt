package com.adt.entity.dto;

/**
 * Zusammenfassung der aktuellen Pool-Auslastung aus Agroal-Metriken.
 */
public record PoolMetricsDTO(
                int activeConnections,
                int availableConnections,
                int awaitingConnections,
                int maxSize) {
}
