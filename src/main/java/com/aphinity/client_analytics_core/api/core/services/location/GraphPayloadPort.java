package com.aphinity.client_analytics_core.api.core.services.location;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;

/** Application-owned boundary for converting graph state to and from transport payloads. */
public interface GraphPayloadPort {
    Object readData(Graph graph);

    void writeData(Graph graph, Object data);
}
