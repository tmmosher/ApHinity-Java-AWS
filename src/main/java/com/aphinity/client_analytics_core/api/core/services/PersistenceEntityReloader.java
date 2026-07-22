package com.aphinity.client_analytics_core.api.core.services;

import java.util.Optional;

/** Application-owned boundary for persistence-context refresh behavior. */
public interface PersistenceEntityReloader {
    <T> Optional<T> refreshOrFind(Class<T> entityType, Object id, T currentEntity);

    default void refreshIfManaged(Object entity) {
        refreshOrFind(Object.class, null, entity);
    }

    static PersistenceEntityReloader noop() {
        return new PersistenceEntityReloader() {
            @Override
            public <T> Optional<T> refreshOrFind(Class<T> entityType, Object id, T currentEntity) {
                return Optional.empty();
            }
        };
    }
}
