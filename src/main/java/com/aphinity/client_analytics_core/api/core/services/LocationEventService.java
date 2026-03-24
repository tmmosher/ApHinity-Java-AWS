package com.aphinity.client_analytics_core.api.core.services;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.core.entities.Location;
import com.aphinity.client_analytics_core.api.core.entities.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.entities.ServiceEventResponsibility;
import com.aphinity.client_analytics_core.api.core.entities.ServiceEventStatus;
import com.aphinity.client_analytics_core.api.core.repositories.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.LocationUserRepository;
import com.aphinity.client_analytics_core.api.core.repositories.ServiceEventRepository;
import com.aphinity.client_analytics_core.api.core.requests.LocationEventRequest;
import com.aphinity.client_analytics_core.api.core.response.ServiceEventResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
    public List<ServiceEventResponse> getAccessibleLocationEvents(Long userId, Long locationId) {
        AppUser user = requireUser(userId);
        if (!locationRepository.existsById(locationId)) {
            throw locationNotFound();
        }
        if (!hasLocationAccess(user, locationId)) {
            throw forbidden();
        }

        return serviceEventRepository.findByLocation_IdOrderByEventDateAscEventTimeAscIdAsc(locationId).stream()
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
        serviceEvent.setTitle(normalizeTitle(request == null ? null : request.title()));
        serviceEvent.setResponsibility(normalizeResponsibility(request == null ? null : request.responsibility()));
        serviceEvent.setEventDate(normalizeDate(request == null ? null : request.date()));
        serviceEvent.setEventTime(normalizeTime(request == null ? null : request.time()));
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

    private ServiceEventStatus normalizeStatus(ServiceEventStatus status) {
        if (status == null) {
            throw invalidEventStatus();
        }
        return status;
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
}
