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

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class GraphResponseMapper {
    private static final Logger log = LoggerFactory.getLogger(GraphResponseMapper.class);
    private static final ZoneId PHOENIX_ZONE = ZoneId.of("America/Phoenix");
    private final Clock clock;

    public GraphResponseMapper() {
        this(Clock.system(PHOENIX_ZONE));
    }

    public GraphResponseMapper(Clock clock) {
        this.clock = clock;
    }

    public GraphResponse toResponse(Graph graph) {
        GraphPayloadMapper.GraphPayload payload = normalize(graph, GraphTimeRange.ALL_TIME);
        LocalDate anchorDate = LocalDate.now(clock);
        Set<GraphTimeRange> materializedRanges = materializedRanges(graph);
        Map<String, List<Map<String, Object>>> timeRangeData = new LinkedHashMap<>();
        for (GraphTimeRange timeRange : GraphTimeRange.values()) {
            timeRangeData.put(
                timeRange.getResponseKey(),
                resolveRangePayload(graph, payload.data(), timeRange, anchorDate, materializedRanges)
            );
        }

        return new GraphResponse(
            graph.getId(),
            graph.getName(),
            payload.data(),
            Map.copyOf(timeRangeData),
            payload.layout(),
            payload.config(),
            payload.style(),
            graph.getCreatedAt(),
            graph.getUpdatedAt()
        );
    }

    private List<Map<String, Object>> resolveRangePayload(
        Graph graph,
        List<Map<String, Object>> allTimePayload,
        GraphTimeRange timeRange,
        LocalDate anchorDate,
        Set<GraphTimeRange> materializedRanges
    ) {
        if (timeRange == GraphTimeRange.ALL_TIME) {
            return allTimePayload;
        }
        if (materializedRanges.contains(timeRange)) {
            return normalize(graph, timeRange).data();
        }
        return GraphTimeRangePayloadProjector.project(allTimePayload, timeRange, anchorDate);
    }

    private Set<GraphTimeRange> materializedRanges(Graph graph) {
        Set<GraphTimeRange> materializedRanges = EnumSet.noneOf(GraphTimeRange.class);
        if (graph == null || graph.getGraphTraces() == null || graph.getGraphTraces().isEmpty()) {
            return materializedRanges;
        }
        graph.getGraphTraces().stream()
            .filter(trace -> trace != null && trace.getTimeRange() != null)
            .forEach(trace -> materializedRanges.add(trace.getTimeRange()));
        return materializedRanges;
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
