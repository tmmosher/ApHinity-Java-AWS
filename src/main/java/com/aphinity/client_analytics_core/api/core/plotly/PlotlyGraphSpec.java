package com.aphinity.client_analytics_core.api.core.plotly;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PlotlyGraphSpec {
    private List<PlotlyTrace> data = new ArrayList<>();
    private PlotlyLayout layout = new PlotlyLayout();
    private Map<String, JsonNode> config = new LinkedHashMap<>();
    private final Map<String, JsonNode> extras = new LinkedHashMap<>();

    public List<PlotlyTrace> getData() {
        return data;
    }

    public void setData(List<PlotlyTrace> data) {
        this.data = data;
    }

    public PlotlyLayout getLayout() {
        return layout;
    }

    public void setLayout(PlotlyLayout layout) {
        this.layout = layout;
    }

    public Map<String, JsonNode> getConfig() {
        return config;
    }

    public void setConfig(Map<String, JsonNode> config) {
        this.config = config;
    }

    @JsonAnySetter
    public void putExtra(String key, JsonNode value) {
        extras.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, JsonNode> getExtras() {
        return extras;
    }
}
