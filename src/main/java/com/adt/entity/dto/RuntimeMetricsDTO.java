package com.adt.entity.dto;

/**
 * Laufzeitstatistiken aus PostgreSQL-Systemviews (Locks, Autovacuum, Checkpoints).
 */
public record RuntimeMetricsDTO(
                long lockWaits,
                long autovacuumWorkers,
                long deadlocksTotal,
                long checkpointsTimed,
                long checkpointsRequested) {
}
