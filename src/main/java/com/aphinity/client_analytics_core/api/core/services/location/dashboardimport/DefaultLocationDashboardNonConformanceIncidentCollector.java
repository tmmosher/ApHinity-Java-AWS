package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategy.AnalyzedSamplePoint;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Location-independent incident identity policy for dashboard imports.
 */
@Component
final class DefaultLocationDashboardNonConformanceIncidentCollector
    implements LocationDashboardNonConformanceIncidentCollector {

    @Override
    public List<LocationDashboardNonConformanceIncident> collect(List<AnalyzedSamplePoint> analyzedSamples) {
        if (analyzedSamples == null || analyzedSamples.isEmpty()) {
            return List.of();
        }

        Map<String, LocationDashboardNonConformanceIncident> incidentsByIdentity = new LinkedHashMap<>();
        Map<String, Integer> sampleIncidentOrdinalsByIdentity = new LinkedHashMap<>();
        for (AnalyzedSamplePoint analyzedSample : analyzedSamples) {
            if (!isIncidentSource(analyzedSample)) {
                continue;
            }
            String baseIdentity = LocationDashboardCorrectiveActionMetadataSupport.identityKey(
                analyzedSample.measurementName(),
                analyzedSample.observedDate(),
                analyzedSample.identityValues(),
                analyzedSample.sampleIdentity()
            );
            if (baseIdentity == null) {
                continue;
            }

            String incidentIdentity = incidentIdentity(
                baseIdentity,
                analyzedSample,
                sampleIncidentOrdinalsByIdentity
            );
            LocationDashboardNonConformanceIncident incident = new LocationDashboardNonConformanceIncident(
                analyzedSample.observedDate(),
                analyzedSample.facilityName(),
                analyzedSample.measurementName(),
                analyzedSample.identityValues(),
                analyzedSample.sampleIdentity(),
                analyzedSample.resolved(),
                analyzedSample.turnaroundDays(),
                analyzedSample.systemTypeName()
            );
            incidentsByIdentity.merge(
                incidentIdentity,
                incident,
                LocationDashboardNonConformanceIncident::merge
            );
        }
        return List.copyOf(incidentsByIdentity.values());
    }

    private boolean isIncidentSource(AnalyzedSamplePoint sample) {
        return sample != null
            && sample.nonConforming()
            && sample.origin() != LocationDashboardImportStrategy.SampleOrigin.CORRECTIVE_ACTION_DRAFT;
    }

    private String incidentIdentity(
        String baseIdentity,
        AnalyzedSamplePoint sample,
        Map<String, Integer> sampleIncidentOrdinalsByIdentity
    ) {
        if (usesCanonicalIdentityWithoutOrdinal(sample)) {
            return baseIdentity;
        }
        int ordinal = sampleIncidentOrdinalsByIdentity.merge(baseIdentity, 1, Integer::sum);
        return ordinal == 1 ? baseIdentity : baseIdentity + "|incident|" + ordinal;
    }

    /**
     * Worksheet identities identify source cells, not distinct incidents, and
     * are intentionally excluded from the canonical key by metadata support.
     * Their presence still means repeated projections should merge on that key.
     * Persisted generated identities lack that guarantee, so they retain ordinals.
     */
    private boolean usesCanonicalIdentityWithoutOrdinal(AnalyzedSamplePoint sample) {
        return sample.sampleIdentity() != null
            && !sample.sampleIdentity().isBlank()
            && !sample.sampleIdentity().startsWith(
                LocationDashboardSamplePersistenceService.GENERATED_SAMPLE_IDENTITY_PREFIX
            );
    }
}
