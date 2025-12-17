package com.adt.entity.dto;

/**
 * Kombinierte Sicht auf Pool- und Datenbanklaufzeitmetriken.
 */
public record DatabaseObservabilitySnapshotDTO(
                PoolMetricsDTO pool,
                RuntimeMetricsDTO runtime,
                String description) {
}
