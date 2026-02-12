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
public class PlotlyAxis {
    private PlotlyTitle title;
    private String type;
    private Boolean autorange;
    private Boolean visible;
    private String tickformat;
    private List<JsonNode> range = new ArrayList<>();
    private final Map<String, JsonNode> extras = new LinkedHashMap<>();

    public PlotlyTitle getTitle() {
        return title;
    }

    public void setTitle(PlotlyTitle title) {
        this.title = title;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Boolean getAutorange() {
        return autorange;
    }

    public void setAutorange(Boolean autorange) {
        this.autorange = autorange;
    }

    public Boolean getVisible() {
        return visible;
    }

    public void setVisible(Boolean visible) {
        this.visible = visible;
    }

    public String getTickformat() {
        return tickformat;
    }

    public void setTickformat(String tickformat) {
        this.tickformat = tickformat;
    }

    public List<JsonNode> getRange() {
        return range;
    }

    public void setRange(List<JsonNode> range) {
        this.range = range;
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
