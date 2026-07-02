package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.MeasurementBound;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.SublocationConfig;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.SystemTypeConfig;

interface LocationDashboardImportedSample {
    LocationDashboardImportStrategy.SampleOrigin origin();

    LocalDate observedDate();

    default LocalDate resolutionAnchorDate() {
        return observedDate();
    }

    default String resolutionBuildingName() {
        String resolvedBuilding = resolvedBuilding();
        SublocationConfig sublocation = sublocation();
        if (resolvedBuilding == null || sublocation == null || sublocation.displayName() == null) {
            return resolvedBuilding;
        }
        String normalizedBuilding = normalizeKey(resolvedBuilding);
        String normalizedDisplayName = normalizeKey(sublocation.displayName());
        if (Objects.equals(normalizedBuilding, normalizedDisplayName)
            || matchesAlias(normalizedBuilding, sublocation.buildingAliases())) {
            return sublocation.displayName();
        }
        return resolvedBuilding;
    }

    default String resolutionSystemName() {
        SystemTypeConfig systemType = systemType();
        if (systemType != null && systemType.displayName() != null && !systemType.displayName().isBlank()) {
            return systemType.displayName();
        }
        return resolvedSystem();
    }

    BigDecimal numericValue();

    String measurementName();

    String facilityName();

    SublocationConfig sublocation();

    SystemTypeConfig systemType();

    MeasurementBound measurementBound();

    String resolvedBuilding();

    String resolvedSystem();

    String pointOfUse();

    String basis();

    String rawValue();

    default String sampleIdentity() {
        return null;
    }

    String cellReference();

    default String systemTypeName() {
        return systemType() == null ? null : systemType().displayName();
    }

    private static boolean matchesAlias(String normalizedCandidate, java.util.List<String> aliases) {
        if (normalizedCandidate == null || aliases == null || aliases.isEmpty()) {
            return false;
        }
        for (String alias : aliases) {
            if (Objects.equals(normalizedCandidate, normalizeKey(alias))) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeKey(String value) {
        return LocationDashboardGraphMetadataSupport.normalizeKey(value);
    }
}

record LocationDashboardWorksheetSample(
    LocalDate observedDate,
    BigDecimal numericValue,
    String measurementName,
    String facilityName,
    SublocationConfig sublocation,
    SystemTypeConfig systemType,
    MeasurementBound measurementBound,
    String resolvedBuilding,
    String resolvedSystem,
    String pointOfUse,
    String basis,
    String rawValue,
    String cellReference,
    LocationDashboardCommentParser.ParsedComment parsedComment
) implements LocationDashboardImportedSample {
    LocationDashboardWorksheetSample(
        LocalDate observedDate,
        BigDecimal numericValue,
        String measurementName,
        String facilityName,
        SublocationConfig sublocation,
        SystemTypeConfig systemType,
        MeasurementBound measurementBound,
        String resolvedBuilding,
        String resolvedSystem,
        String pointOfUse,
        String basis,
        String cellReference
    ) {
        this(
            observedDate,
            numericValue,
            measurementName,
            facilityName,
            sublocation,
            systemType,
            measurementBound,
            resolvedBuilding,
            resolvedSystem,
            pointOfUse,
            basis,
            null,
            cellReference,
            null
        );
    }

    LocationDashboardWorksheetSample(
        LocalDate observedDate,
        BigDecimal numericValue,
        String measurementName,
        String facilityName,
        SublocationConfig sublocation,
        SystemTypeConfig systemType,
        MeasurementBound measurementBound,
        String resolvedBuilding,
        String resolvedSystem,
        String pointOfUse,
        String basis,
        String rawValue,
        String cellReference
    ) {
        this(
            observedDate,
            numericValue,
            measurementName,
            facilityName,
            sublocation,
            systemType,
            measurementBound,
            resolvedBuilding,
            resolvedSystem,
            pointOfUse,
            basis,
            rawValue,
            cellReference,
            null
        );
    }

    @Override
    public LocationDashboardImportStrategy.SampleOrigin origin() {
        return LocationDashboardImportStrategy.SampleOrigin.WORKSHEET;
    }
}

record LocationDashboardCommentSample(
    LocationDashboardImportStrategy.SampleOrigin origin,
    LocalDate observedDate,
    BigDecimal numericValue,
    String measurementName,
    String facilityName,
    SublocationConfig sublocation,
    SystemTypeConfig systemType,
    MeasurementBound measurementBound,
    String resolvedBuilding,
    String resolvedSystem,
    String pointOfUse,
    String basis,
    String rawValue,
    String cellReference,
    LocationDashboardCommentParser.ParsedComment parsedComment,
    LocationDashboardCommentParser.ParsedCommentSample parsedSample,
    String sampleLabel,
    String sampleIdentity
) implements LocationDashboardImportedSample {
    LocationDashboardCommentSample(
        LocationDashboardImportStrategy.SampleOrigin origin,
        LocalDate observedDate,
        BigDecimal numericValue,
        String measurementName,
        String facilityName,
        SublocationConfig sublocation,
        SystemTypeConfig systemType,
        MeasurementBound measurementBound,
        String resolvedBuilding,
        String resolvedSystem,
        String pointOfUse,
        String basis,
        String cellReference,
        LocationDashboardCommentParser.ParsedComment parsedComment,
        LocationDashboardCommentParser.ParsedCommentSample parsedSample,
        String sampleLabel,
        String sampleIdentity
    ) {
        this(
            origin,
            observedDate,
            numericValue,
            measurementName,
            facilityName,
            sublocation,
            systemType,
            measurementBound,
            resolvedBuilding,
            resolvedSystem,
            pointOfUse,
            basis,
            null,
            cellReference,
            parsedComment,
            parsedSample,
            sampleLabel,
            sampleIdentity
        );
    }

    @Override
    public LocalDate resolutionAnchorDate() {
        if (parsedSample != null && parsedSample.resultReceivedOn() != null) {
            return parsedSample.resultReceivedOn();
        }
        return observedDate;
    }
}

record LocationDashboardAnalyzedSample(
    LocationDashboardImportedSample sample,
    boolean compliant,
    boolean resolved,
    Long turnaroundDays
) {
    LocationDashboardAnalyzedSample(LocationDashboardImportedSample sample, boolean compliant) {
        this(sample, compliant, false, null);
    }

    LocationDashboardAnalyzedSample withResolution(boolean resolved, Long turnaroundDays) {
        return new LocationDashboardAnalyzedSample(sample, compliant, resolved, turnaroundDays);
    }

    LocationDashboardImportStrategy.ImportedObservation toObservation() {
        return new LocationDashboardImportStrategy.ImportedObservation(
            sample.observedDate(),
            sample.facilityName(),
            sample.systemTypeName(),
            sample.measurementName(),
            compliant
        );
    }

    LocationDashboardImportStrategy.AnalyzedSamplePoint toAnalyzedSamplePoint() {
        return new LocationDashboardImportStrategy.AnalyzedSamplePoint(
            sample.observedDate(),
            sample.facilityName(),
            sample.resolvedBuilding(),
            sample.resolvedSystem(),
            sample.systemTypeName(),
            sample.measurementName(),
            sample.pointOfUse(),
            sample.basis(),
            sample.rawValue(),
            sample.sampleIdentity(),
            compliant,
            resolved,
            turnaroundDays,
            sample.origin()
        );
    }
}
