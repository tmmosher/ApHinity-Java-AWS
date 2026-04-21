package com.aphinity.client_analytics_core.api.core.services.servicecalendar;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventResponsibility;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.servicecalendar.ServiceEventRepository;
import com.aphinity.client_analytics_core.api.core.requests.servicecalendar.LocationEventRequest;
import com.aphinity.client_analytics_core.api.core.response.servicecalendar.ServiceEventResponse;
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
import java.util.List;

@Service
public class LocationEventService {
    private static final Logger log = LoggerFactory.getLogger(LocationEventService.class);

    private final LocationRepository locationRepository;
    private final ServiceEventRepository serviceEventRepository;
    private final ServiceCalendarAuthorizationService authorizationService;
    private final ServiceEventRequestMapper requestMapper;
    private final ServiceCalendarTemplateService templateService;
    private final ServiceCalendarImportService importService;
    private final ServiceEventAuditService auditService;
    private final ServiceCorrectiveActionEmailService correctiveActionEmailService;

    public LocationEventService(
        LocationRepository locationRepository,
        ServiceEventRepository serviceEventRepository,
        ServiceCalendarAuthorizationService authorizationService,
        ServiceEventRequestMapper requestMapper,
        ServiceCalendarTemplateService templateService,
        ServiceCalendarImportService importService,
        ServiceEventAuditService auditService,
        ServiceCorrectiveActionEmailService correctiveActionEmailService
    ) {
        this.locationRepository = locationRepository;
        this.serviceEventRepository = serviceEventRepository;
        this.authorizationService = authorizationService;
        this.requestMapper = requestMapper;
        this.templateService = templateService;
        this.importService = importService;
        this.auditService = auditService;
        this.correctiveActionEmailService = correctiveActionEmailService;
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
        return importService.uploadServiceCalendar(userId, locationId, file);
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
            auditService.recordCreated(userId, persisted);
            ServiceEventResponse response = requestMapper.toResponse(persisted);
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

        ServiceEvent correctiveAction = requestMapper.createServiceEvent(sourceEvent.getLocation(), request);
        correctiveAction.setCorrectiveAction(true);
        correctiveAction.setCorrectiveActionSourceEvent(sourceEvent);

        try {
            ServiceEvent persisted = serviceEventRepository.saveAndFlush(correctiveAction);
            auditService.recordCreated(userId, persisted);

            if (authorizationService.isPartnerOrAdmin(user)) {
                correctiveActionEmailService.sendCorrectiveActionWorkOrderEmail(
                    locationId,
                    sourceEvent,
                    persisted,
                    user
                );
            }

            ServiceEventResponse response = requestMapper.toResponse(persisted);
            locationRepository.touchUpdatedAt(locationId, Instant.now());
            return response;
        } catch (RuntimeException ex) {
            log.error(
                "Corrective action creation persistence failed actorUserId={} locationId={} sourceEventId={}",
                userId,
                locationId,
                sourceEventId,
                ex
            );
            throw ex;
        }
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
            auditService.recordUpdated(userId, persisted);
            // touchUpdatedAt() clears the persistence context, so map the response first while
            // any lazy corrective-action source proxy is still attached.
            ServiceEventResponse response = requestMapper.toResponse(persisted);
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

    @Transactional
    public void deleteLocationEvent(Long userId, Long locationId, Long eventId, String actorIpAddress) {
        AppUser user = authorizationService.requireUser(userId);
        authorizationService.requireDeletePermission(user);

        ServiceEvent serviceEvent = serviceEventRepository.findByIdAndLocation_Id(eventId, locationId).orElse(null);
        if (serviceEvent != null) {
            try {
                auditService.recordDeleted(userId, actorIpAddress, serviceEvent);
                serviceEventRepository.delete(serviceEvent);
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
}
