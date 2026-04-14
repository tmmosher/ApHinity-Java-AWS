package com.aphinity.client_analytics_core.api.core.entities.servicecalendar;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "service_event_audit_log")
public class ServiceEventAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_event_id", nullable = false)
    private Long serviceEventId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Column(name = "actor_ip_address", length = 64)
    private String actorIpAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ServiceEventAuditAction action;

    @Column(nullable = false, length = ServiceEvent.TITLE_MAX_LENGTH)
    private String title;

    @Convert(converter = ServiceEventResponsibilityConverter.class)
    @Column(nullable = false)
    private ServiceEventResponsibility responsibility;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "event_time", nullable = false)
    private LocalTime eventTime;

    @Column(name = "end_event_date", nullable = false)
    private LocalDate endEventDate;

    @Column(name = "end_event_time", nullable = false)
    private LocalTime endEventTime;

    @Column(name = "description")
    private String description;

    @Convert(converter = ServiceEventStatusConverter.class)
    @Column(nullable = false)
    private ServiceEventStatus status;

    @Column(name = "service_event_created_at", nullable = false)
    private Instant serviceEventCreatedAt;

    @Column(name = "service_event_updated_at", nullable = false)
    private Instant serviceEventUpdatedAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @PrePersist
    void prePersist() {
        if (recordedAt == null) {
            recordedAt = Instant.now();
        }
        if (actorIpAddress != null) {
            actorIpAddress = actorIpAddress.strip();
            if (actorIpAddress.isBlank()) {
                actorIpAddress = null;
            }
        }
        if (title != null) {
            title = title.strip();
        }
        if (description != null) {
            description = description.strip();
            if (description.isBlank()) {
                description = null;
            }
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getServiceEventId() {
        return serviceEventId;
    }

    public void setServiceEventId(Long serviceEventId) {
        this.serviceEventId = serviceEventId;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(Long actorUserId) {
        this.actorUserId = actorUserId;
    }

    public String getActorIpAddress() {
        return actorIpAddress;
    }

    public void setActorIpAddress(String actorIpAddress) {
        this.actorIpAddress = actorIpAddress;
    }

    public ServiceEventAuditAction getAction() {
        return action;
    }

    public void setAction(ServiceEventAuditAction action) {
        this.action = action;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public ServiceEventResponsibility getResponsibility() {
        return responsibility;
    }

    public void setResponsibility(ServiceEventResponsibility responsibility) {
        this.responsibility = responsibility;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }

    public LocalTime getEventTime() {
        return eventTime;
    }

    public void setEventTime(LocalTime eventTime) {
        this.eventTime = eventTime;
    }

    public LocalDate getEndEventDate() {
        return endEventDate;
    }

    public void setEndEventDate(LocalDate endEventDate) {
        this.endEventDate = endEventDate;
    }

    public LocalTime getEndEventTime() {
        return endEventTime;
    }

    public void setEndEventTime(LocalTime endEventTime) {
        this.endEventTime = endEventTime;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ServiceEventStatus getStatus() {
        return status;
    }

    public void setStatus(ServiceEventStatus status) {
        this.status = status;
    }

    public Instant getServiceEventCreatedAt() {
        return serviceEventCreatedAt;
    }

    public void setServiceEventCreatedAt(Instant serviceEventCreatedAt) {
        this.serviceEventCreatedAt = serviceEventCreatedAt;
    }

    public Instant getServiceEventUpdatedAt() {
        return serviceEventUpdatedAt;
    }

    public void setServiceEventUpdatedAt(Instant serviceEventUpdatedAt) {
        this.serviceEventUpdatedAt = serviceEventUpdatedAt;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }
}
