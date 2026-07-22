package com.aphinity.client_analytics_core.api.core.services;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** JPA adapter for explicit refreshes required after bulk or database-generated updates. */
@Component
public class JpaPersistenceEntityReloader implements PersistenceEntityReloader {
    private final EntityManager entityManager;

    public JpaPersistenceEntityReloader(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public <T> Optional<T> refreshOrFind(Class<T> entityType, Object id, T currentEntity) {
        if (currentEntity != null && entityManager.contains(currentEntity)) {
            entityManager.refresh(currentEntity);
            return entityType == Object.class ? Optional.empty() : Optional.of(currentEntity);
        }
        if (entityType == Object.class || id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(entityManager.find(entityType, id));
    }
}
