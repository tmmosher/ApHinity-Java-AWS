package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventResponsibility;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventStatus;
import com.aphinity.client_analytics_core.api.core.repositories.servicecalendar.ServiceEventRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Owns corrective-action preview rules and historical corrective-action reconstruction.
 */
final class LocationDashboardCorrectiveActionService {
    private static final LocalTime ALL_DAY_START_TIME = LocalTime.MIDNIGHT;
    private static final LocalTime ALL_DAY_END_TIME = LocalTime.of(23, 59, 59);

    private final ServiceEventRepository serviceEventRepository;
    private final Clock clock;
    private final LocationDashboardImportStrategyRegistry strategyRegistry;

    LocationDashboardCorrectiveActionService(ServiceEventRepository serviceEventRepository, Clock clock) {
        this(serviceEventRepository, clock, null);
    }

    LocationDashboardCorrectiveActionService(
        ServiceEventRepository serviceEventRepository,
        Clock clock,
        LocationDashboardImportStrategyRegistry strategyRegistry
    ) {
        this.serviceEventRepository = serviceEventRepository;
        this.clock = clock;
        this.strategyRegistry = strategyRegistry;
    }

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
            persistedIndexesByTitle.putIfAbsent(
                correctiveActionIdentity(existingCorrectiveAction.getTitle(), existingCorrectiveAction.getDescription()),
                index
            );
        }

        for (LocationDashboardImportStrategy.CorrectiveActionDraft draft : correctiveActions) {
            String correctiveActionIdentity = correctiveActionIdentity(draft.title(), draft.description());
            Integer existingIndex = persistedIndexesByTitle.get(correctiveActionIdentity);
            if (existingIndex != null) {
                ServiceEvent previewEvent = previewCorrectiveActions.get(existingIndex);
                previewEvent.setTitle(draft.title());
                previewEvent.setDescription(draft.description());
                continue;
            }
            previewCorrectiveActions.add(createPreviewServiceEvent(draft));
        }

        return List.copyOf(previewCorrectiveActions);
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
        String systemTypeName = descriptionFields.get("system");
        String measurementName = descriptionFields.get("measurement");

        LocationDashboardImportStrategy strategy = strategyRegistry == null || serviceEvent.getLocation() == null
            ? null
            : strategyRegistry.resolve(serviceEvent.getLocation().getName()).orElse(null);
        if (strategy instanceof ConfiguredLocationDashboardImportStrategy configuredStrategy) {
            facilityName = configuredStrategy.canonicalFacilityName(facilityName, buildingName);
            systemTypeName = configuredStrategy.canonicalSystemTypeName(systemTypeName);
        }

        if (facilityName == null || facilityName.isBlank()
            || systemTypeName == null || systemTypeName.isBlank()
            || measurementName == null || measurementName.isBlank()) {
            return null;
        }
        return new LocationDashboardDerivedGraphSupport.HistoricalCorrectiveAction(
            observedDate,
            facilityName,
            systemTypeName,
            measurementName,
            serviceEvent
        );
    }

    private String correctiveActionIdentity(String title, String description) {
        return LocationDashboardCorrectiveActionMetadataSupport.identityKey(title, description);
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
        serviceEvent.setEndEventDate(draft.observedDate());
        serviceEvent.setEndEventTime(ALL_DAY_END_TIME);
        serviceEvent.setStatus(resolveImportedCorrectiveActionStatus(draft.observedDate()));
        return serviceEvent;
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
