package com.aphinity.client_analytics_core.api.core.entities.dashboard;

import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
    name = "location_dashboard_sample",
    indexes = {
        @Index(name = "idx_dashboard_sample_location_date", columnList = "location_id, observed_date"),
        @Index(name = "idx_dashboard_sample_location_measurement", columnList = "location_id, measurement_name"),
        @Index(name = "idx_dashboard_sample_location_system_type", columnList = "location_id, system_type_name"),
        @Index(name = "idx_dashboard_sample_location_status", columnList = "location_id, compliant, resolved")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_dashboard_sample_location_identity", columnNames = {"location_id", "sample_identity"})
    }
)
public class LocationDashboardSample {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "observed_date", nullable = false)
    private LocalDate observedDate;

    @Column(name = "system_type_name", length = 256)
    private String systemTypeName;

    @Column(name = "measurement_name", nullable = false, length = 256)
    private String measurementName;

    @Column(name = "raw_value", columnDefinition = "text")
    private String rawValue;

    @Column(name = "sample_identity", nullable = false, length = 1024)
    private String sampleIdentity;

    @Column(name = "compliant", nullable = false)
    private boolean compliant;

    @Column(name = "resolved", nullable = false)
    private boolean resolved;

    @Column(name = "turnaround_days")
    private Long turnaroundDays;

    @Column(name = "origin", nullable = false, length = 64)
    private String origin;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public LocalDate getObservedDate() {
        return observedDate;
    }

    public void setObservedDate(LocalDate observedDate) {
        this.observedDate = observedDate;
    }

    public String getSystemTypeName() {
        return systemTypeName;
    }

    public void setSystemTypeName(String systemTypeName) {
        this.systemTypeName = systemTypeName;
    }

    public String getMeasurementName() {
        return measurementName;
    }

    public void setMeasurementName(String measurementName) {
        this.measurementName = measurementName;
    }

    public String getRawValue() {
        return rawValue;
    }

    public void setRawValue(String rawValue) {
        this.rawValue = rawValue;
    }

    public String getSampleIdentity() {
        return sampleIdentity;
    }

    public void setSampleIdentity(String sampleIdentity) {
        this.sampleIdentity = sampleIdentity;
    }

    public boolean isCompliant() {
        return compliant;
    }

    public void setCompliant(boolean compliant) {
        this.compliant = compliant;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }

    public Long getTurnaroundDays() {
        return turnaroundDays;
    }

    public void setTurnaroundDays(Long turnaroundDays) {
        this.turnaroundDays = turnaroundDays;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
