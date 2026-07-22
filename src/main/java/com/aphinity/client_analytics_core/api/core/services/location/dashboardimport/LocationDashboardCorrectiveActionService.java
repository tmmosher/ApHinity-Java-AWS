package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventResponsibility;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventStatus;
import com.aphinity.client_analytics_core.api.core.repositories.servicecalendar.ServiceEventRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Owns corrective-action preview rules and historical corrective-action reconstruction.
 */
@Component
public final class LocationDashboardCorrectiveActionService {
    private static final LocalTime ALL_DAY_START_TIME = LocalTime.MIDNIGHT;
    private static final LocalTime ALL_DAY_END_TIME = LocalTime.of(23, 59, 59);

    private final ServiceEventRepository serviceEventRepository;
    private final Clock clock;
    private final DashboardImportStrategyResolver strategyRegistry;

    LocationDashboardCorrectiveActionService(ServiceEventRepository serviceEventRepository, Clock clock) {
        this(serviceEventRepository, clock, null);
    }

    @Autowired
    public LocationDashboardCorrectiveActionService(
        ServiceEventRepository serviceEventRepository,
        Clock clock,
        DashboardImportStrategyResolver strategyRegistry
    ) {
        this.serviceEventRepository = serviceEventRepository;
        this.clock = clock;
        this.strategyRegistry = strategyRegistry;
    }

    /**
     * Combines persisted corrective actions with new import drafts without
     * saving the drafts.
     *
     * @param locationId location id
     * @param correctiveActions draft corrective actions from the import
     * @return preview events sorted for the service calendar response
     */
    List<ServiceEvent> buildPreviewCorrectiveActions(
        Long locationId,
        List<LocationDashboardImportStrategy.CorrectiveActionDraft> correctiveActions
    ) {
        List<ServiceEvent> persistedCorrectiveActions =
            serviceEventRepository.findByLocation_IdAndCorrectiveActionTrueOrderByEventDateAscEventTimeAscIdAsc(locationId);
        if (correctiveActions.isEmpty()) {
            return persistedCorrectiveActions;
        }

        Map<String, Integer> persistedIndexesByTitle = new LinkedHashMap<>();
        List<ServiceEvent> previewCorrectiveActions = new ArrayList<>();
        for (int index = 0; index < persistedCorrectiveActions.size(); index += 1) {
            ServiceEvent existingCorrectiveAction = persistedCorrectiveActions.get(index);
            previewCorrectiveActions.add(copyServiceEvent(existingCorrectiveAction));
            for (String identity : correctiveActionIdentities(
                existingCorrectiveAction.getTitle(),
                existingCorrectiveAction.getDescription()
            )) {
                persistedIndexesByTitle.putIfAbsent(identity, index);
            }
        }

        for (LocationDashboardImportStrategy.CorrectiveActionDraft draft : correctiveActions) {
            String correctiveActionIdentity = firstMatchingIdentity(
                correctiveActionIdentities(draft.title(), draft.description()),
                persistedIndexesByTitle
            );
            Integer existingIndex = persistedIndexesByTitle.get(correctiveActionIdentity);
            if (existingIndex != null) {
                ServiceEvent previewEvent = previewCorrectiveActions.get(existingIndex);
                previewEvent.setTitle(draft.title());
                previewEvent.setDescription(draft.description());
                if (draft.resolved()) {
                    completeServiceEvent(previewEvent, draft);
                }
                continue;
            }
            previewCorrectiveActions.add(createPreviewServiceEvent(draft));
        }

        return List.copyOf(previewCorrectiveActions);
    }

    /**
     * Persists newly imported corrective-action drafts, reusing existing matching
     * corrective actions when present.
     *
     * @param location target location
     * @param correctiveActions draft corrective actions from the import
     * @return full corrective-action set for the location after persistence
     */
    List<ServiceEvent> persistCorrectiveActions(
        Location location,
        List<LocationDashboardImportStrategy.CorrectiveActionDraft> correctiveActions
    ) {
        if (location == null || location.getId() == null) {
            throw new IllegalArgumentException("Location is required");
        }

        List<ServiceEvent> persistedCorrectiveActions =
            serviceEventRepository.findByLocation_IdAndCorrectiveActionTrueOrderByEventDateAscEventTimeAscIdAsc(location.getId());
        if (correctiveActions == null || correctiveActions.isEmpty()) {
            return persistedCorrectiveActions;
        }

        Map<String, ServiceEvent> persistedByIdentity = new LinkedHashMap<>();
        for (ServiceEvent persistedCorrectiveAction : persistedCorrectiveActions) {
            for (String identity : correctiveActionIdentities(
                persistedCorrectiveAction.getTitle(),
                persistedCorrectiveAction.getDescription()
            )) {
                persistedByIdentity.putIfAbsent(identity, persistedCorrectiveAction);
            }
        }

        List<ServiceEvent> correctiveActionsToSave = new ArrayList<>();
        List<ServiceEvent> effectiveCorrectiveActions = new ArrayList<>(persistedCorrectiveActions);
        for (LocationDashboardImportStrategy.CorrectiveActionDraft draft : correctiveActions) {
            Set<String> correctiveActionIdentities = correctiveActionIdentities(draft.title(), draft.description());
            String correctiveActionIdentity = firstMatchingIdentity(correctiveActionIdentities, persistedByIdentity);
            ServiceEvent existingCorrectiveAction = persistedByIdentity.get(correctiveActionIdentity);
            if (existingCorrectiveAction != null) {
                if (needsCompletionUpdate(existingCorrectiveAction, draft)) {
                    completeServiceEvent(existingCorrectiveAction, draft);
                    correctiveActionsToSave.add(existingCorrectiveAction);
                }
                continue;
            }

            ServiceEvent correctiveAction = createPreviewServiceEvent(draft);
            correctiveAction.setLocation(location);
            correctiveActionsToSave.add(correctiveAction);
            effectiveCorrectiveActions.add(correctiveAction);
            for (String identity : correctiveActionIdentities) {
                persistedByIdentity.putIfAbsent(identity, correctiveAction);
            }
        }

        if (!correctiveActionsToSave.isEmpty()) {
            serviceEventRepository.saveAllAndFlush(correctiveActionsToSave);
        }
        return List.copyOf(effectiveCorrectiveActions);
    }

    void completeResolvedPersistedCorrectiveActions(
        Location location,
        List<LocationDashboardImportStrategy.CorrectiveActionDraft> correctiveActions
    ) {
        if (location == null || location.getId() == null || correctiveActions == null || correctiveActions.isEmpty()) {
            return;
        }

        Map<String, LocationDashboardImportStrategy.CorrectiveActionDraft> resolvedDraftsByIdentity = new LinkedHashMap<>();
        for (LocationDashboardImportStrategy.CorrectiveActionDraft draft : correctiveActions) {
            if (draft != null && draft.resolved()) {
                for (String identity : correctiveActionIdentities(draft.title(), draft.description())) {
                    resolvedDraftsByIdentity.putIfAbsent(identity, draft);
                }
            }
        }
        if (resolvedDraftsByIdentity.isEmpty()) {
            return;
        }

        List<ServiceEvent> eventsToSave = new ArrayList<>();
        for (ServiceEvent persistedCorrectiveAction :
            serviceEventRepository.findByLocation_IdAndCorrectiveActionTrueOrderByEventDateAscEventTimeAscIdAsc(location.getId())) {
            if (persistedCorrectiveAction == null) {
                continue;
            }
            String identity = firstMatchingIdentity(correctiveActionIdentities(
                persistedCorrectiveAction.getTitle(),
                persistedCorrectiveAction.getDescription()
            ), resolvedDraftsByIdentity);
            LocationDashboardImportStrategy.CorrectiveActionDraft draft = resolvedDraftsByIdentity.get(identity);
            if (needsCompletionUpdate(persistedCorrectiveAction, draft)) {
                completeServiceEvent(persistedCorrectiveAction, draft);
                eventsToSave.add(persistedCorrectiveAction);
            }
        }
        if (!eventsToSave.isEmpty()) {
            serviceEventRepository.saveAllAndFlush(eventsToSave);
        }
    }

    LocationDashboardDerivedGraphSupport.HistoricalCorrectiveAction toHistoricalCorrectiveAction(ServiceEvent serviceEvent) {
        if (serviceEvent == null) {
            return null;
        }
        Map<String, String> descriptionFields = LocationDashboardCorrectiveActionMetadataSupport.parseStructuredMetadata(
            serviceEvent.getDescription()
        );
        LocalDate observedDate = LocationDashboardGraphMetadataSupport.parseLocalDate(descriptionFields.get("observed at"));
        if (observedDate == null) {
            observedDate = serviceEvent.getEventDate();
        }
        String facilityName = LocationDashboardGraphMetadataSupport.firstNonBlank(
            descriptionFields.get("sublocation"),
            descriptionFields.get("facility")
        );
        String buildingName = descriptionFields.get("building");
        String systemName = descriptionFields.get("system");
        String measurementName = descriptionFields.get("measurement");
        String pointOfUse = descriptionFields.get("point of use");
        String basis = descriptionFields.get("basis");
        String sampleIdentity = descriptionFields.get("sample identity");
        Map<String, String> identityValues = LocationDashboardCorrectiveActionMetadataSupport.identityValues(
            descriptionFields
        );
        if (identityValues.isEmpty()) {
            Map<String, String> legacyIdentityValues = new LinkedHashMap<>();
            putIdentityValue(legacyIdentityValues, "facility", facilityName);
            putIdentityValue(legacyIdentityValues, "building", buildingName);
            putIdentityValue(legacyIdentityValues, "system", systemName);
            putIdentityValue(legacyIdentityValues, "pointOfUse", pointOfUse);
            putIdentityValue(legacyIdentityValues, "basis", basis);
            identityValues = LocationDashboardIdentitySupport.immutableCopy(legacyIdentityValues);
        }

        LocationDashboardImportStrategy strategy = strategyRegistry == null || serviceEvent.getLocation() == null
            ? null
            : strategyRegistry.resolve(serviceEvent.getLocation().getName()).orElse(null);
        if (strategy instanceof ConfiguredLocationDashboardImportStrategy configuredStrategy) {
            facilityName = configuredStrategy.canonicalFacilityName(facilityName, buildingName);
        }

        if (facilityName == null || facilityName.isBlank()
            || measurementName == null || measurementName.isBlank()
            || (identityValues.isEmpty() && (sampleIdentity == null || sampleIdentity.isBlank()))) {
            return null;
        }
        return new LocationDashboardDerivedGraphSupport.HistoricalCorrectiveAction(
            observedDate,
            facilityName,
            measurementName,
            identityValues,
            sampleIdentity,
            serviceEvent
        );
    }

    private void putIdentityValue(Map<String, String> values, String key, String value) {
        if (value != null && !value.isBlank()) {
            values.put(key, value.strip());
        }
    }

    private String correctiveActionIdentity(String title, String description) {
        return LocationDashboardCorrectiveActionMetadataSupport.identityKey(title, description);
    }

    private Set<String> correctiveActionIdentities(String title, String description) {
        Set<String> identities = new LinkedHashSet<>();
        String primaryIdentity = correctiveActionIdentity(title, description);
        if (primaryIdentity != null) {
            identities.add(primaryIdentity);
        }
        String legacyIdentity = LocationDashboardCorrectiveActionMetadataSupport.identityKeyIgnoringSampleIdentity(
            title,
            description
        );
        if (legacyIdentity != null) {
            identities.add(legacyIdentity);
        }
        return identities;
    }

    private <T> String firstMatchingIdentity(Set<String> candidates, Map<String, T> valuesByIdentity) {
        if (candidates == null || valuesByIdentity == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (valuesByIdentity.containsKey(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private ServiceEventStatus resolveImportedCorrectiveActionStatus(LocalDate observedDate) {
        LocalDate today = LocalDate.now(clock);
        if (observedDate.isBefore(today)) {
            return ServiceEventStatus.OVERDUE;
        }
        if (observedDate.isEqual(today)) {
            return ServiceEventStatus.CURRENT;
        }
        return ServiceEventStatus.UPCOMING;
    }

    private ServiceEvent createPreviewServiceEvent(LocationDashboardImportStrategy.CorrectiveActionDraft draft) {
        ServiceEvent serviceEvent = new ServiceEvent();
        serviceEvent.setCorrectiveAction(true);
        serviceEvent.setTitle(draft.title());
        serviceEvent.setDescription(draft.description());
        serviceEvent.setResponsibility(ServiceEventResponsibility.PARTNER);
        serviceEvent.setEventDate(draft.observedDate());
        serviceEvent.setEventTime(ALL_DAY_START_TIME);
        serviceEvent.setEndEventDate(draft.resolved() && draft.resolvedDate() != null
            ? draft.resolvedDate()
            : draft.observedDate());
        serviceEvent.setEndEventTime(ALL_DAY_END_TIME);
        serviceEvent.setStatus(draft.resolved()
            ? ServiceEventStatus.COMPLETED
            : resolveImportedCorrectiveActionStatus(draft.observedDate()));
        return serviceEvent;
    }

    private void completeServiceEvent(
        ServiceEvent serviceEvent,
        LocationDashboardImportStrategy.CorrectiveActionDraft draft
    ) {
        if (serviceEvent == null || draft == null) {
            return;
        }
        serviceEvent.setStatus(ServiceEventStatus.COMPLETED);
        if (draft.resolvedDate() != null) {
            serviceEvent.setEndEventDate(draft.resolvedDate());
            serviceEvent.setEndEventTime(ALL_DAY_END_TIME);
        }
    }

    private boolean needsCompletionUpdate(
        ServiceEvent serviceEvent,
        LocationDashboardImportStrategy.CorrectiveActionDraft draft
    ) {
        if (serviceEvent == null || draft == null || !draft.resolved()) {
            return false;
        }
        if (serviceEvent.getStatus() != ServiceEventStatus.COMPLETED) {
            return true;
        }
        return draft.resolvedDate() != null && !Objects.equals(serviceEvent.getEndEventDate(), draft.resolvedDate());
    }

    private ServiceEvent copyServiceEvent(ServiceEvent source) {
        if (source == null) {
            return null;
        }
        ServiceEvent copy = new ServiceEvent();
        copy.setId(source.getId());
        copy.setLocation(source.getLocation());
        copy.setTitle(source.getTitle());
        copy.setDescription(source.getDescription());
        copy.setCorrectiveAction(source.isCorrectiveAction());
        copy.setResponsibility(Objects.requireNonNullElse(source.getResponsibility(), ServiceEventResponsibility.PARTNER));
        copy.setEventDate(source.getEventDate());
        copy.setEventTime(source.getEventTime());
        copy.setEndEventDate(source.getEndEventDate());
        copy.setEndEventTime(source.getEndEventTime());
        copy.setStatus(source.getStatus());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }
}
