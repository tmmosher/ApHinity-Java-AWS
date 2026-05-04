package com.aphinity.client_analytics_core.api.core.entities.dashboard;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class LocationMeasurementId implements Serializable {
    private static final long serialVersionUID = 7642186925424379636L;
    @NotNull
    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @NotNull
    @Column(name = "measurement_id", nullable = false)
    private Long measurementId;

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public Long getMeasurementId() {
        return measurementId;
    }

    public void setMeasurementId(Long measurementId) {
        this.measurementId = measurementId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocationMeasurementId entity = (LocationMeasurementId) o;
        return Objects.equals(this.locationId, entity.locationId) &&
                Objects.equals(this.measurementId, entity.measurementId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locationId, measurementId);
    }
}