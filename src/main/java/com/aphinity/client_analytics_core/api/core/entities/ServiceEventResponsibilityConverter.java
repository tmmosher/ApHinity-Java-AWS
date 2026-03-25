package com.aphinity.client_analytics_core.api.core.entities;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ServiceEventResponsibilityConverter
    implements AttributeConverter<ServiceEventResponsibility, String> {

    @Override
    public String convertToDatabaseColumn(ServiceEventResponsibility attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getDatabaseValue();
    }

    @Override
    public ServiceEventResponsibility convertToEntityAttribute(String dbData) {
        return ServiceEventResponsibility.fromValue(dbData);
    }
}
