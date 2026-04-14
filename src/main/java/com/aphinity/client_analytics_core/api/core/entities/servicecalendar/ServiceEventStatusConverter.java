package com.aphinity.client_analytics_core.api.core.entities.servicecalendar;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ServiceEventStatusConverter implements AttributeConverter<ServiceEventStatus, String> {
    @Override
    public String convertToDatabaseColumn(ServiceEventStatus attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getDatabaseValue();
    }

    @Override
    public ServiceEventStatus convertToEntityAttribute(String dbData) {
        return ServiceEventStatus.fromValue(dbData);
    }
}
