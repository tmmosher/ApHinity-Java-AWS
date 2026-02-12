package com.aphinity.client_analytics_core.api.core.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class LocationUserId implements Serializable {
    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "user_id")
    private Long userId;

    public LocationUserId() {
    }

    public LocationUserId(Long locationId, Long userId) {
        this.locationId = locationId;
        this.userId = userId;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LocationUserId that)) {
            return false;
        }
        return Objects.equals(locationId, that.locationId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locationId, userId);
    }
}
