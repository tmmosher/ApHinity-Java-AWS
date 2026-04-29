package com.aphinity.client_analytics_core.api.core.entities.dashboard;

import jakarta.persistence.Column;
import jakarta.persistence.Index;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(
    name = "graph_time_series_point",
    indexes = {
        @Index(name = "idx_graph_time_series_point_trace_id", columnList = "graph_trace_id"),
        @Index(name = "idx_graph_time_series_point_trace_observed_at", columnList = "graph_trace_id, observed_at"),
        @Index(name = "idx_graph_time_series_point_trace_order", columnList = "graph_trace_id, point_order")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_graph_time_series_point_trace_order",
            columnNames = {"graph_trace_id", "point_order"}
        )
    }
)
public class GraphTimeSeriesPoint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "graph_trace_id", nullable = false)
    private GraphTrace graphTrace;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "point_order", nullable = false)
    private int pointOrder;

    @Column(name = "y_numeric")
    private BigDecimal yNumeric;

    @Column(name = "y_text")
    private String yText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "point_meta", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> pointMeta = new LinkedHashMap<>();

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
        if (pointMeta == null) {
            pointMeta = new LinkedHashMap<>();
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
        if (pointMeta == null) {
            pointMeta = new LinkedHashMap<>();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public GraphTrace getGraphTrace() {
        return graphTrace;
    }

    public void setGraphTrace(GraphTrace graphTrace) {
        this.graphTrace = graphTrace;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(Instant observedAt) {
        this.observedAt = observedAt;
    }

    public int getPointOrder() {
        return pointOrder;
    }

    public void setPointOrder(int pointOrder) {
        this.pointOrder = pointOrder;
    }

    public BigDecimal getYNumeric() {
        return yNumeric;
    }

    public void setYNumeric(BigDecimal yNumeric) {
        this.yNumeric = yNumeric;
    }

    public String getYText() {
        return yText;
    }

    public void setYText(String yText) {
        this.yText = yText;
    }

    public Map<String, Object> getPointMeta() {
        return pointMeta;
    }

    public void setPointMeta(Map<String, Object> pointMeta) {
        this.pointMeta = pointMeta == null ? new LinkedHashMap<>() : new LinkedHashMap<>(pointMeta);
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
}
