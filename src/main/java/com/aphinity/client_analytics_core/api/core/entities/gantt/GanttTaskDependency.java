package com.aphinity.client_analytics_core.api.core.entities.gantt;

import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
    name = "gantt_task_dependency",
    uniqueConstraints = @UniqueConstraint(name = "uq_gantt_task_dependency_pair", columnNames = {
        "gantt_task_id",
        "dependency_task_id"
    })
)
public class GanttTaskDependency {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "gantt_task_id", nullable = false)
    private Long ganttTaskId;

    @Column(name = "dependency_task_id", nullable = false)
    private Long dependencyTaskId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "location_id",
        nullable = false,
        insertable = false,
        updatable = false,
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
        @JoinColumn(
            name = "gantt_task_id",
            referencedColumnName = "id",
            insertable = false,
            updatable = false,
            nullable = false
        ),
        @JoinColumn(
            name = "location_id",
            referencedColumnName = "location_id",
            insertable = false,
            updatable = false,
            nullable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
        )
    })
    private GanttTask ganttTask;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
        @JoinColumn(
            name = "dependency_task_id",
            referencedColumnName = "id",
            insertable = false,
            updatable = false,
            nullable = false
        ),
        @JoinColumn(
            name = "location_id",
            referencedColumnName = "location_id",
            insertable = false,
            updatable = false,
            nullable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
        )
    })
    private GanttTask dependencyTask;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
        this.locationId = location == null ? null : location.getId();
    }

    public Long getGanttTaskId() {
        return ganttTaskId;
    }

    public void setGanttTaskId(Long ganttTaskId) {
        this.ganttTaskId = ganttTaskId;
    }

    public GanttTask getGanttTask() {
        return ganttTask;
    }

    public void setGanttTask(GanttTask ganttTask) {
        this.ganttTask = ganttTask;
        this.ganttTaskId = ganttTask == null ? null : ganttTask.getId();
        if (this.locationId == null && ganttTask != null && ganttTask.getLocation() != null) {
            this.locationId = ganttTask.getLocation().getId();
        }
    }

    public Long getDependencyTaskId() {
        return dependencyTaskId;
    }

    public void setDependencyTaskId(Long dependencyTaskId) {
        this.dependencyTaskId = dependencyTaskId;
    }

    public GanttTask getDependencyTask() {
        return dependencyTask;
    }

    public void setDependencyTask(GanttTask dependencyTask) {
        this.dependencyTask = dependencyTask;
        this.dependencyTaskId = dependencyTask == null ? null : dependencyTask.getId();
        if (this.locationId == null && dependencyTask != null && dependencyTask.getLocation() != null) {
            this.locationId = dependencyTask.getLocation().getId();
        }
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
