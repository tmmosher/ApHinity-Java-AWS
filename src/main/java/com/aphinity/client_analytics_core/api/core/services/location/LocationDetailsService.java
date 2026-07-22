package com.aphinity.client_analytics_core.api.core.services.location;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.entities.location.LocationUser;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationUserRepository;
import com.aphinity.client_analytics_core.api.core.response.location.LocationResponse;
import com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.DashboardProjectionInvalidator;
import com.aphinity.client_analytics_core.api.core.services.PersistenceEntityReloader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Owns location catalogue, identity, and operational settings. */
@Service
public class LocationDetailsService {
    private PersistenceEntityReloader entityReloader = PersistenceEntityReloader.noop();

    private final LocationRepository locationRepository;
    private final LocationUserRepository locationUserRepository;
    private final LocationAccessPolicy accessPolicy;
    private final LocationResponseMapper responseMapper;
    private final DashboardProjectionInvalidator cacheInvalidationService;

    public LocationDetailsService(
        LocationRepository locationRepository,
        LocationUserRepository locationUserRepository,
        LocationAccessPolicy accessPolicy,
        LocationResponseMapper responseMapper,
        DashboardProjectionInvalidator cacheInvalidationService
    ) {
        this.locationRepository = locationRepository;
        this.locationUserRepository = locationUserRepository;
        this.accessPolicy = accessPolicy;
        this.responseMapper = responseMapper;
        this.cacheInvalidationService = cacheInvalidationService;
    }

    @Autowired(required = false)
    void configureEntityReloader(PersistenceEntityReloader entityReloader) {
        this.entityReloader = entityReloader;
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> getAccessibleLocations(Long userId) {
        AppUser user = accessPolicy.requireUser(userId);
        if (accessPolicy.isPartnerOrAdmin(user)) {
            return locationRepository.findAllByOrderByNameAsc().stream().map(responseMapper::toResponse).toList();
        }
        Map<Long, LocationResponse> unique = new LinkedHashMap<>();
        for (LocationUser membership : locationUserRepository.findByUserIdWithLocation(userId)) {
            Location location = membership.getLocation();
            if (location != null && location.getId() != null) {
                unique.putIfAbsent(location.getId(), responseMapper.toResponse(location));
            }
        }
        return List.copyOf(unique.values());
    }

    @Transactional(readOnly = true)
    public LocationResponse getAccessibleLocation(Long userId, Long locationId) {
        AppUser user = accessPolicy.requireUser(userId);
        Location location = locationRepository.findById(locationId).orElseThrow(accessPolicy::locationNotFound);
        if (!accessPolicy.hasLocationAccess(user, locationId)) {
            throw accessPolicy.forbidden();
        }
        return responseMapper.toResponse(location, user);
    }

    @Transactional
    public LocationResponse createLocation(Long userId, String name) {
        AppUser user = accessPolicy.requireUser(userId);
        accessPolicy.requireAdmin(user);
        Location location = new Location();
        location.setName(normalizeName(name));
        try {
            Location persisted = locationRepository.saveAndFlush(location);
            if (persisted != null) location = persisted;
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Location name already in use");
        }
        refresh(location);
        return responseMapper.toResponse(location, user);
    }

    @Transactional
    public LocationResponse updateLocationName(Long userId, Long locationId, String name) {
        AppUser user = accessPolicy.requireUser(userId);
        accessPolicy.requirePartnerOrAdmin(user);
        Location location = locationRepository.findById(locationId).orElseThrow(accessPolicy::locationNotFound);
        location.setName(normalizeName(name));
        try {
            Location persisted = locationRepository.saveAndFlush(location);
            if (persisted != null) location = persisted;
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Location name already in use");
        }
        refresh(location);
        cacheInvalidationService.invalidate(locationId);
        return responseMapper.toResponse(location, user);
    }

    @Transactional
    public LocationResponse updateWorkOrderEmail(Long userId, Long locationId, String value) {
        AppUser user = accessPolicy.requireUser(userId);
        accessPolicy.requirePartnerOrAdmin(user);
        Location location = locationRepository.findById(locationId).orElseThrow(accessPolicy::locationNotFound);
        location.setWorkOrderEmail(normalizeEmail(value));
        Location persisted = locationRepository.saveAndFlush(location);
        if (persisted != null) location = persisted;
        refresh(location);
        return responseMapper.toResponse(location, user);
    }

    private void refresh(Location location) {
        entityReloader.refreshIfManaged(location);
    }

    private String normalizeName(String value) {
        if (value == null || value.strip().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Location name is required");
        }
        return value.strip();
    }

    private String normalizeEmail(String value) {
        if (value == null) return null;
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }
}
