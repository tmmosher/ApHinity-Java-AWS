package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.MeasurementBound;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationDashboardSampleBucketsTest {
    @Test
    void analyzedSamplesMarksNonConformanceResolvedWhenFutureCompliantSampleExistsInSameBucket() {
        LocationDashboardSampleBuckets buckets = new LocationDashboardSampleBuckets();

        buckets.add(sample(
            LocalDate.parse("2025-08-01"),
            new BigDecimal("11"),
            "Newport Beach",
            "Hospital",
            "Cooling Towers",
            "HPC",
            "Recirc Line",
            "CTI/514P"
        ));
        buckets.add(sample(
            LocalDate.parse("2025-08-05"),
            new BigDecimal("5"),
            "Newport Beach",
            "Hospital",
            "Cooling Towers",
            "HPC",
            "Recirc Line",
            "CTI/514P"
        ));

        List<LocationDashboardAnalyzedSample> analyzedSamples = buckets.analyzedSamples();

        assertEquals(2, analyzedSamples.size());
        assertFalse(analyzedSamples.getFirst().compliant());
        assertTrue(analyzedSamples.getFirst().resolved());
        assertEquals(4L, analyzedSamples.getFirst().turnaroundDays());
        assertTrue(analyzedSamples.get(1).compliant());
        assertFalse(analyzedSamples.get(1).resolved());
        assertNull(analyzedSamples.get(1).turnaroundDays());
    }

    @Test
    void analyzedSamplesDoesNotResolveAcrossDifferentLeafDimensions() {
        LocationDashboardSampleBuckets buckets = new LocationDashboardSampleBuckets();

        buckets.add(sample(
            LocalDate.parse("2025-08-01"),
            new BigDecimal("11"),
            "Newport Beach",
            "Hospital",
            "Cooling Towers",
            "HPC",
            "Recirc Line",
            "CTI/514P"
        ));
        buckets.add(sample(
            LocalDate.parse("2025-08-05"),
            new BigDecimal("5"),
            "Newport Beach",
            "Hospital",
            "Cooling Towers",
            "HPC",
            "Makeup Line",
            "CTI/514P"
        ));

        List<LocationDashboardAnalyzedSample> analyzedSamples = buckets.analyzedSamples();

        assertEquals(2, analyzedSamples.size());
        assertFalse(analyzedSamples.getFirst().compliant());
        assertFalse(analyzedSamples.getFirst().resolved());
        assertNull(analyzedSamples.getFirst().turnaroundDays());
    }

    @Test
    void analyzedSamplesUsesClosestFutureConformingSampleForTurnaround() {
        LocationDashboardSampleBuckets buckets = new LocationDashboardSampleBuckets();

        buckets.add(sample(
            LocalDate.parse("2025-08-01"),
            new BigDecimal("11"),
            "Newport Beach",
            "Hospital",
            "Cooling Towers",
            "HPC",
            "Recirc Line",
            "CTI/514P"
        ));
        buckets.add(sample(
            LocalDate.parse("2025-08-10"),
            new BigDecimal("5"),
            "Newport Beach",
            "Hospital",
            "Cooling Towers",
            "HPC",
            "Recirc Line",
            "CTI/514P"
        ));
        buckets.add(sample(
            LocalDate.parse("2025-08-03"),
            new BigDecimal("4"),
            "Newport Beach",
            "Hospital",
            "Cooling Towers",
            "HPC",
            "Recirc Line",
            "CTI/514P"
        ));

        List<LocationDashboardAnalyzedSample> analyzedSamples = buckets.analyzedSamples();

        assertTrue(analyzedSamples.getFirst().resolved());
        assertEquals(2L, analyzedSamples.getFirst().turnaroundDays());
    }

    @Test
    void analyzedSamplesDoesNotResolveWhenRequiredIdentityIsIncomplete() {
        LocationDashboardSampleBuckets buckets = new LocationDashboardSampleBuckets();

        buckets.add(sample(
            LocalDate.parse("2025-08-01"),
            new BigDecimal("11"),
            "Newport Beach",
            "Hospital",
            "Cooling Towers",
            "HPC",
            null,
            "CTI/514P"
        ));
        buckets.add(sample(
            LocalDate.parse("2025-08-05"),
            new BigDecimal("5"),
            "Newport Beach",
            "Hospital",
            "Cooling Towers",
            "HPC",
            null,
            "CTI/514P"
        ));

        List<LocationDashboardAnalyzedSample> analyzedSamples = buckets.analyzedSamples();

        assertFalse(analyzedSamples.getFirst().resolved());
        assertNull(analyzedSamples.getFirst().turnaroundDays());
    }

    @Test
    void analyzedSamplesDoesNotTreatSameDayConformanceAsResolution() {
        LocationDashboardSampleBuckets buckets = new LocationDashboardSampleBuckets();

        buckets.add(sample(
            LocalDate.parse("2025-08-01"),
            new BigDecimal("11"),
            "Newport Beach",
            "Hospital",
            "Cooling Towers",
            "HPC",
            "Recirc Line",
            "CTI/514P"
        ));
        buckets.add(sample(
            LocalDate.parse("2025-08-01"),
            new BigDecimal("5"),
            "Newport Beach",
            "Hospital",
            "Cooling Towers",
            "HPC",
            "Recirc Line",
            "CTI/514P"
        ));

        List<LocationDashboardAnalyzedSample> analyzedSamples = buckets.analyzedSamples();

        assertFalse(analyzedSamples.getFirst().resolved());
        assertNull(analyzedSamples.getFirst().turnaroundDays());
    }

    private LocationDashboardWorksheetSample sample(
        LocalDate observedDate,
        BigDecimal numericValue,
        String facilityName,
        String resolvedBuilding,
        String resolvedSystem,
        String measurementName,
        String pointOfUse,
        String basis
    ) {
        return new LocationDashboardWorksheetSample(
            observedDate,
            numericValue,
            measurementName,
            facilityName,
            new LocationDashboardImportStrategyConfig.SublocationConfig(
                "newport-beach",
                facilityName,
                List.of(facilityName),
                List.of(resolvedBuilding),
                true
            ),
            new LocationDashboardImportStrategyConfig.SystemTypeConfig(
                "cooling-towers",
                "Cooling Towers",
                LocationDashboardImportStrategyConfig.RangeProfile.TOWERS,
                List.of("Cooling Towers")
            ),
            measurementBound(measurementName),
            resolvedBuilding,
            resolvedSystem,
            pointOfUse,
            basis,
            "F5"
        );
    }

    private MeasurementBound measurementBound(String measurementName) {
        MeasurementBound measurementBound = new MeasurementBound();
        measurementBound.setMeasurementName(measurementName);
        measurementBound.setTowersRangeMax(new BigDecimal("10"));
        return measurementBound;
    }
}
