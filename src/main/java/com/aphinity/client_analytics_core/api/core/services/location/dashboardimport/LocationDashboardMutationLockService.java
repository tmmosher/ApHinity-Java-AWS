package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Serializes dashboard mutations per location so spreadsheet imports and direct graph edits
 * do not race each other.
 */
@Service
public class LocationDashboardMutationLockService {
    private final ConcurrentHashMap<Long, ReentrantLock> locationLocks = new ConcurrentHashMap<>();

    /**
     * Runs the supplied mutation under a per-location lock.
     *
     * <p>When invoked inside a Spring transaction, the lock is held until transaction
     * completion so database writes remain serialized for the full commit window.</p>
     *
     * @param locationId location whose dashboard data is being mutated
     * @param action mutation to execute
     * @return action result
     * @param <T> action return type
     */
    public <T> T executeWithLocationLock(Long locationId, Supplier<T> action) {
        ReentrantLock locationLock = locationLocks.computeIfAbsent(locationId, ignored -> new ReentrantLock());
        locationLock.lock();
        boolean releaseInFinally = true;
        try {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (locationLock.isHeldByCurrentThread()) {
                            locationLock.unlock();
                        }
                    }
                });
                releaseInFinally = false;
            }
            return action.get();
        } finally {
            if (releaseInFinally && locationLock.isHeldByCurrentThread()) {
                locationLock.unlock();
            }
        }
    }
}
