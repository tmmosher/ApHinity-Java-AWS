package com.aphinity.client_analytics_core.api.core.plotly;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PlotlyLayout {
    private PlotlyTitle title;
    private PlotlyAxis xaxis;
    private PlotlyAxis yaxis;
    private String barmode;
    private String template;

    @JsonProperty("showlegend")
    private Boolean showLegend;

    private final Map<String, JsonNode> extras = new LinkedHashMap<>();

    public PlotlyTitle getTitle() {
        return title;
    }

    public void setTitle(PlotlyTitle title) {
        this.title = title;
    }

    public PlotlyAxis getXaxis() {
        return xaxis;
    }

    public void setXaxis(PlotlyAxis xaxis) {
        this.xaxis = xaxis;
    }

    public PlotlyAxis getYaxis() {
        return yaxis;
    }

    public void setYaxis(PlotlyAxis yaxis) {
        this.yaxis = yaxis;
    }

    public String getBarmode() {
        return barmode;
    }

    public void setBarmode(String barmode) {
        this.barmode = barmode;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public Boolean getShowLegend() {
        return showLegend;
    }

    public void setShowLegend(Boolean showLegend) {
        this.showLegend = showLegend;
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
