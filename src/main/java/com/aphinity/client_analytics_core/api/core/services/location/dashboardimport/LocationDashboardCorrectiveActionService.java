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
import java.util.List;
import java.util.Map;

/**
 * Owns corrective-action persistence rules and historical corrective-action reconstruction.
 */
final class LocationDashboardCorrectiveActionService {
    private static final LocalTime ALL_DAY_START_TIME = LocalTime.MIDNIGHT;
    private static final LocalTime ALL_DAY_END_TIME = LocalTime.of(23, 59, 59);

    private final ServiceEventRepository serviceEventRepository;
    private final Clock clock;

    LocationDashboardCorrectiveActionService(ServiceEventRepository serviceEventRepository, Clock clock) {
        this.serviceEventRepository = serviceEventRepository;
        this.clock = clock;
    }

    void upsertCorrectiveActions(
        Location location,
        List<LocationDashboardImportStrategy.CorrectiveActionDraft> correctiveActions
    ) {
        if (correctiveActions.isEmpty()) {
            return;
        }

        List<String> titles = correctiveActions.stream()
            .map(LocationDashboardImportStrategy.CorrectiveActionDraft::title)
            .distinct()
            .toList();
        Map<String, ServiceEvent> existingByTitle = new LinkedHashMap<>();
        for (ServiceEvent existingCorrectiveAction : serviceEventRepository.findByLocation_IdAndCorrectiveActionTrueAndTitleIn(
            location.getId(),
            titles
        )) {
            existingByTitle.putIfAbsent(
                LocationDashboardGraphMetadataSupport.normalizeKey(existingCorrectiveAction.getTitle()),
                existingCorrectiveAction
            );
        }

        List<ServiceEvent> eventsToPersist = new ArrayList<>();
        for (LocationDashboardImportStrategy.CorrectiveActionDraft draft : correctiveActions) {
            ServiceEvent serviceEvent = existingByTitle.get(
                LocationDashboardGraphMetadataSupport.normalizeKey(draft.title())
            );
            boolean newEvent = serviceEvent == null;
            if (serviceEvent == null) {
                serviceEvent = new ServiceEvent();
                serviceEvent.setLocation(location);
                serviceEvent.setCorrectiveAction(true);
            }
            serviceEvent.setTitle(draft.title());
            serviceEvent.setResponsibility(ServiceEventResponsibility.PARTNER);
            if (newEvent) {
                serviceEvent.setEventDate(draft.observedDate());
                serviceEvent.setEventTime(ALL_DAY_START_TIME);
                serviceEvent.setEndEventDate(draft.observedDate());
                serviceEvent.setEndEventTime(ALL_DAY_END_TIME);
                serviceEvent.setStatus(resolveImportedCorrectiveActionStatus(draft.observedDate()));
            }
            serviceEvent.setDescription(draft.description());
            eventsToPersist.add(serviceEvent);
        }

        serviceEventRepository.saveAllAndFlush(eventsToPersist);
    }

    List<ServiceEvent> findPersistedCorrectiveActions(Long locationId) {
        return serviceEventRepository.findByLocation_IdAndCorrectiveActionTrueOrderByEventDateAscEventTimeAscIdAsc(locationId);
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
        return new LocationDashboardDerivedGraphSupport.HistoricalCorrectiveAction(
            observedDate,
            LocationDashboardGraphMetadataSupport.firstNonBlank(
                descriptionFields.get("sublocation"),
                descriptionFields.get("facility")
            ),
            descriptionFields.get("system"),
            descriptionFields.get("measurement"),
            serviceEvent
        );
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
}
