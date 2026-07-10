package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.MeasurementBound;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocationDashboardMeasurementBoundResolverTest {
    @Test
    void resolvesDifferentDatabaseTypesForMeasurementsInOneProfile() {
        MeasurementBound criticalHpc = measurementBound(1L, "HPC", "critical");
        MeasurementBound potableLegionella = measurementBound(2L, "Legionella", "potable");
        LocationDashboardMeasurementBoundResolver resolver = new LocationDashboardMeasurementBoundResolver(
            List.of(criticalHpc, potableLegionella),
            List.of(new LocationDashboardImportStrategyConfig.RangeProfileConfig(
                "domestic-hot",
                Map.of("HPC", "critical", "Legionella", "potable")
            ))
        );
        LocationDashboardImportStrategyConfig.RangeProfile profile =
            new LocationDashboardImportStrategyConfig.RangeProfile("domestic-hot");

        assertEquals(criticalHpc, resolver.resolve(" hpc ", profile));
        assertEquals(potableLegionella, resolver.resolve("LEGIONELLA", profile));
    }

    @Test
    void doesNotFallBackWhenConfiguredProfileOmitsMeasurement() {
        LocationDashboardMeasurementBoundResolver resolver = new LocationDashboardMeasurementBoundResolver(
            List.of(measurementBound(1L, "HPC", "domestic-hot")),
            List.of(new LocationDashboardImportStrategyConfig.RangeProfileConfig(
                "domestic-hot",
                Map.of("Legionella", "potable")
            ))
        );

        assertNull(resolver.resolve(
            "HPC",
            new LocationDashboardImportStrategyConfig.RangeProfile("domestic-hot")
        ));
    }

    @Test
    void rejectsDuplicateLocationBoundsForSameMeasurementAndType() {
        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> new LocationDashboardMeasurementBoundResolver(
                List.of(
                    measurementBound(1L, "HPC", "critical"),
                    measurementBound(2L, " hpc ", "CRITICAL")
                ),
                List.of()
            )
        );

        assertEquals(
            "Location has duplicate measurement bounds for  hpc  and type CRITICAL",
            error.getMessage()
        );
    }

    @Test
    void rejectsConfiguredMeasurementWhoseDatabaseBoundIsNotAssignedToLocation() {
        LocationDashboardMeasurementBoundResolver resolver = new LocationDashboardMeasurementBoundResolver(
            List.of(),
            List.of(new LocationDashboardImportStrategyConfig.RangeProfileConfig(
                "domestic-hot",
                Map.of("HPC", "critical")
            ))
        );

        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> resolver.resolve(
                "HPC",
                new LocationDashboardImportStrategyConfig.RangeProfile("domestic-hot")
            )
        );

        assertEquals(
            "No location measurement bound is configured for HPC and database type critical "
                + "selected by range profile domestic-hot",
            error.getMessage()
        );
    }

    @Test
    void preservesLegacyDirectTypeResolutionWhenNoProfilesAreConfigured() {
        MeasurementBound utilityHpc = measurementBound(1L, "HPC", "utility");
        LocationDashboardMeasurementBoundResolver resolver =
            new LocationDashboardMeasurementBoundResolver(List.of(utilityHpc), List.of());

        assertEquals(
            utilityHpc,
            resolver.resolve("HPC", new LocationDashboardImportStrategyConfig.RangeProfile("utility"))
        );
    }

    private MeasurementBound measurementBound(Long id, String measurementName, String type) {
        MeasurementBound bound = new MeasurementBound();
        bound.setId(id);
        bound.setMeasurementName(measurementName);
        bound.setType(type);
        return bound;
    }
}
