package com.adt.entity.dto;

import java.util.List;

/**
 * Transport-Objekt für Explain-Ausgaben mit Laufzeitmessung.
 */
public record ExplainPlanDTO(
                String action,
                long durationMillis,
                String description,
                List<String> planLines) {
}
