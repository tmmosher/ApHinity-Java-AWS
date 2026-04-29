package com.aphinity.client_analytics_core.api.core.entities.dashboard;

import com.aphinity.client_analytics_core.api.core.plotly.GraphPayloadMapper;
import com.aphinity.client_analytics_core.api.core.plotly.GraphRelationalPayloadMapper;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Index;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "graph",
    indexes = {
        @Index(name = "idx_graph_graph_type", columnList = "graph_type"),
        @Index(name = "idx_graph_data_model_version", columnList = "data_model_version"),
        @Index(name = "idx_graph_updated_at", columnList = "updated_at")
    }
)
public class Graph {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // Unused snapshot column retained only for schema compatibility.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "template_data", columnDefinition = "jsonb")
    private Object templateData;

    @Column(name = "graph_type")
    private String graphType;

    @Column(name = "data_model_version")
    private Integer dataModelVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "layout")
    private Map<String, Object> layout;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config")
    private Map<String, Object> config;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "style")
    private Map<String, Object> style;

    @OneToMany(
        mappedBy = "graph",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @OrderBy("traceOrder asc")
    @Fetch(FetchMode.SUBSELECT)
    private List<GraphTrace> graphTraces = new ArrayList<>();

    @OneToMany(mappedBy = "graph")
    private Set<LocationGraph> locationGraphs = new LinkedHashSet<>();

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (dataModelVersion == null && graphTraces != null && !graphTraces.isEmpty()) {
            dataModelVersion = 1;
        }
        if ((graphType == null || graphType.isBlank()) && graphTraces != null && !graphTraces.isEmpty()) {
            graphType = graphTraces.getFirst().getTraceType();
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
        if (dataModelVersion == null && graphTraces != null && !graphTraces.isEmpty()) {
            dataModelVersion = 1;
        }
        if ((graphType == null || graphType.isBlank()) && graphTraces != null && !graphTraces.isEmpty()) {
            graphType = graphTraces.getFirst().getTraceType();
        }
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

    public Object getData() {
        return GraphRelationalPayloadMapper.normalize(this).data();
    }

    public void setData(Object data) {
        GraphPayloadMapper.GraphPayload normalized = GraphPayloadMapper.normalize(
            data,
            layout,
            config,
            style
        );
        GraphRelationalPayloadMapper.syncGraphData(this, normalized.data());
        templateData = null;
        this.layout = normalized.layout();
        this.config = normalized.config();
        this.style = normalized.style();
    }

    public String getGraphType() {
        return graphType;
    }

    public void setGraphType(String graphType) {
        this.graphType = graphType;
    }

    public Integer getDataModelVersion() {
        return dataModelVersion;
    }

    public void setDataModelVersion(Integer dataModelVersion) {
        this.dataModelVersion = dataModelVersion;
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

    public Map<String, Object> getLayout() {
        return layout;
    }

    public void setLayout(Map<String, Object> layout) {
        this.layout = layout;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }

    public Map<String, Object> getStyle() {
        return style;
    }

    public void setStyle(Map<String, Object> style) {
        this.style = style;
    }

    public List<GraphTrace> getGraphTraces() {
        return graphTraces;
    }

    public void setGraphTraces(List<GraphTrace> graphTraces) {
        this.graphTraces = graphTraces == null ? new ArrayList<>() : new ArrayList<>(graphTraces);
    }

    public Set<LocationGraph> getLocationGraphs() {
        return locationGraphs;
    }

    public void setLocationGraphs(Set<LocationGraph> locationGraphs) {
        this.locationGraphs = locationGraphs;
    }
}
