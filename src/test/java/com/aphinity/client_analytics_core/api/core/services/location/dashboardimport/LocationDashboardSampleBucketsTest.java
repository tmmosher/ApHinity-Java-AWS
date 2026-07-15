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
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardIdentityFixtures.identityValues;

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
    void analyzedSamplesUsesClosestFutureSampleForTurnaroundEvenWhenStillNonConforming() {
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
            new BigDecimal("12"),
            "Newport Beach",
            "Hospital",
            "Cooling Towers",
            "HPC",
            "Recirc Line",
            "CTI/514P"
        ));

        List<LocationDashboardAnalyzedSample> analyzedSamples = buckets.analyzedSamples();

        assertFalse(analyzedSamples.get(2).compliant());
        assertTrue(analyzedSamples.getFirst().resolved());
        assertEquals(2L, analyzedSamples.getFirst().turnaroundDays());
    }

    @Test
    void analyzedSamplesResolvesToNextSampleEvenWhenNoFutureConformanceExists() {
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
            LocalDate.parse("2025-08-03"),
            new BigDecimal("12"),
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
    void analyzedSamplesResolvesWhenTheAvailableDynamicIdentityValuesMatch() {
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

        assertTrue(analyzedSamples.getFirst().resolved());
        assertEquals(4L, analyzedSamples.getFirst().turnaroundDays());
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

    @Test
    void analyzedSamplesDoesNotResolveCommentFailureBeforeFailedResultIsReceived() {
        LocationDashboardSampleBuckets buckets = new LocationDashboardSampleBuckets();

        buckets.add(commentSample(
            LocationDashboardImportStrategy.SampleOrigin.COMMENT_SUPPLEMENTAL,
            LocalDate.parse("2026-01-30"),
            LocalDate.parse("2026-02-02"),
            new BigDecimal("93"),
            "Irvine",
            "Shared RODI",
            "Critical",
            "HPC",
            "DI Return",
            "Return from SPD"
        ));
        buckets.add(sample(
            LocalDate.parse("2026-02-01"),
            new BigDecimal("5"),
            "Irvine",
            "Shared RODI",
            "Critical",
            "HPC",
            "DI Return",
            "Return from SPD"
        ));

        List<LocationDashboardAnalyzedSample> analyzedSamples = buckets.analyzedSamples();

        assertFalse(analyzedSamples.getFirst().compliant());
        assertFalse(analyzedSamples.getFirst().resolved());
        assertNull(analyzedSamples.getFirst().turnaroundDays());
    }

    @Test
    void analyzedSamplesUsesCommentResultReceivedDateForResolutionOrderingAndTurnaround() {
        LocationDashboardSampleBuckets buckets = new LocationDashboardSampleBuckets();

        buckets.add(sample(
            LocalDate.parse("2026-01-01"),
            new BigDecimal("11"),
            "Irvine",
            "Shared RODI",
            "Critical",
            "HPC",
            "DI Return",
            "Return from SPD"
        ));
        buckets.add(commentSample(
            LocationDashboardImportStrategy.SampleOrigin.COMMENT_SUPPLEMENTAL,
            LocalDate.parse("2026-01-30"),
            LocalDate.parse("2026-02-05"),
            new BigDecimal("5"),
            "Irvine",
            "Shared RODI",
            "Critical",
            "HPC",
            "DI Return",
            "Return from SPD"
        ));

        List<LocationDashboardAnalyzedSample> analyzedSamples = buckets.analyzedSamples();

        assertTrue(analyzedSamples.getFirst().resolved());
        assertEquals(35L, analyzedSamples.getFirst().turnaroundDays());
    }

    @Test
    void analyzedSamplesResolvesAcrossCanonicalizedBuildingAliasesAndSystemLabels() {
        LocationDashboardSampleBuckets buckets = new LocationDashboardSampleBuckets();

        buckets.add(aliasSample(
            LocalDate.parse("2025-08-01"),
            new BigDecimal("620"),
            "16405 Irvine",
            "SPD-16405",
            "Utility Water",
            "HPC",
            "Sink",
            "Source to SPD"
        ));
        buckets.add(aliasSample(
            LocalDate.parse("2025-08-05"),
            new BigDecimal("2"),
            "16405 Irvine",
            "16105",
            "Utility Water SPD",
            "HPC",
            "Sink",
            "Source to SPD"
        ));

        List<LocationDashboardAnalyzedSample> analyzedSamples = buckets.analyzedSamples();

        assertTrue(analyzedSamples.getFirst().resolved());
        assertEquals(4L, analyzedSamples.getFirst().turnaroundDays());
    }

    @Test
    void analyzedSamplesAllowsResolutionWhenBuildingIsAbsentFromBothSamples() {
        LocationDashboardSampleBuckets buckets = new LocationDashboardSampleBuckets();

        buckets.add(sample(
            LocalDate.parse("2025-08-01"),
            new BigDecimal("11"),
            "Irvine",
            null,
            "Cooling Tower",
            "HPC",
            "Recirc Line",
            "CTI/514P"
        ));
        buckets.add(sample(
            LocalDate.parse("2025-08-05"),
            new BigDecimal("5"),
            "Irvine",
            null,
            "Cooling Tower",
            "HPC",
            "Recirc Line",
            "CTI/514P"
        ));

        List<LocationDashboardAnalyzedSample> analyzedSamples = buckets.analyzedSamples();

        assertTrue(analyzedSamples.getFirst().resolved());
        assertEquals(4L, analyzedSamples.getFirst().turnaroundDays());
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
                normalizeKey(facilityName),
                facilityName,
                List.of(facilityName),
                resolvedBuilding == null ? List.of() : List.of(resolvedBuilding),
                true
            ),
            systemType(resolvedSystem),
            measurementBound(measurementName, rangeProfile(resolvedSystem)),
            identityValues(facilityName, resolvedBuilding, resolvedSystem, pointOfUse, basis),
            null,
            null,
            "worksheet|" + observedDate,
            "F5",
            null
        );
    }

    private LocationDashboardCommentSample commentSample(
        LocationDashboardImportStrategy.SampleOrigin origin,
        LocalDate observedDate,
        LocalDate resultReceivedOn,
        BigDecimal numericValue,
        String facilityName,
        String resolvedBuilding,
        String resolvedSystem,
        String measurementName,
        String pointOfUse,
        String basis
    ) {
        return new LocationDashboardCommentSample(
            origin,
            observedDate,
            numericValue,
            measurementName,
            facilityName,
            new LocationDashboardImportStrategyConfig.SublocationConfig(
                normalizeKey(facilityName),
                facilityName,
                List.of(facilityName),
                resolvedBuilding == null ? List.of() : List.of(resolvedBuilding),
                true
            ),
            systemType(resolvedSystem),
            measurementBound(measurementName, rangeProfile(resolvedSystem)),
            identityValues(facilityName, resolvedBuilding, resolvedSystem, pointOfUse, basis),
            null,
            null,
            "AB41",
            null,
            new LocationDashboardCommentParser.ParsedCommentSample(
                observedDate,
                resultReceivedOn,
                numericValue.toPlainString() + " CFU.mL",
                numericValue,
                "CFU.mL",
                List.of(),
                List.of()
            ),
            "Supplemental Sample",
            "comment|" + observedDate + "|" + resultReceivedOn + "|" + numericValue
        );
    }

    private LocationDashboardWorksheetSample aliasSample(
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
                "16405-irvine",
                "16405 Irvine",
                List.of("Irvine"),
                List.of("16405", "16105", "SPD-16405"),
                false
            ),
            new LocationDashboardImportStrategyConfig.SystemTypeConfig(
                "utility-spd",
                "Utility SPD",
                new LocationDashboardImportStrategyConfig.RangeProfile("utility"),
                List.of("Utility Water", "Utility Water SPD", "Utility HLD")
            ),
            measurementBound(measurementName, new LocationDashboardImportStrategyConfig.RangeProfile("utility")),
            identityValues(facilityName, resolvedBuilding, resolvedSystem, pointOfUse, basis),
            null,
            null,
            "worksheet|" + observedDate,
            "K68",
            null
        );
    }

    private MeasurementBound measurementBound(
        String measurementName,
        LocationDashboardImportStrategyConfig.RangeProfile rangeProfile
    ) {
        MeasurementBound measurementBound = new MeasurementBound();
        measurementBound.setMeasurementName(measurementName);
        measurementBound.setType(rangeProfile.value());
        measurementBound.setMax(new LocationDashboardImportStrategyConfig.RangeProfile("utility").equals(rangeProfile)
            ? new BigDecimal("500")
            : new BigDecimal("10"));
        return measurementBound;
    }

    private LocationDashboardImportStrategyConfig.SystemTypeConfig systemType(String resolvedSystem) {
        String displayName = resolvedSystem == null || resolvedSystem.isBlank() ? "Cooling Towers" : resolvedSystem.strip();
        return new LocationDashboardImportStrategyConfig.SystemTypeConfig(
            normalizeKey(displayName),
            displayName,
            rangeProfile(displayName),
            List.of(displayName)
        );
    }

    private LocationDashboardImportStrategyConfig.RangeProfile rangeProfile(String resolvedSystem) {
        String normalized = normalizeKey(resolvedSystem);
        if (normalized != null && normalized.contains("utility")) {
            return new LocationDashboardImportStrategyConfig.RangeProfile("utility");
        }
        if (normalized != null && normalized.contains("critical")) {
            return new LocationDashboardImportStrategyConfig.RangeProfile("critical");
        }
        return new LocationDashboardImportStrategyConfig.RangeProfile("towers");
    }

    private String normalizeKey(String value) {
        return LocationDashboardGraphMetadataSupport.normalizeKey(value);
    }
}
