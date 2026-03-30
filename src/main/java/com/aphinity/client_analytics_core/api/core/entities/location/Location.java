package com.aphinity.client_analytics_core.api.core.entities.location;

import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "location")
public class Location {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "section_layout", columnDefinition = "jsonb")
    private Map<String, Object> sectionLayout;

    @OneToMany(mappedBy = "location")
    private Set<ServiceEvent> serviceEvents = new LinkedHashSet<>();

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (sectionLayout == null) {
            sectionLayout = defaultSectionLayout();
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Map<String, Object> getSectionLayout() {
        if (sectionLayout == null) {
            return defaultSectionLayout();
        }
        return sectionLayout;
    }

    public void setSectionLayout(Map<String, Object> sectionLayout) {
        this.sectionLayout = sectionLayout;
    }

    public Set<ServiceEvent> getServiceEvents() {
        return serviceEvents;
    }

    public void setServiceEvents(Set<ServiceEvent> serviceEvents) {
        this.serviceEvents = serviceEvents;
    }

    private Map<String, Object> defaultSectionLayout() {
        return Map.of("sections", List.of());
    }
}
