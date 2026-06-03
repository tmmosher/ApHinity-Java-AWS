package com.aphinity.client_analytics_core.api.core.services.location;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.GraphTimeRange;
import com.aphinity.client_analytics_core.api.core.plotly.GraphPayloadMapper;
import com.aphinity.client_analytics_core.api.core.plotly.GraphRelationalPayloadMapper;
import com.aphinity.client_analytics_core.api.core.response.dashboard.GraphResponse;
import com.aphinity.client_analytics_core.api.error.ApiClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class GraphResponseMapper {
    private static final Logger log = LoggerFactory.getLogger(GraphResponseMapper.class);

    public GraphResponse toResponse(Graph graph) {
        GraphPayloadMapper.GraphPayload payload = normalize(graph, GraphTimeRange.ALL_TIME);
        return toResponse(graph, payload, payload.data());
    }

    public GraphResponse toResponse(Graph graph, List<Map<String, Object>> data) {
        GraphPayloadMapper.GraphPayload payload = normalize(graph, GraphTimeRange.ALL_TIME);
        return toResponse(graph, payload, data == null ? payload.data() : data);
    }

    private GraphResponse toResponse(
        Graph graph,
        GraphPayloadMapper.GraphPayload payload,
        List<Map<String, Object>> data
    ) {
        return new GraphResponse(
            graph.getId(),
            graph.getName(),
            data,
            payload.layout(),
            payload.config(),
            payload.style(),
            graph.getCreatedAt(),
            graph.getUpdatedAt()
        );
    }

    private GraphPayloadMapper.GraphPayload normalize(Graph graph, GraphTimeRange timeRange) {
        try {
            return GraphRelationalPayloadMapper.normalize(graph, timeRange);
        } catch (IllegalArgumentException ex) {
            log.warn(
                "Invalid graph payload for graphId={} during response mapping range={}",
                graph == null ? null : graph.getId(),
                timeRange,
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
