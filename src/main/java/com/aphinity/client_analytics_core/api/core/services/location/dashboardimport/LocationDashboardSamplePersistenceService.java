package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.LocationDashboardSample;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventStatus;
import com.aphinity.client_analytics_core.api.core.repositories.dashboard.LocationDashboardSampleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists imported dashboard sample facts used by rolling graph ranges and
 * historical derived graph rebuilding.
 */
@Service
public class LocationDashboardSamplePersistenceService {
    private static final int MAX_TEXT_LENGTH = 1024;
    static final String GENERATED_SAMPLE_IDENTITY_PREFIX = "__generated__|";

    private final LocationDashboardSampleRepository sampleRepository;

    public LocationDashboardSamplePersistenceService(LocationDashboardSampleRepository sampleRepository) {
        this.sampleRepository = sampleRepository;
    }

    /**
     * Replaces stored samples for a location using the analyzed samples and
     * observations from an import computation.
     *
     * @param location imported location
     * @param computation import computation to persist
     */
    @Transactional
    public void replaceLocationSamples(
        Location location,
        LocationDashboardImportStrategy.LocationDashboardImportComputation computation
    ) {
        replaceLocationSamples(
            location,
            computation == null ? List.of() : computation.analyzedSamples(),
            computation == null ? List.of() : computation.observations(),
            List.of()
        );
    }

    /**
     * Replaces stored samples and enriches them with matching corrective-action
     * resolution data.
     *
     * @param location imported location
     * @param computation import computation to persist
     * @param correctiveActions corrective actions created or matched during import
     */
    @Transactional
    public void replaceLocationSamples(
        Location location,
        LocationDashboardImportStrategy.LocationDashboardImportComputation computation,
        List<ServiceEvent> correctiveActions
    ) {
        replaceLocationSamples(
            location,
            computation == null ? List.of() : computation.analyzedSamples(),
            computation == null ? List.of() : computation.observations(),
            correctiveActions
        );
    }

    /**
     * Replaces stored samples from pre-analyzed historical points.
     *
     * @param location target location
     * @param analyzedSamples samples to persist
     */
    @Transactional
    public void replaceLocationSamples(
        Location location,
        List<LocationDashboardImportStrategy.AnalyzedSamplePoint> analyzedSamples
    ) {
        replaceLocationSamples(location, analyzedSamples, List.of(), List.of());
    }

    private void replaceLocationSamples(
        Location location,
        List<LocationDashboardImportStrategy.AnalyzedSamplePoint> analyzedSamples,
        List<LocationDashboardImportStrategy.ImportedObservation> observations,
        List<ServiceEvent> correctiveActions
    ) {
        if (location == null || location.getId() == null) {
            throw new IllegalArgumentException("Location is required");
        }

        sampleRepository.deleteByLocationId(location.getId());
        List<LocationDashboardSample> analyzedSampleFacts = toPersistedSamples(location, analyzedSamples);
        List<LocationDashboardSample> samples = analyzedSampleFacts;
        if (samples.isEmpty()) {
            samples = toPersistedObservationSamples(location, observations);
        } else {
            samples = combineSamples(
                analyzedSampleFacts,
                toPersistedCorrectiveActionSamples(location, analyzedSamples, correctiveActions)
            );
        }
        if (!samples.isEmpty()) {
            sampleRepository.saveAll(samples);
        }
    }

    /**
     * Loads persisted samples as analyzed sample points for derived graph rebuilds.
     *
     * @param locationId location id
     * @return persisted samples converted to import-domain records
     */
    @Transactional(readOnly = true)
    public List<LocationDashboardImportStrategy.AnalyzedSamplePoint> loadLocationSamples(Long locationId) {
        if (locationId == null) {
            return List.of();
        }
        return sampleRepository.findByLocation_IdOrderByObservedDateAscIdAsc(locationId).stream()
            .map(this::toAnalyzedSamplePoint)
            .toList();
    }

    List<LocationDashboardSample> toPersistedSamples(
        Location location,
        List<LocationDashboardImportStrategy.AnalyzedSamplePoint> analyzedSamples
    ) {
        if (analyzedSamples == null || analyzedSamples.isEmpty()) {
            return List.of();
        }

        Map<String, Integer> ordinalsByBaseIdentity = new LinkedHashMap<>();
        Map<String, LocationDashboardSample> samplesByIdentity = new LinkedHashMap<>();
        for (LocationDashboardImportStrategy.AnalyzedSamplePoint analyzedSample : analyzedSamples) {
            if (!canPersist(analyzedSample)) {
                continue;
            }
            String sampleIdentity = analyzedSample.sampleIdentity();
            if (!notBlank(sampleIdentity)) {
                String baseIdentity = generatedAnalyzedSampleIdentity(analyzedSample);
                int ordinal = ordinalsByBaseIdentity.merge(baseIdentity, 1, Integer::sum);
                sampleIdentity = baseIdentity + "|" + ordinal;
            }
            LocationDashboardSample persistedSample = new LocationDashboardSample();
            persistedSample.setLocation(location);
            persistedSample.setObservedDate(analyzedSample.observedDate());
            persistedSample.setSystemTypeName(truncate(analyzedSample.systemTypeName(), 256));
            persistedSample.setMeasurementName(truncate(analyzedSample.measurementName(), 256));
            persistedSample.setRawValue(truncate(analyzedSample.rawValue(), MAX_TEXT_LENGTH));
            persistedSample.setSampleIdentity(truncate(sampleIdentity, MAX_TEXT_LENGTH));
            persistedSample.setCompliant(analyzedSample.compliant());
            persistedSample.setResolved(analyzedSample.resolved());
            persistedSample.setTurnaroundDays(analyzedSample.turnaroundDays());
            persistedSample.setOrigin(analyzedSample.origin() == null ? "UNKNOWN" : analyzedSample.origin().name());
            samplesByIdentity.put(persistedSample.getSampleIdentity(), persistedSample);
        }
        return List.copyOf(new ArrayList<>(samplesByIdentity.values()));
    }

    private List<LocationDashboardSample> toPersistedObservationSamples(
        Location location,
        List<LocationDashboardImportStrategy.ImportedObservation> observations
    ) {
        if (observations == null || observations.isEmpty()) {
            return List.of();
        }

        Map<String, Integer> ordinalsByBaseIdentity = new LinkedHashMap<>();
        List<LocationDashboardSample> samples = new ArrayList<>();
        for (LocationDashboardImportStrategy.ImportedObservation observation : observations) {
            if (!canPersist(observation)) {
                continue;
            }
            String baseIdentity = observationIdentity(observation);
            int ordinal = ordinalsByBaseIdentity.merge(baseIdentity, 1, Integer::sum);

            LocationDashboardSample persistedSample = new LocationDashboardSample();
            persistedSample.setLocation(location);
            persistedSample.setObservedDate(observation.observedDate());
            persistedSample.setSystemTypeName(truncate(observation.systemTypeName(), 256));
            persistedSample.setMeasurementName(truncate(observation.measurementName(), 256));
            persistedSample.setSampleIdentity(truncate(baseIdentity + "|" + ordinal, MAX_TEXT_LENGTH));
            persistedSample.setCompliant(observation.compliant());
            persistedSample.setResolved(false);
            persistedSample.setOrigin(LocationDashboardImportStrategy.SampleOrigin.WORKSHEET.name());
            samples.add(persistedSample);
        }
        return List.copyOf(samples);
    }

    private List<LocationDashboardSample> toPersistedCorrectiveActionSamples(
        Location location,
        List<LocationDashboardImportStrategy.AnalyzedSamplePoint> analyzedSamples,
        List<ServiceEvent> correctiveActions
    ) {
        if (correctiveActions == null || correctiveActions.isEmpty()) {
            return List.of();
        }

        Map<String, Boolean> analyzedIdentities = new LinkedHashMap<>();
        if (analyzedSamples != null) {
            for (LocationDashboardImportStrategy.AnalyzedSamplePoint analyzedSample : analyzedSamples) {
                String identity = identityKey(analyzedSample);
                if (identity != null) {
                    analyzedIdentities.put(identity, Boolean.TRUE);
                }
            }
        }

        List<LocationDashboardSample> samples = new ArrayList<>();
        for (ServiceEvent correctiveAction : correctiveActions) {
            if (correctiveAction == null || !correctiveAction.isCorrectiveAction()) {
                continue;
            }
            Map<String, String> metadata = LocationDashboardCorrectiveActionMetadataSupport.parseStructuredMetadata(
                correctiveAction.getDescription()
            );
            LocalDate observedDate = LocationDashboardGraphMetadataSupport.parseLocalDate(metadata.get("observed at"));
            if (observedDate == null) {
                observedDate = correctiveAction.getEventDate();
            }
            String measurementName = metadata.get("measurement");
            String facilityName = firstNonBlank(
                metadata.get("sublocation"),
                metadata.get("facility")
            );
            String buildingName = metadata.get("building");
            String systemName = metadata.get("system");
            String metadataSampleIdentity = metadata.get("sample identity");
            String sampleIdentity = correctiveActionSampleIdentity(
                metadataSampleIdentity,
                correctiveAction,
                observedDate,
                facilityName,
                buildingName,
                systemName,
                measurementName,
                metadata.get("point of use"),
                metadata.get("basis")
            );
            String identity = LocationDashboardCorrectiveActionMetadataSupport.identityKey(
                measurementName,
                observedDate,
                facilityName,
                buildingName,
                systemName,
                metadata.get("point of use"),
                metadata.get("basis"),
                metadataSampleIdentity
            );
            if (identity == null || analyzedIdentities.containsKey(identity) || measurementName == null || observedDate == null) {
                continue;
            }

            LocationDashboardSample persistedSample = new LocationDashboardSample();
            persistedSample.setLocation(location);
            persistedSample.setObservedDate(observedDate);
            persistedSample.setMeasurementName(truncate(measurementName, 256));
            persistedSample.setSampleIdentity(truncate(sampleIdentity, MAX_TEXT_LENGTH));
            persistedSample.setCompliant(false);
            persistedSample.setResolved(correctiveAction.getStatus() == ServiceEventStatus.COMPLETED);
            persistedSample.setTurnaroundDays(turnaroundDays(observedDate, correctiveAction));
            persistedSample.setOrigin(LocationDashboardImportStrategy.SampleOrigin.CORRECTIVE_ACTION_DRAFT.name());
            samples.add(persistedSample);
            analyzedIdentities.put(identity, Boolean.TRUE);
        }
        return List.copyOf(samples);
    }

    private String correctiveActionSampleIdentity(
        String metadataSampleIdentity,
        ServiceEvent correctiveAction,
        LocalDate observedDate,
        String facilityName,
        String buildingName,
        String systemName,
        String measurementName,
        String pointOfUse,
        String basis
    ) {
        if (notBlank(metadataSampleIdentity)) {
            return metadataSampleIdentity;
        }
        String correctiveActionIdentity = LocationDashboardCorrectiveActionMetadataSupport.identityKey(
            correctiveAction.getTitle(),
            correctiveAction.getDescription()
        );
        String baseIdentity = generatedSampleIdentity(
            observedDate,
            facilityName,
            buildingName,
            systemName,
            null,
            measurementName,
            pointOfUse,
            basis,
            LocationDashboardImportStrategy.SampleOrigin.CORRECTIVE_ACTION_DRAFT
        );
        return correctiveActionIdentity == null ? baseIdentity : baseIdentity + "|" + correctiveActionIdentity;
    }

    private boolean canPersist(LocationDashboardImportStrategy.AnalyzedSamplePoint analyzedSample) {
        return analyzedSample != null
            && analyzedSample.observedDate() != null
            && notBlank(analyzedSample.measurementName());
    }

    private boolean canPersist(LocationDashboardImportStrategy.ImportedObservation observation) {
        return observation != null
            && observation.observedDate() != null
            && notBlank(observation.measurementName());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private List<LocationDashboardSample> combineSamples(List<LocationDashboardSample> primary, List<LocationDashboardSample> secondary) {
        Map<String, LocationDashboardSample> samplesByIdentity = new LinkedHashMap<>();
        if (primary != null) {
            for (LocationDashboardSample sample : primary) {
                if (sample != null && notBlank(sample.getSampleIdentity())) {
                    samplesByIdentity.put(sample.getSampleIdentity(), sample);
                }
            }
        }
        if (secondary != null) {
            for (LocationDashboardSample sample : secondary) {
                if (sample != null && notBlank(sample.getSampleIdentity())) {
                    samplesByIdentity.putIfAbsent(sample.getSampleIdentity(), sample);
                }
            }
        }
        return List.copyOf(samplesByIdentity.values());
    }

    private String identityKey(LocationDashboardImportStrategy.AnalyzedSamplePoint analyzedSample) {
        if (analyzedSample == null) {
            return null;
        }
        return LocationDashboardCorrectiveActionMetadataSupport.identityKey(
            analyzedSample.measurementName(),
            analyzedSample.observedDate(),
            analyzedSample.facilityName(),
            analyzedSample.buildingName(),
            analyzedSample.systemName(),
            analyzedSample.pointOfUse(),
            analyzedSample.basis(),
            analyzedSample.sampleIdentity()
        );
    }

    // this is kinda stupid to re-define the firstNonBlank method.
    private String firstNonBlank(String... values) {
        return LocationDashboardGraphMetadataSupport.firstNonBlank(values);
    }

    private Long turnaroundDays(LocalDate observedDate, ServiceEvent correctiveAction) {
        if (observedDate == null
            || correctiveAction == null
            || correctiveAction.getStatus() != ServiceEventStatus.COMPLETED
            || correctiveAction.getEndEventDate() == null) {
            return null;
        }
        LocalDateTime observedAt = LocalDateTime.of(observedDate, LocalTime.MIDNIGHT);
        LocalDateTime resolvedAt = LocalDateTime.of(
            correctiveAction.getEndEventDate(),
            correctiveAction.getEndEventTime() == null ? LocalTime.MIDNIGHT : correctiveAction.getEndEventTime()
        );
        return Math.max(0L, ChronoUnit.DAYS.between(observedAt, resolvedAt));
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private String observationIdentity(LocationDashboardImportStrategy.ImportedObservation observation) {
        return "observation|"
            + observation.observedDate()
            + "|"
            + nullSafeNormalized(observation.facilityName())
            + "|"
            + nullSafeNormalized(observation.systemTypeName())
            + "|"
            + nullSafeNormalized(observation.measurementName());
    }

    private String generatedAnalyzedSampleIdentity(LocationDashboardImportStrategy.AnalyzedSamplePoint analyzedSample) {
        return generatedSampleIdentity(
            analyzedSample.observedDate(),
            analyzedSample.facilityName(),
            analyzedSample.buildingName(),
            analyzedSample.systemName(),
            analyzedSample.systemTypeName(),
            analyzedSample.measurementName(),
            analyzedSample.pointOfUse(),
            analyzedSample.basis(),
            analyzedSample.origin()
        );
    }

    private String generatedSampleIdentity(
        LocalDate observedDate,
        String facilityName,
        String buildingName,
        String systemName,
        String systemTypeName,
        String measurementName,
        String pointOfUse,
        String basis,
        LocationDashboardImportStrategy.SampleOrigin origin
    ) {
        return GENERATED_SAMPLE_IDENTITY_PREFIX
            + observedDate
            + "|"
            + nullSafeIdentityToken(facilityName)
            + "|"
            + nullSafeIdentityToken(buildingName)
            + "|"
            + nullSafeIdentityToken(systemName)
            + "|"
            + nullSafeIdentityToken(systemTypeName)
            + "|"
            + nullSafeIdentityToken(measurementName)
            + "|"
            + nullSafeIdentityToken(pointOfUse)
            + "|"
            + nullSafeIdentityToken(basis)
            + "|"
            + (origin == null ? "" : origin.name());
    }

    private String nullSafeIdentityToken(String value) {
        if (value == null) {
            return "";
        }
        return value.strip();
    }

    private String nullSafeNormalized(String value) {
        if (value == null) {
            return "";
        }
        return value.strip().toLowerCase(java.util.Locale.ROOT);
    }

    LocationDashboardImportStrategy.AnalyzedSamplePoint toAnalyzedSamplePoint(LocationDashboardSample sample) {
        LocationDashboardImportStrategy.SampleOrigin origin = null;
        if (sample.getOrigin() != null && !sample.getOrigin().isBlank()) {
            try {
                origin = LocationDashboardImportStrategy.SampleOrigin.valueOf(sample.getOrigin());
            } catch (IllegalArgumentException ignored) {
                // kinda shitty to purposefully ignore a malformed origin. Should
                // probably log this, or at least warn about it.
            }
        }
        PersistedSampleIdentity identity = PersistedSampleIdentity.parse(sample.getSampleIdentity());
        return new LocationDashboardImportStrategy.AnalyzedSamplePoint(
            sample.getObservedDate(),
            identity.facilityName(),
            identity.buildingName(),
            identity.systemName(),
            firstNonBlank(sample.getSystemTypeName(), identity.systemTypeName()),
            firstNonBlank(sample.getMeasurementName(), identity.measurementName()),
            identity.pointOfUse(),
            identity.basis(),
            sample.getRawValue(),
            rehydratedSampleIdentity(sample.getSampleIdentity()),
            sample.isCompliant(),
            sample.isResolved(),
            sample.getTurnaroundDays(),
            origin
        );
    }

    private String rehydratedSampleIdentity(String sampleIdentity) {
        return sampleIdentity;
    }

    private record PersistedSampleIdentity(
        String facilityName,
        String buildingName,
        String systemName,
        String systemTypeName,
        String measurementName,
        String pointOfUse,
        String basis
    ) {
        static PersistedSampleIdentity parse(String sampleIdentity) {
            if (!notBlank(sampleIdentity)) {
                return empty();
            }
            String[] tokens = sampleIdentity.split("\\|", -1);
            if (sampleIdentity.startsWith(GENERATED_SAMPLE_IDENTITY_PREFIX)) {
                return new PersistedSampleIdentity(
                    token(tokens, 2),
                    token(tokens, 3),
                    token(tokens, 4),
                    token(tokens, 5),
                    token(tokens, 6),
                    token(tokens, 7),
                    token(tokens, 8)
                );
            }
            if (sampleIdentity.startsWith("primary-sample|")
                || sampleIdentity.startsWith("supplemental-sample-")) {
                return new PersistedSampleIdentity(
                    token(tokens, 1),
                    token(tokens, 2),
                    token(tokens, 3),
                    null,
                    token(tokens, 4),
                    token(tokens, 5),
                    token(tokens, 6)
                );
            }
            if (sampleIdentity.startsWith("observation|")) {
                return new PersistedSampleIdentity(
                    token(tokens, 2),
                    null,
                    null,
                    token(tokens, 3),
                    token(tokens, 4),
                    null,
                    null
                );
            }
            return empty();
        }

        private static PersistedSampleIdentity empty() {
            return new PersistedSampleIdentity(null, null, null, null, null, null, null);
        }

        private static String token(String[] tokens, int index) {
            if (tokens == null || index < 0 || index >= tokens.length) {
                return null;
            }
            String token = tokens[index];
            return token == null || token.isBlank() ? null : token.strip();
        }
    }
}
