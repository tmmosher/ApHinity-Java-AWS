package com.aphinity.client_analytics_core.api.core.services;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.auth.repositories.AuthSessionRepository;
import com.aphinity.client_analytics_core.api.core.response.AccountRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Stores pending user deletions in-memory and deletes queued users on the daily schedule.
 */
@Service
public class UserDeletionService {
    private static final Logger log = LoggerFactory.getLogger(UserDeletionService.class);
    private static final int MAX_QUEUE_SIZE = 50;

    private final AppUserRepository appUserRepository;
    private final AuthSessionRepository authSessionRepository;
    private final AccountRoleService accountRoleService;
    private final TransactionTemplate transactionTemplate;
    private final ReentrantLock queueLock = new ReentrantLock();
    private final AtomicLong queueGeneration = new AtomicLong();
    private final LinkedHashMap<Long, PendingUserDeletion> pendingDeletions = new LinkedHashMap<>();

    public UserDeletionService(
        AppUserRepository appUserRepository,
        AuthSessionRepository authSessionRepository,
        AccountRoleService accountRoleService,
        TransactionTemplate transactionTemplate
    ) {
        this.appUserRepository = appUserRepository;
        this.authSessionRepository = authSessionRepository;
        this.accountRoleService = accountRoleService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Marks a user for deletion, refreshing the queued snapshot if it already exists.
     */
    public void queueUser(AppUser user, AccountRole role) {
        queueLock.lock();
        try {
            PendingUserDeletion snapshot = new PendingUserDeletion(
                user.getId(),
                user.getEmail(),
                user.getName(),
                role,
                queueGeneration.incrementAndGet()
            );
            if (pendingDeletions.containsKey(user.getId())) {
                pendingDeletions.remove(user.getId());
                pendingDeletions.put(user.getId(), snapshot);
                return;
            }
            if (pendingDeletions.size() >= MAX_QUEUE_SIZE) {
                throw queueFull();
            }
            pendingDeletions.put(user.getId(), snapshot);
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * Removes a user from the pending deletion queue.
     */
    public boolean restoreUser(Long userId) {
        queueLock.lock();
        try {
            return pendingDeletions.remove(userId) != null;
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * Returns queued ids for the provided user ids.
     */
    public Set<Long> findQueuedUserIds(Collection<Long> userIds) {
        queueLock.lock();
        try {
            return userIds.stream()
                .filter(pendingDeletions::containsKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * Deletes all currently queued users at local midnight.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void processPendingDeletions() {
        List<PendingUserDeletion> snapshot = snapshotPendingDeletions();
        if (snapshot.isEmpty()) {
            return;
        }

        List<PendingUserDeletion> processedEntries = new ArrayList<>();
        try {
            transactionTemplate.executeWithoutResult(status -> {
                Map<Long, AppUser> usersById = appUserRepository.findByIdIn(
                    snapshot.stream().map(PendingUserDeletion::userId).toList()
                ).stream().collect(Collectors.toMap(AppUser::getId, user -> user));

                List<Long> deletableIds = new ArrayList<>();
                for (PendingUserDeletion pending : snapshot) {
                    AppUser user = usersById.get(pending.userId());
                    if (user == null) {
                        processedEntries.add(pending);
                        continue;
                    }
                    if (accountRoleService.resolveAccountRole(user) == AccountRole.ADMIN) {
                        log.warn(
                            "Skipping queued user deletion because target is now an admin userId={} email={}",
                            user.getId(),
                            user.getEmail()
                        );
                        processedEntries.add(pending);
                        continue;
                    }
                    deletableIds.add(user.getId());
                    processedEntries.add(pending);
                }

                if (!deletableIds.isEmpty()) {
                    authSessionRepository.deleteAllByUserIdIn(deletableIds);
                    appUserRepository.deleteAllByIdInBatch(deletableIds);
                    appUserRepository.flush();
                    log.info("Deleted queued users count={} userIds={}", deletableIds.size(), deletableIds);
                }
            });
            removeProcessedEntries(processedEntries);
        } catch (RuntimeException ex) {
            log.error(
                "Failed scheduled user deletion run queuedUserIds={}",
                snapshot.stream().map(PendingUserDeletion::userId).toList(),
                ex
            );
        }
    }

    private List<PendingUserDeletion> snapshotPendingDeletions() {
        queueLock.lock();
        try {
            return List.copyOf(pendingDeletions.values());
        } finally {
            queueLock.unlock();
        }
    }

    private void removeProcessedEntries(Collection<PendingUserDeletion> processedEntries) {
        if (processedEntries.isEmpty()) {
            return;
        }
        queueLock.lock();
        try {
            for (PendingUserDeletion processedEntry : processedEntries) {
                PendingUserDeletion currentEntry = pendingDeletions.get(processedEntry.userId());
                if (currentEntry != null && currentEntry.generation() == processedEntry.generation()) {
                    pendingDeletions.remove(processedEntry.userId());
                }
            }
        } finally {
            queueLock.unlock();
        }
    }

    private ResponseStatusException queueFull() {
        return new ResponseStatusException(
            org.springframework.http.HttpStatus.CONFLICT,
            "User deletion queue is full"
        );
    }

    private record PendingUserDeletion(
        Long userId,
        String email,
        String name,
        AccountRole role,
        long generation
    ) {
    }
}
