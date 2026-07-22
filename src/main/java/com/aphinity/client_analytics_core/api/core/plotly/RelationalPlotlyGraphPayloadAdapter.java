package com.aphinity.client_analytics_core.api.core.plotly;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.services.location.GraphPayloadPort;
import org.springframework.stereotype.Component;

/** Plotly/relational implementation of the application graph-payload port. */
@Component
public class RelationalPlotlyGraphPayloadAdapter implements GraphPayloadPort {
    @Override
    public Object readData(Graph graph) {
        return GraphRelationalPayloadMapper.readData(graph);
    }

    @Override
    public void writeData(Graph graph, Object data) {
        GraphRelationalPayloadMapper.writeData(graph, data);
    }
}
