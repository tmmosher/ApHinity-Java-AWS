package com.aphinity.client_analytics_core.api.core.plotly;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PlotlyTitle {
    private String text;
    private final Map<String, JsonNode> extras = new LinkedHashMap<>();

    @JsonCreator
    public static PlotlyTitle fromJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        PlotlyTitle title = new PlotlyTitle();
        if (node.isTextual()) {
            title.text = node.asText();
            return title;
        }

        if (node.isObject()) {
            JsonNode textNode = node.get("text");
            if (textNode != null && textNode.isTextual()) {
                title.text = textNode.asText();
            }
            node.properties().forEach(entry -> {
                if (!"text".equals(entry.getKey())) {
                    title.extras.put(entry.getKey(), entry.getValue());
                }
            });
        }
        return title;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
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
