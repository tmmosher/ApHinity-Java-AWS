package com.aphinity.client_analytics_core.api.core.services.servicecalendar;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventResponsibility;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.servicecalendar.ServiceEventRepository;
import com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardTimeRangeService;
import com.aphinity.client_analytics_core.api.notifications.MailOutboxCommandService;
import com.aphinity.client_analytics_core.api.core.requests.servicecalendar.LocationEventRequest;
import com.aphinity.client_analytics_core.api.core.requests.servicecalendar.ServiceCalendarBulkEventRowRequest;
import com.aphinity.client_analytics_core.api.core.response.servicecalendar.ServiceEventResponse;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.persistence.EntityManager;
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

@Service
public class LocationEventService {
    private static final Logger log = LoggerFactory.getLogger(LocationEventService.class);

    @Autowired(required = false)
    private EntityManager entityManager;

    private final LocationRepository locationRepository;
    private final ServiceEventRepository serviceEventRepository;
    private final ServiceCalendarAuthorizationService authorizationService;
    private final ServiceEventRequestMapper requestMapper;
    private final ServiceCalendarTemplateService templateService;
    private final ServiceCalendarImportService importService;
    private final ServiceEventAuditService auditService;
    private final MailOutboxCommandService mailOutboxCommandService;
    private final LocationDashboardTimeRangeService locationDashboardTimeRangeService;

    public LocationEventService(
        LocationRepository locationRepository,
        ServiceEventRepository serviceEventRepository,
        ServiceCalendarAuthorizationService authorizationService,
        ServiceEventRequestMapper requestMapper,
        ServiceCalendarTemplateService templateService,
        ServiceCalendarImportService importService,
        ServiceEventAuditService auditService,
        MailOutboxCommandService mailOutboxCommandService,
        LocationDashboardTimeRangeService locationDashboardTimeRangeService
    ) {
        this.locationRepository = locationRepository;
        this.serviceEventRepository = serviceEventRepository;
        this.authorizationService = authorizationService;
        this.requestMapper = requestMapper;
        this.templateService = templateService;
        this.importService = importService;
        this.auditService = auditService;
        this.mailOutboxCommandService = mailOutboxCommandService;
        this.locationDashboardTimeRangeService = locationDashboardTimeRangeService;
    }

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

    @Transactional(readOnly = true)
    public Resource getServiceCalendarTemplate(Long userId, Long locationId) {
        AppUser user = authorizationService.requireUser(userId);
        authorizationService.requireReadableLocationAccess(user, locationId);
        return templateService.getTemplate();
    }

    @Transactional
    public int uploadServiceCalendar(Long userId, Long locationId, MultipartFile file) {
        int importedCount = importService.uploadServiceCalendar(userId, locationId, file);
        locationDashboardTimeRangeService.refreshLocationDateGroups(locationId);
        return importedCount;
    }

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
            locationDashboardTimeRangeService.refreshLocationDateGroups(locationId);
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
            locationDashboardTimeRangeService.refreshLocationDateGroups(locationId);
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
            locationDashboardTimeRangeService.refreshLocationDateGroups(locationId);
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
            locationDashboardTimeRangeService.refreshLocationDateGroups(locationId);
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
        if (entityManager != null) {
            if (entityManager.contains(fallbackEvent)) {
                entityManager.refresh(fallbackEvent);
                return fallbackEvent;
            }

            ServiceEvent refreshedEvent = entityManager.find(ServiceEvent.class, eventId);
            if (refreshedEvent != null) {
                return refreshedEvent;
            }
        }
        return serviceEventRepository.findById(eventId).orElse(fallbackEvent);
    }

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
                locationDashboardTimeRangeService.refreshLocationDateGroups(locationId);
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
