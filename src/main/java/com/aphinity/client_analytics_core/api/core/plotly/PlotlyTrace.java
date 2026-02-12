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
public class PlotlyTrace {
    private String type;
    private String name;
    private String mode;
    private String orientation;
    private String fill;
    private String stackgroup;
    private List<JsonNode> x = new ArrayList<>();
    private List<JsonNode> y = new ArrayList<>();
    private final Map<String, JsonNode> extras = new LinkedHashMap<>();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getOrientation() {
        return orientation;
    }

    public void setOrientation(String orientation) {
        this.orientation = orientation;
    }

    public String getFill() {
        return fill;
    }

    public void setFill(String fill) {
        this.fill = fill;
    }

    public String getStackgroup() {
        return stackgroup;
    }

    public void setStackgroup(String stackgroup) {
        this.stackgroup = stackgroup;
    }

    public List<JsonNode> getX() {
        return x;
    }

    public void setX(List<JsonNode> x) {
        this.x = x;
    }

    public List<JsonNode> getY() {
        return y;
    }

    public void setY(List<JsonNode> y) {
        this.y = y;
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
