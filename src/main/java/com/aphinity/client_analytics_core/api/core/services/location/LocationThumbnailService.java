package com.aphinity.client_analytics_core.api.core.services.location;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationRepository;
import com.aphinity.client_analytics_core.api.core.response.location.LocationResponse;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Owns location thumbnail conversion, persistence, and authorized retrieval. */
@Service
public class LocationThumbnailService {
    @Autowired(required = false)
    private EntityManager entityManager;

    private final LocationRepository locationRepository;
    private final LocationThumbnailImageService imageService;
    private final LocationAccessPolicy accessPolicy;
    private final LocationResponseMapper responseMapper;

    public LocationThumbnailService(
        LocationRepository locationRepository,
        LocationThumbnailImageService imageService,
        LocationAccessPolicy accessPolicy,
        LocationResponseMapper responseMapper
    ) {
        this.locationRepository = locationRepository;
        this.imageService = imageService;
        this.accessPolicy = accessPolicy;
        this.responseMapper = responseMapper;
    }

    @Transactional
    public LocationResponse updateThumbnail(Long userId, Long locationId, MultipartFile file) {
        AppUser user = accessPolicy.requireUser(userId);
        accessPolicy.requirePartnerOrAdmin(user);
        Location location = locationRepository.findById(locationId).orElseThrow(accessPolicy::locationNotFound);
        location.setThumbnail(imageService.convertToWebp(file));
        Location persisted = locationRepository.saveAndFlush(location);
        if (persisted != null) location = persisted;
        if (entityManager != null && entityManager.contains(location)) {
            entityManager.refresh(location);
        }
        return responseMapper.toResponse(location, user);
    }

    @Transactional(readOnly = true)
    public byte[] getThumbnail(Long userId, Long locationId) {
        AppUser user = accessPolicy.requireUser(userId);
        Location location = locationRepository.findById(locationId).orElseThrow(accessPolicy::locationNotFound);
        if (!accessPolicy.hasLocationAccess(user, locationId)) throw accessPolicy.forbidden();
        byte[] thumbnail = location.getThumbnail();
        if (thumbnail == null || thumbnail.length == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Location thumbnail not found");
        }
        return thumbnail;
    }
}
