package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.MeasurementBound;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.SublocationConfig;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.SystemTypeConfig;

interface LocationDashboardImportedSample {
    LocationDashboardImportStrategy.SampleOrigin origin();

    LocalDate observedDate();

    default LocalDate resolutionAnchorDate() {
        return observedDate();
    }

    BigDecimal numericValue();

    String measurementName();

    String facilityName();

    SublocationConfig sublocation();

    SystemTypeConfig systemType();

    MeasurementBound measurementBound();

    Map<String, String> identityValues();

    default Map<String, String> resolutionIdentityValues() {
        Map<String, String> resolved = new LinkedHashMap<>();
        identityValues().forEach((key, value) -> resolved.put(key, canonicalIdentityValue(value)));
        return LocationDashboardIdentitySupport.immutableCopy(resolved);
    }

    String rawValue();

    default String units() {
        return null;
    }

    default String sampleIdentity() {
        return null;
    }

    String cellReference();

    default String systemTypeName() {
        return systemType() == null ? null : systemType().displayName();
    }

    private String canonicalIdentityValue(String value) {
        String normalizedValue = LocationDashboardGraphMetadataSupport.normalizeKey(value);
        SublocationConfig sublocation = sublocation();
        if (sublocation != null
            && (matches(normalizedValue, sublocation.key())
                || matches(normalizedValue, sublocation.displayName())
                || matchesAny(normalizedValue, sublocation.facilityAliases())
                || matchesAny(normalizedValue, sublocation.buildingAliases()))) {
            return sublocation.displayName();
        }
        SystemTypeConfig systemType = systemType();
        if (systemType != null
            && (matches(normalizedValue, systemType.key())
                || matches(normalizedValue, systemType.displayName())
                || matchesAny(normalizedValue, systemType.aliases()))) {
            return systemType.displayName();
        }
        return value;
    }

    private boolean matchesAny(String normalizedValue, List<String> aliases) {
        if (aliases == null) {
            return false;
        }
        return aliases.stream().anyMatch(alias -> matches(normalizedValue, alias));
    }

    private boolean matches(String normalizedValue, String candidate) {
        return Objects.equals(
            normalizedValue,
            LocationDashboardGraphMetadataSupport.normalizeKey(candidate)
        );
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
    Map<String, String> identityValues,
    String rawValue,
    String units,
    String sampleIdentity,
    String cellReference,
    LocationDashboardCommentParser.ParsedComment parsedComment
) implements LocationDashboardImportedSample {
    LocationDashboardWorksheetSample {
        identityValues = LocationDashboardIdentitySupport.immutableCopy(identityValues);
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
    Map<String, String> identityValues,
    String rawValue,
    String units,
    String cellReference,
    LocationDashboardCommentParser.ParsedComment parsedComment,
    LocationDashboardCommentParser.ParsedCommentSample parsedSample,
    String sampleLabel,
    String sampleIdentity
) implements LocationDashboardImportedSample {
    LocationDashboardCommentSample {
        identityValues = LocationDashboardIdentitySupport.immutableCopy(identityValues);
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
            sample.systemTypeName(),
            sample.measurementName(),
            sample.identityValues(),
            sample.rawValue(),
            sample.units(),
            sample.sampleIdentity(),
            compliant,
            resolved,
            turnaroundDays,
            sample.origin()
        );
    }
}
