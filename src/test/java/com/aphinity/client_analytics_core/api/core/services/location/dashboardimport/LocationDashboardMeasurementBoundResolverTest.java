package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.MeasurementBound;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocationDashboardMeasurementBoundResolverTest {
    @Test
    void resolvesConfiguredMeasurementsUsingTheRangeProfileAsDatabaseType() {
        MeasurementBound domesticHpc = measurementBound(1L, "HPC", "domestic-hot");
        MeasurementBound domesticLegionella = measurementBound(2L, "Legionella", "domestic-hot");
        LocationDashboardMeasurementBoundResolver resolver = new LocationDashboardMeasurementBoundResolver(
            List.of(domesticHpc, domesticLegionella),
            List.of(new LocationDashboardImportStrategyConfig.RangeProfileConfig(
                "domestic-hot",
                List.of("HPC", "Legionella")
            ))
        );
        LocationDashboardImportStrategyConfig.RangeProfile profile =
            new LocationDashboardImportStrategyConfig.RangeProfile("domestic-hot");

        assertEquals(domesticHpc, resolver.resolve(" hpc ", profile));
        assertEquals(domesticLegionella, resolver.resolve("LEGIONELLA", profile));
    }

    @Test
    void doesNotFallBackWhenConfiguredProfileOmitsMeasurement() {
        LocationDashboardMeasurementBoundResolver resolver = new LocationDashboardMeasurementBoundResolver(
            List.of(measurementBound(1L, "HPC", "domestic-hot")),
            List.of(new LocationDashboardImportStrategyConfig.RangeProfileConfig(
                "domestic-hot",
                List.of("Legionella")
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
                List.of("HPC")
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
            "No location measurement bound is configured for HPC and database type domestic-hot "
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
