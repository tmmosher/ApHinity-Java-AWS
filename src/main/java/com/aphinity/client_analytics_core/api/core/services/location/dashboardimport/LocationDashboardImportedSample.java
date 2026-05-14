package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.MeasurementBound;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.SublocationConfig;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.SystemTypeConfig;

interface LocationDashboardImportedSample {
    LocationDashboardImportStrategy.SampleOrigin origin();

    LocalDate observedDate();

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

    String cellReference();

    default String systemTypeName() {
        return systemType() == null ? null : systemType().displayName();
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
    String cellReference
) implements LocationDashboardImportedSample {
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
    String cellReference,
    LocationDashboardCommentParser.ParsedComment parsedComment,
    LocationDashboardCommentParser.ParsedCommentSample parsedSample,
    String sampleLabel,
    String sampleIdentity
) implements LocationDashboardImportedSample {
}

record LocationDashboardAnalyzedSample(
    LocationDashboardImportedSample sample,
    boolean compliant
) {
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
            compliant,
            sample.origin()
        );
    }
}
