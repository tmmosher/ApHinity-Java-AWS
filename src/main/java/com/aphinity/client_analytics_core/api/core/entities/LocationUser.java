package com.aphinity.client_analytics_core.api.core.entities;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "location_user")
public class LocationUser {
    @EmbeddedId
    private LocationUserId id = new LocationUserId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("locationId")
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Convert(converter = LocationMemberRoleConverter.class)
    @Column(name = "user_role", nullable = false)
    private LocationMemberRole userRole;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (userRole == null) {
            userRole = LocationMemberRole.CLIENT;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public LocationUserId getId() {
        return id;
    }

    public void setId(LocationUserId id) {
        this.id = id;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public LocationMemberRole getUserRole() {
        return userRole;
    }

    public void setUserRole(LocationMemberRole userRole) {
        this.userRole = userRole;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
