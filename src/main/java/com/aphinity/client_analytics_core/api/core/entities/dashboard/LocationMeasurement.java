package com.aphinity.client_analytics_core.api.core.entities.dashboard;

import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "location_measurements")
public class LocationMeasurement {
    @EmbeddedId
    private LocationMeasurementId id;

    @MapsId("locationId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    public LocationMeasurementId getId() {
        return id;
    }

    public void setId(LocationMeasurementId id) {
        this.id = id;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

}