package com.adt.resource;

import jakarta.inject.Inject;

import com.adt.entity.dto.ExplainPlanDTO;
import com.adt.entity.dto.MaintenanceActionDTO;
import com.adt.service.DatabaseMaintenanceService;

/**
 * Implementierung der Wartungs-Endpunkte.
 */
public class DatabaseMaintenanceResourceImpl implements DatabaseMaintenanceResource {

        @Inject
        DatabaseMaintenanceService maintenanceService;

        @Override
        public MaintenanceActionDTO analyze() {
                return maintenanceService.analyzeStatistics();
        }

        @Override
        public ExplainPlanDTO explainReleaseRange(int startYear, int endYear, int limit) {
                return maintenanceService.explainReleaseRange(startYear, endYear, limit);
        }

        @Override
        public ExplainPlanDTO explainYearExtraction(int year, int limit) {
                return maintenanceService.explainYearExtraction(year, limit);
        }
}
