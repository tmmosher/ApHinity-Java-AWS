package com.aphinity.client_analytics_core.api.core.entities.dashboard;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class GraphTimeRangeConverter implements AttributeConverter<GraphTimeRange, String> {
    @Override
    public String convertToDatabaseColumn(GraphTimeRange attribute) {
        return attribute == null ? GraphTimeRange.ALL_TIME.getDatabaseValue() : attribute.getDatabaseValue();
    }

    @Override
    public GraphTimeRange convertToEntityAttribute(String dbData) {
        return GraphTimeRange.fromDatabaseValue(dbData);
    }
}
