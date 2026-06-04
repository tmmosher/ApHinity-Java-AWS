package com.aphinity.client_analytics_core.api.core.entities.dashboard;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Index;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(
    name = "graph_trace",
    indexes = {
        @Index(name = "idx_graph_trace_graph_id", columnList = "graph_id"),
        @Index(name = "idx_graph_trace_graph_order", columnList = "graph_id, trace_order"),
        @Index(name = "idx_graph_trace_graph_key", columnList = "graph_id, trace_key")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_graph_trace_graph_key", columnNames = {"graph_id", "trace_key"}),
        @UniqueConstraint(name = "uk_graph_trace_graph_order", columnNames = {"graph_id", "trace_order"})
    }
)
public class GraphTrace {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "graph_id", nullable = false)
    private Graph graph;

    @Column(name = "trace_key", nullable = false)
    private String traceKey;

    @Column(name = "trace_name", nullable = false)
    private String traceName;

    @Column(name = "trace_type", nullable = false)
    private String traceType;

    @Column(name = "data_mode", nullable = false)
    private String dataMode;

    @Column(name = "trace_order", nullable = false)
    private int traceOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trace_config", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> traceConfig = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "graphTrace", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("pointOrder asc")
    @Fetch(FetchMode.SUBSELECT)
    private List<GraphTimeSeriesPoint> timeSeriesPoints = new ArrayList<>();

    @OneToMany(mappedBy = "graphTrace", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("pointOrder asc")
    @Fetch(FetchMode.SUBSELECT)
    private List<GraphCategoryPoint> categoryPoints = new ArrayList<>();

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (traceConfig == null) {
            traceConfig = new LinkedHashMap<>();
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
        if (traceConfig == null) {
            traceConfig = new LinkedHashMap<>();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Graph getGraph() {
        return graph;
    }

    public void setGraph(Graph graph) {
        this.graph = graph;
    }

    public String getTraceKey() {
        return traceKey;
    }

    public void setTraceKey(String traceKey) {
        this.traceKey = traceKey;
    }

    public String getTraceName() {
        return traceName;
    }

    public void setTraceName(String traceName) {
        this.traceName = traceName;
    }

    public String getTraceType() {
        return traceType;
    }

    public void setTraceType(String traceType) {
        this.traceType = traceType;
    }

    public String getDataMode() {
        return dataMode;
    }

    public void setDataMode(String dataMode) {
        this.dataMode = dataMode;
    }

    public int getTraceOrder() {
        return traceOrder;
    }

    public void setTraceOrder(int traceOrder) {
        this.traceOrder = traceOrder;
    }

    public Map<String, Object> getTraceConfig() {
        return traceConfig;
    }

    public void setTraceConfig(Map<String, Object> traceConfig) {
        this.traceConfig = traceConfig == null ? new LinkedHashMap<>() : new LinkedHashMap<>(traceConfig);
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

    public List<GraphTimeSeriesPoint> getTimeSeriesPoints() {
        return timeSeriesPoints;
    }

    public void setTimeSeriesPoints(List<GraphTimeSeriesPoint> timeSeriesPoints) {
        this.timeSeriesPoints = timeSeriesPoints == null ? new ArrayList<>() : new ArrayList<>(timeSeriesPoints);
    }

    public List<GraphCategoryPoint> getCategoryPoints() {
        return categoryPoints;
    }

    public void setCategoryPoints(List<GraphCategoryPoint> categoryPoints) {
        this.categoryPoints = categoryPoints == null ? new ArrayList<>() : new ArrayList<>(categoryPoints);
    }
}
