package com.aphinity.client_analytics_core.api.core.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class LocationGraphId implements Serializable {
    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "graph_id")
    private Long graphId;

    public LocationGraphId() {
    }

    public LocationGraphId(Long locationId, Long graphId) {
        this.locationId = locationId;
        this.graphId = graphId;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public Long getGraphId() {
        return graphId;
    }

    public void setGraphId(Long graphId) {
        this.graphId = graphId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LocationGraphId that)) {
            return false;
        }
        return Objects.equals(locationId, that.locationId) && Objects.equals(graphId, that.graphId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locationId, graphId);
    }
}
