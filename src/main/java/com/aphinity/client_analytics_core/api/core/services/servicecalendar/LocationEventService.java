package com.aphinity.client_analytics_core.api.core.services.servicecalendar;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventResponsibility;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.servicecalendar.ServiceEventRepository;
import com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.DerivedGraphRefresher;
import com.aphinity.client_analytics_core.api.notifications.MailOutboxCommandService;
import com.aphinity.client_analytics_core.api.core.requests.servicecalendar.LocationEventRequest;
import com.aphinity.client_analytics_core.api.core.requests.servicecalendar.ServiceCalendarBulkEventRowRequest;
import com.aphinity.client_analytics_core.api.core.response.servicecalendar.ServiceEventResponse;
import org.springframework.beans.factory.annotation.Autowired;
import com.aphinity.client_analytics_core.api.core.services.PersistenceEntityReloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Coordinates service calendar reads, imports, mutations, corrective actions,
 * and audit logging for a location.
 * <p>
 * Authorization is delegated to {@link ServiceCalendarAuthorizationService}, while
 * this service keeps calendar mutations, corrective-action links, location
 * timestamp touches, and audit entries in the same transaction.
 */
@Service
public class LocationEventService {
    private static final Logger log = LoggerFactory.getLogger(LocationEventService.class);

    private PersistenceEntityReloader entityReloader = PersistenceEntityReloader.noop();

    private final LocationRepository locationRepository;
    private final ServiceEventRepository serviceEventRepository;
    private final ServiceCalendarAuthorizationService authorizationService;
    private final ServiceEventRequestMapper requestMapper;
    private final ServiceCalendarTemplateService templateService;
    private final ServiceCalendarImportService importService;
    private final ServiceEventAuditService auditService;
    private final MailOutboxCommandService mailOutboxCommandService;
    private final DerivedGraphRefresher dashboardRefreshService;

    public LocationEventService(
        LocationRepository locationRepository,
        ServiceEventRepository serviceEventRepository,
        ServiceCalendarAuthorizationService authorizationService,
        ServiceEventRequestMapper requestMapper,
        ServiceCalendarTemplateService templateService,
        ServiceCalendarImportService importService,
        ServiceEventAuditService auditService,
        MailOutboxCommandService mailOutboxCommandService,
        DerivedGraphRefresher dashboardRefreshService
    ) {
        this.locationRepository = locationRepository;
        this.serviceEventRepository = serviceEventRepository;
        this.authorizationService = authorizationService;
        this.requestMapper = requestMapper;
        this.templateService = templateService;
        this.importService = importService;
        this.auditService = auditService;
        this.mailOutboxCommandService = mailOutboxCommandService;
        this.dashboardRefreshService = dashboardRefreshService;
    }

    @Autowired(required = false)
    void configureEntityReloader(PersistenceEntityReloader entityReloader) {
        this.entityReloader = entityReloader;
    }

    /**
     * Returns service events visible in the requested calendar month.
     * The query includes events whose date range overlaps the month window rather
     * than only events that start inside the month.
     *
     * @param userId authenticated actor id
     * @param locationId target location id
     * @param viewedMonth month currently rendered by the calendar UI
     * @return ordered event responses for the visible window
     */
    @Transactional(readOnly = true)
    public List<ServiceEventResponse> getAccessibleLocationEvents(Long userId, Long locationId, YearMonth viewedMonth) {
        AppUser user = authorizationService.requireUser(userId);
        authorizationService.requireReadableLocationAccess(user, locationId);

        YearMonth normalizedViewedMonth = requestMapper.requireViewedMonth(viewedMonth);
        LocalDate windowStart = normalizedViewedMonth.minusMonths(1).atDay(1);
        LocalDate windowEnd = normalizedViewedMonth.plusMonths(1).atEndOfMonth();

        return serviceEventRepository.findVisibleByLocationIdAndDateWindow(locationId, windowStart, windowEnd).stream()
            .map(requestMapper::toResponse)
            .toList();
    }

    /**
     * Returns the service calendar spreadsheet template after verifying read access
     * to the location.
     *
     * @param userId authenticated actor id
     * @param locationId target location id
     * @return classpath template resource
     */
    @Transactional(readOnly = true)
    public Resource getServiceCalendarTemplate(Long userId, Long locationId) {
        AppUser user = authorizationService.requireUser(userId);
        authorizationService.requireReadableLocationAccess(user, locationId);
        return templateService.getTemplate();
    }

    /**
     * Imports service events from an uploaded spreadsheet.
     * Parsed rows are mapped through the same request mapper used by the JSON API,
     * so validation and defaults are consistent across upload and direct creation.
     *
     * @param userId authenticated actor id
     * @param locationId target location id
     * @param file uploaded spreadsheet
     * @return number of imported rows
     */
    @Transactional
    public int uploadServiceCalendar(Long userId, Long locationId, MultipartFile file) {
        int importedCount = importService.uploadServiceCalendar(userId, locationId, file);
        dashboardRefreshService.refreshDerivedGraphs(locationId);
        return importedCount;
    }

    /**
     * Creates a batch of service events, typically from frontend-staged imports.
     * Empty batches are rejected because a successful no-op import would be
     * ambiguous to callers.
     *
     * @param userId authenticated actor id
     * @param locationId target location id
     * @param requests event row requests to persist
     * @return number of persisted events
     */
    @Transactional
    public int createLocationEvents(Long userId, Long locationId, List<ServiceCalendarBulkEventRowRequest> requests) {
        AppUser user = authorizationService.requireUser(userId);
        Location location = authorizationService.requireLocation(locationId);
        authorizationService.requireReadableLocationAccess(user, locationId);

        List<ServiceEvent> serviceEvents = new ArrayList<>(requests == null ? 0 : requests.size());
        if (requests != null) {
            for (ServiceCalendarBulkEventRowRequest request : requests) {
                LocationEventRequest eventRequest = request.toLocationEventRequest();
                ServiceEventResponsibility responsibility = requestMapper.requireResponsibility(eventRequest);
                authorizationService.requireCreatePermission(user, locationId, responsibility);
                ServiceEvent serviceEvent = requestMapper.createServiceEvent(location, eventRequest);
                serviceEvent.setCorrectiveAction(Boolean.TRUE.equals(request.correctiveAction()));
                serviceEvents.add(serviceEvent);
            }
        }

        try {
            serviceEventRepository.saveAllAndFlush(serviceEvents);
            for (ServiceEvent serviceEvent : serviceEvents) {
                auditService.recordCreated(userId, serviceEvent);
            }
            dashboardRefreshService.refreshDerivedGraphs(locationId);
            locationRepository.touchUpdatedAt(locationId, Instant.now());
            return serviceEvents.size();
        } catch (RuntimeException ex) {
            log.error(
                "Bulk location event creation persistence failed actorUserId={} locationId={} eventCount={}",
                userId,
                locationId,
                serviceEvents.size(),
                ex
            );
            throw ex;
        }
    }

    /**
     * Creates one service event and records a creation audit entry.
     *
     * @param userId authenticated actor id
     * @param locationId target location id
     * @param request validated event request
     * @return created event response
     */
    @Transactional
    public ServiceEventResponse createLocationEvent(Long userId, Long locationId, LocationEventRequest request) {
        AppUser user = authorizationService.requireUser(userId);
        ServiceEventResponsibility responsibility = requestMapper.requireResponsibility(request);

        Location location = authorizationService.requireLocation(locationId);
        authorizationService.requireCreatePermission(user, locationId, responsibility);
        ServiceEvent serviceEvent = requestMapper.createServiceEvent(location, request);

        try {
            ServiceEvent persisted = serviceEventRepository.saveAndFlush(serviceEvent);
            persisted = refreshServiceEventFromStore(persisted.getId(), persisted);
            auditService.recordCreated(userId, persisted);
            ServiceEventResponse response = requestMapper.toResponse(persisted);
            dashboardRefreshService.refreshDerivedGraphs(locationId);
            locationRepository.touchUpdatedAt(locationId, Instant.now());
            return response;
        } catch (RuntimeException ex) {
            log.error(
                "Location event creation persistence failed actorUserId={} locationId={}",
                userId,
                locationId,
                ex
            );
            throw ex;
        }
    }

    /**
     * Creates a corrective action linked to an existing service event.
     * The source event must exist in the same location; the created event stores a
     * source-event association that can later be cleared if the source is deleted.
     *
     * @param userId authenticated actor id
     * @param locationId location owning both source and corrective action
     * @param sourceEventId persisted event that triggered the corrective action
     * @param request corrective action request payload
     * @return created corrective action response
     */
    @Transactional
    public ServiceEventResponse createCorrectiveActionForLocationEvent(
        Long userId,
        Long locationId,
        Long sourceEventId,
        LocationEventRequest request
    ) {
        AppUser user = authorizationService.requireUser(userId);
        ServiceEventResponsibility responsibility = requestMapper.requireResponsibility(request);

        ServiceEvent sourceEvent = serviceEventRepository.findByIdAndLocation_Id(sourceEventId, locationId)
            .orElseThrow(this::locationEventNotFound);
        authorizationService.requireCreateCorrectiveActionPermission(user, locationId, sourceEvent);
        authorizationService.requireCreatePermission(user, locationId, responsibility);

        Location location = sourceEvent.getLocation();
        String locationName = location.getName() == null ? null : location.getName().strip();
        String workOrderEmail = requireWorkOrderEmail(location);

        ServiceEvent correctiveAction = requestMapper.createServiceEvent(location, request);
        correctiveAction.setCorrectiveAction(true);
        correctiveAction.setCorrectiveActionSourceEvent(sourceEvent);

        try {
            ServiceEvent persisted = serviceEventRepository.saveAndFlush(correctiveAction);
            persisted = refreshServiceEventFromStore(persisted.getId(), persisted);
            auditService.recordCreated(userId, persisted);

            ServiceEventResponse response = requestMapper.toResponse(persisted);
            dashboardRefreshService.refreshDerivedGraphs(locationId);
            locationRepository.touchUpdatedAt(locationId, Instant.now());
            mailOutboxCommandService.queueWorkOrderEmail(
                userId,
                locationName,
                workOrderEmail,
                persisted.getTitle(),
                persisted.getDescription()
            );
            return response;
        } catch (RuntimeException ex) {
            log.error(
                "Corrective action creation failed actorUserId={} locationId={} sourceEventId={}",
                userId,
                locationId,
                sourceEventId,
                ex
            );
            throw ex;
        }
    }

    private String requireWorkOrderEmail(Location location) {
        String workOrderEmail = location.getWorkOrderEmail();
        if (workOrderEmail == null || workOrderEmail.isBlank()) {
            throw locationWorkOrderEmailMissing();
        }
        return workOrderEmail.strip();
    }

    /**
     * Replaces one service event's editable fields and records an update audit
     * entry. The lookup is scoped by location to avoid exposing cross-location
     * event ids.
     *
     * @param userId authenticated actor id
     * @param locationId location owning the event
     * @param eventId event to update
     * @param request replacement event fields
     * @return updated event response
     */
    @Transactional
    public ServiceEventResponse updateLocationEvent(
        Long userId,
        Long locationId,
        Long eventId,
        LocationEventRequest request
    ) {
        AppUser user = authorizationService.requireUser(userId);
        ServiceEventResponsibility responsibility = requestMapper.requireResponsibility(request);

        authorizationService.requireLocationExists(locationId);

        ServiceEvent serviceEvent = serviceEventRepository.findByIdAndLocation_Id(eventId, locationId)
            .orElseThrow(this::locationEventNotFound);
        authorizationService.requireUpdatePermission(user, locationId, serviceEvent, responsibility);
        requestMapper.applyRequest(serviceEvent, request);

        try {
            ServiceEvent persisted = serviceEventRepository.saveAndFlush(serviceEvent);
            persisted = refreshServiceEventFromStore(persisted.getId(), persisted);
            auditService.recordUpdated(userId, persisted);
            // touchUpdatedAt() clears the persistence context, so map the response first while
            // any lazy corrective-action source proxy is still attached.
            ServiceEventResponse response = requestMapper.toResponse(persisted);
            dashboardRefreshService.refreshDerivedGraphs(locationId);
            locationRepository.touchUpdatedAt(locationId, Instant.now());
            return response;
        } catch (RuntimeException ex) {
            log.error(
                "Location event update persistence failed actorUserId={} locationId={} eventId={}",
                userId,
                locationId,
                eventId,
                ex
            );
            throw ex;
        }
    }

    private ServiceEvent refreshServiceEventFromStore(Long eventId, ServiceEvent fallbackEvent) {
        return entityReloader.refreshOrFind(ServiceEvent.class, eventId, fallbackEvent)
            .orElseGet(() -> serviceEventRepository.findById(eventId).orElse(fallbackEvent));
    }

    /**
     * Deletes one service event after clearing any corrective actions that point at
     * it. The deletion audit captures request IP metadata before the row is removed.
     *
     * @param userId authenticated actor id
     * @param locationId location owning the event
     * @param eventId event to delete
     * @param actorIpAddress request IP address for audit metadata
     */
    @Transactional
    public void deleteLocationEvent(Long userId, Long locationId, Long eventId, String actorIpAddress) {
        AppUser user = authorizationService.requireUser(userId);
        authorizationService.requireDeletePermission(user);

        ServiceEvent serviceEvent = serviceEventRepository.findByIdAndLocation_Id(eventId, locationId).orElse(null);
        if (serviceEvent != null) {
            try {
                auditService.recordDeleted(userId, actorIpAddress, serviceEvent);
                // Break corrective-action back-references before deleting the source event.
                serviceEventRepository.clearCorrectiveActionSourceEvent(locationId, serviceEvent.getId());
                serviceEventRepository.delete(serviceEvent);
                serviceEventRepository.flush();
                dashboardRefreshService.refreshDerivedGraphs(locationId);
                locationRepository.touchUpdatedAt(locationId, Instant.now());
            } catch (RuntimeException ex) {
                log.error(
                    "Location event delete persistence failed actorUserId={} locationId={} eventId={}",
                    userId,
                    locationId,
                    eventId,
                    ex
                );
                throw ex;
            }
            return;
        }

        authorizationService.requireLocationExists(locationId);
        throw locationEventNotFound();
    }

    private ResponseStatusException locationEventNotFound() {
        return new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Location event not found");
    }

    private ResponseStatusException locationWorkOrderEmailMissing() {
        return new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Location work-order email is required");
    }
}
