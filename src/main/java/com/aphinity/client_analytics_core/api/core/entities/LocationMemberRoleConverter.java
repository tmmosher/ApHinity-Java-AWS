package com.aphinity.client_analytics_core.api.core.entities;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class LocationMemberRoleConverter implements AttributeConverter<LocationMemberRole, String> {
    @Override
    public String convertToDatabaseColumn(LocationMemberRole attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getDatabaseValue();
    }

    @Override
    public LocationMemberRole convertToEntityAttribute(String dbData) {
        return LocationMemberRole.fromValue(dbData);
    }
}
