package com.aphinity.client_analytics_core.api.core.services.location;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.plotly.GraphPayloadMapper;
import com.aphinity.client_analytics_core.api.core.response.dashboard.GraphResponse;
import com.aphinity.client_analytics_core.api.error.ApiClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Converts persisted graph entities into API responses after normalizing their
 * relational Plotly payload representation.
 */
@Component
public class GraphResponseMapper {
    private static final Logger log = LoggerFactory.getLogger(GraphResponseMapper.class);
    private final GraphPayloadPort graphPayloadPort;

    public GraphResponseMapper(GraphPayloadPort graphPayloadPort) {
        this.graphPayloadPort = graphPayloadPort;
    }

    /**
     * Maps a graph using its persisted data payload.
     *
     * @param graph graph entity to expose
     * @return normalized graph response
     */
    public GraphResponse toResponse(Graph graph) {
        GraphPayloadMapper.GraphPayload payload = normalize(graph);
        return toResponse(graph, payload, payload.data());
    }

    /**
     * Maps a graph with caller-supplied data, preserving persisted layout,
     * configuration, and style values.
     *
     * @param graph graph entity to expose
     * @param data optional pre-projected graph data
     * @return normalized graph response
     */
    public GraphResponse toResponse(Graph graph, List<Map<String, Object>> data) {
        return toResponse(graph, data, null);
    }

    public GraphResponse toResponse(Graph graph, List<Map<String, Object>> data, Map<String, Object> layout) {
        if (data != null) {
            return new GraphResponse(
                graph.getId(),
                graph.getName(),
                graph.getDescription(),
                data,
                layout == null ? graph.getLayout() : layout,
                graph.getConfig(),
                graph.getStyle(),
                graph.getCreatedAt(),
                graph.getUpdatedAt()
            );
        }
        GraphPayloadMapper.GraphPayload payload = normalize(graph);
        return toResponse(graph, payload, payload.data());
    }

    private GraphResponse toResponse(
        Graph graph,
        GraphPayloadMapper.GraphPayload payload,
        List<Map<String, Object>> data
    ) {
        return new GraphResponse(
            graph.getId(),
            graph.getName(),
            graph.getDescription(),
            data,
            payload.layout(),
            payload.config(),
            payload.style(),
            graph.getCreatedAt(),
            graph.getUpdatedAt()
        );
    }

    private GraphPayloadMapper.GraphPayload normalize(Graph graph) {
        try {
            Object data = graphPayloadPort.readData(graph);
            return GraphPayloadMapper.normalize(data, graph.getLayout(), graph.getConfig(), graph.getStyle());
        } catch (IllegalArgumentException ex) {
            log.warn(
                "Invalid graph payload for graphId={} during response mapping",
                graph == null ? null : graph.getId(),
                ex
            );
            throw new ApiClientException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "graph_data_invalid",
                "Graph data is invalid"
            );
        }
    }
}
