package com.aphinity.client_analytics_core.api.core.services.servicecalendar;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventResponsibility;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventStatus;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationUserRepository;
import com.aphinity.client_analytics_core.api.core.repositories.servicecalendar.ServiceEventRepository;
import com.aphinity.client_analytics_core.api.core.requests.servicecalendar.LocationEventRequest;
import com.aphinity.client_analytics_core.api.core.response.servicecalendar.ServiceEventResponse;
import com.aphinity.client_analytics_core.api.core.services.AccountRoleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

@Service
public class LocationEventService {
    private static final Logger log = LoggerFactory.getLogger(LocationEventService.class);

    private final AppUserRepository appUserRepository;
    private final LocationRepository locationRepository;
    private final LocationUserRepository locationUserRepository;
    private final ServiceEventRepository serviceEventRepository;
    private final AccountRoleService accountRoleService;

    public LocationEventService(
        AppUserRepository appUserRepository,
        LocationRepository locationRepository,
        LocationUserRepository locationUserRepository,
        ServiceEventRepository serviceEventRepository,
        AccountRoleService accountRoleService
    ) {
        this.appUserRepository = appUserRepository;
        this.locationRepository = locationRepository;
        this.locationUserRepository = locationUserRepository;
        this.serviceEventRepository = serviceEventRepository;
        this.accountRoleService = accountRoleService;
    }

    @Transactional(readOnly = true)
    public List<ServiceEventResponse> getAccessibleLocationEvents(Long userId, Long locationId, YearMonth viewedMonth) {
        AppUser user = requireUser(userId);
        if (!locationRepository.existsById(locationId)) {
            throw locationNotFound();
        }
        if (!hasLocationAccess(user, locationId)) {
            throw forbidden();
        }

        YearMonth normalizedViewedMonth = requireViewedMonth(viewedMonth);
        LocalDate windowStart = normalizedViewedMonth.minusMonths(1).atDay(1);
        LocalDate windowEnd = normalizedViewedMonth.plusMonths(1).atEndOfMonth();

        return serviceEventRepository.findVisibleByLocationIdAndDateWindow(locationId, windowStart, windowEnd).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public ServiceEventResponse createLocationEvent(Long userId, Long locationId, LocationEventRequest request) {
        AppUser user = requireUser(userId);
        ServiceEventResponsibility responsibility = normalizeResponsibility(request == null ? null : request.responsibility());

        Location location = locationRepository.findById(locationId).orElseThrow(this::locationNotFound);
        requireCreatePermission(user, locationId, responsibility);
        ServiceEvent serviceEvent = new ServiceEvent();
        serviceEvent.setLocation(location);
        applyRequest(serviceEvent, request);

        try {
            ServiceEvent persisted = serviceEventRepository.saveAndFlush(serviceEvent);
            locationRepository.touchUpdatedAt(locationId, Instant.now());
            return toResponse(persisted);
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
    public ServiceEventResponse updateLocationEvent(
        Long userId,
        Long locationId,
        Long eventId,
        LocationEventRequest request
    ) {
        AppUser user = requireUser(userId);
        ServiceEventResponsibility responsibility = normalizeResponsibility(request == null ? null : request.responsibility());

        if (!locationRepository.existsById(locationId)) {
            throw locationNotFound();
        }

        ServiceEvent serviceEvent = serviceEventRepository.findByIdAndLocation_Id(eventId, locationId)
            .orElseThrow(this::locationEventNotFound);
        requireUpdatePermission(user, locationId, serviceEvent, responsibility);
        applyRequest(serviceEvent, request);

        try {
            ServiceEvent persisted = serviceEventRepository.saveAndFlush(serviceEvent);
            locationRepository.touchUpdatedAt(locationId, Instant.now());
            return toResponse(persisted);
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
    public void deleteLocationEvent(Long userId, Long locationId, Long eventId) {
        AppUser user = requireUser(userId);
        requirePartnerOrAdmin(user);

        ServiceEvent serviceEvent = serviceEventRepository.findByIdAndLocation_Id(eventId, locationId).orElse(null);
        if (serviceEvent != null) {
            try {
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

        if (!locationRepository.existsById(locationId)) {
            throw locationNotFound();
        }
        throw locationEventNotFound();
    }

    private void applyRequest(ServiceEvent serviceEvent, LocationEventRequest request) {
        LocalDate eventDate = normalizeDate(request == null ? null : request.date());
        LocalTime eventTime = normalizeTime(request == null ? null : request.time());
        LocalDate endEventDate = normalizeEndDate(eventDate, request == null ? null : request.endDate());
        LocalTime endEventTime = normalizeEndTime(eventTime, request == null ? null : request.endTime());
        validateEventRange(eventDate, eventTime, endEventDate, endEventTime);

        serviceEvent.setTitle(normalizeTitle(request == null ? null : request.title()));
        serviceEvent.setResponsibility(normalizeResponsibility(request == null ? null : request.responsibility()));
        serviceEvent.setEventDate(eventDate);
        serviceEvent.setEventTime(eventTime);
        serviceEvent.setEndEventDate(endEventDate);
        serviceEvent.setEndEventTime(endEventTime);
        serviceEvent.setDescription(normalizeDescription(request == null ? null : request.description()));
        serviceEvent.setStatus(normalizeStatus(request == null ? null : request.status()));
    }

    private ServiceEventResponse toResponse(ServiceEvent serviceEvent) {
        return new ServiceEventResponse(
            serviceEvent.getId(),
            serviceEvent.getTitle(),
            serviceEvent.getResponsibility(),
            serviceEvent.getEventDate(),
            serviceEvent.getEventTime(),
            serviceEvent.getEndEventDate(),
            serviceEvent.getEndEventTime(),
            serviceEvent.getDescription(),
            serviceEvent.getStatus(),
            serviceEvent.getCreatedAt(),
            serviceEvent.getUpdatedAt()
        );
    }

    private String normalizeTitle(String value) {
        if (value == null) {
            throw invalidEventTitle();
        }
        String normalized = value.strip();
        if (normalized.isBlank()) {
            throw invalidEventTitle();
        }
        if (normalized.length() > ServiceEvent.TITLE_MAX_LENGTH) {
            throw invalidEventTitleLength();
        }
        return normalized;
    }

    private String normalizeDescription(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isBlank() ? null : normalized;
    }

    private ServiceEventResponsibility normalizeResponsibility(ServiceEventResponsibility responsibility) {
        if (responsibility == null) {
            throw invalidEventResponsibility();
        }
        return responsibility;
    }

    private LocalDate normalizeDate(LocalDate date) {
        if (date == null) {
            throw invalidEventDate();
        }
        return date;
    }

    private LocalTime normalizeTime(LocalTime time) {
        if (time == null) {
            throw invalidEventTime();
        }
        return time;
    }

    private LocalDate normalizeEndDate(LocalDate eventDate, LocalDate endDate) {
        if (endDate == null) {
            return eventDate;
        }
        return endDate;
    }

    private LocalTime normalizeEndTime(LocalTime eventTime, LocalTime endTime) {
        if (endTime == null) {
            return eventTime;
        }
        return endTime;
    }

    private void validateEventRange(
        LocalDate eventDate,
        LocalTime eventTime,
        LocalDate endEventDate,
        LocalTime endEventTime
    ) {
        LocalDateTime startDateTime = LocalDateTime.of(eventDate, eventTime);
        LocalDateTime endDateTime = LocalDateTime.of(endEventDate, endEventTime);
        if (endDateTime.isBefore(startDateTime)) {
            throw invalidEventRange();
        }
    }

    private ServiceEventStatus normalizeStatus(ServiceEventStatus status) {
        if (status == null) {
            throw invalidEventStatus();
        }
        return status;
    }

    private YearMonth requireViewedMonth(YearMonth viewedMonth) {
        if (viewedMonth == null) {
            throw invalidViewedMonth();
        }
        return viewedMonth;
    }

    private AppUser requireUser(Long userId) {
        AppUser user = appUserRepository.findById(userId).orElseThrow(this::invalidAuthenticatedUser);
        requireVerified(user);
        return user;
    }

    private void requireVerified(AppUser user) {
        if (user.getEmailVerifiedAt() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account email is not verified");
        }
    }

    private void requirePartnerOrAdmin(AppUser user) {
        if (!accountRoleService.isPartnerOrAdmin(user)) {
            throw forbidden();
        }
    }

    private void requireCreatePermission(
        AppUser user,
        Long locationId,
        ServiceEventResponsibility responsibility
    ) {
        if (accountRoleService.isPartnerOrAdmin(user)) {
            return;
        }
        if (responsibility == ServiceEventResponsibility.CLIENT && hasLocationAccess(user, locationId)) {
            return;
        }
        throw forbidden();
    }

    private void requireUpdatePermission(
        AppUser user,
        Long locationId,
        ServiceEvent serviceEvent,
        ServiceEventResponsibility responsibility
    ) {
        if (accountRoleService.isPartnerOrAdmin(user)) {
            return;
        }
        if (serviceEvent.getResponsibility() == ServiceEventResponsibility.CLIENT
            && responsibility == ServiceEventResponsibility.CLIENT
            && hasLocationAccess(user, locationId)
        ) {
            return;
        }
        throw forbidden();
    }

    private boolean hasLocationAccess(AppUser user, Long locationId) {
        if (accountRoleService.isPartnerOrAdmin(user)) {
            return true;
        }
        return locationUserRepository.existsByIdLocationIdAndIdUserId(locationId, user.getId());
    }

    private ResponseStatusException invalidAuthenticatedUser() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user");
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permissions");
    }

    private ResponseStatusException locationNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Location not found");
    }

    private ResponseStatusException locationEventNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Location event not found");
    }

    private ResponseStatusException invalidEventTitle() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event title is required");
    }

    private ResponseStatusException invalidEventTitleLength() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event title must be 42 characters or fewer");
    }

    private ResponseStatusException invalidEventResponsibility() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event responsibility is required");
    }

    private ResponseStatusException invalidEventDate() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event date is required");
    }

    private ResponseStatusException invalidEventTime() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event time is required");
    }

    private ResponseStatusException invalidEventStatus() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event status is required");
    }

    private ResponseStatusException invalidViewedMonth() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Viewed month is required");
    }

    private ResponseStatusException invalidEventRange() {
        return new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Event end must be on or after the start date and time"
        );
    }
}
