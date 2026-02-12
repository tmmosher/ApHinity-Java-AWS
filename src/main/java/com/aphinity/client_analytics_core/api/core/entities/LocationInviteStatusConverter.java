package com.aphinity.client_analytics_core.api.core.entities;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class LocationInviteStatusConverter implements AttributeConverter<LocationInviteStatus, String> {
    @Override
    public String convertToDatabaseColumn(LocationInviteStatus attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getDatabaseValue();
    }

    @Override
    public LocationInviteStatus convertToEntityAttribute(String dbData) {
        return LocationInviteStatus.fromValue(dbData);
    }
}
