package com.adt.entity.dto;

/**
 * Transport-Objekt für Wartungsaktionen wie ANALYZE oder VACUUM.
 */
public record MaintenanceActionDTO(
                String action,
                long durationMillis,
                String details) {
}
