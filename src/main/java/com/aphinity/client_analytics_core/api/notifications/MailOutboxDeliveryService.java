package com.aphinity.client_analytics_core.api.notifications;

import com.aphinity.client_analytics_core.api.auth.services.MailSendingService;
import com.aphinity.client_analytics_core.logging.AsyncLogService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns the actual outbox delivery workflow, including claim, send, retry and terminal reconciliation.
 */
@Service
public class MailOutboxDeliveryService {
    private static final int MAX_ATTEMPTS = 3;
    private static final int RETRY_BATCH_SIZE = 100;

    private final MailSendingService mailSendingService;
    private final MailOutboxRepository mailOutboxRepository;
    private final TransactionTemplate transactionTemplate;
    private final AsyncLogService asyncLogService;

    public MailOutboxDeliveryService(
        MailSendingService mailSendingService,
        MailOutboxRepository mailOutboxRepository,
        TransactionTemplate transactionTemplate,
        AsyncLogService asyncLogService
    ) {
        this.mailSendingService = mailSendingService;
        this.mailOutboxRepository = mailOutboxRepository;
        this.transactionTemplate = transactionTemplate;
        this.asyncLogService = asyncLogService;
    }

    /**
     * Delivers a single outbox message after it was enqueued for immediate async processing.
     */
    public void deliverQueuedMessage(Long outboxId) {
        try {
            MailOutboxDeliverySnapshot snapshot = claimSingleMessage(outboxId);
            if (snapshot == null) {
                return;
            }
            deliverSnapshot(snapshot);
        } catch (RuntimeException ex) {
            asyncLogService.log(
                "Mail outbox delivery failed | outboxId=" + outboxId
                    + ", errorType=" + ex.getClass().getSimpleName()
                    + ", errorMessage=" + safeValue(ex.getMessage())
            );
        }
    }

    /**
     * Processes all currently due messages and then reconciles any exhausted rows that
     * have not yet reached a terminal state.
     */
    public void processPendingMailOutbox() {
        try {
            drainDueMessages();
            reconcileTerminalMessages();
        } catch (RuntimeException ex) {
            asyncLogService.log(
                "Mail outbox retry run failed | errorType=" + ex.getClass().getSimpleName()
                    + ", errorMessage=" + safeValue(ex.getMessage())
            );
        }
    }

    private void drainDueMessages() {
        while (true) {
            List<MailOutboxDeliverySnapshot> batch = claimPendingBatch();
            if (batch.isEmpty()) {
                return;
            }
            for (MailOutboxDeliverySnapshot snapshot : batch) {
                deliverSnapshot(snapshot);
            }
        }
    }

    private void reconcileTerminalMessages() {
        while (true) {
            List<MailOutboxDeliverySnapshot> batch = claimTerminalBatch();
            if (batch.isEmpty()) {
                return;
            }
            for (MailOutboxDeliverySnapshot snapshot : batch) {
                reconcileTerminalSnapshot(snapshot);
            }
        }
    }

    private MailOutboxDeliverySnapshot claimSingleMessage(Long outboxId) {
        return transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            return mailOutboxRepository.lockById(outboxId)
                .filter(message -> isEligibleForDelivery(message, now))
                .map(message -> {
                    claimForDelivery(message, now);
                    mailOutboxRepository.save(message);
                    return snapshot(message);
                })
                .orElse(null);
        });
    }

    private List<MailOutboxDeliverySnapshot> claimPendingBatch() {
        List<MailOutboxDeliverySnapshot> snapshots = transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            List<MailOutboxMessage> messages = mailOutboxRepository.lockDueForDelivery(
                now,
                MAX_ATTEMPTS,
                PageRequest.of(0, RETRY_BATCH_SIZE)
            );
            if (messages.isEmpty()) {
                return List.of();
            }

            List<MailOutboxDeliverySnapshot> batch = new ArrayList<>(messages.size());
            for (MailOutboxMessage message : messages) {
                if (!isEligibleForDelivery(message, now)) {
                    continue;
                }
                claimForDelivery(message, now);
                batch.add(snapshot(message));
            }
            mailOutboxRepository.saveAll(messages);
            return batch;
        });
        return snapshots == null ? List.of() : snapshots;
    }

    private List<MailOutboxDeliverySnapshot> claimTerminalBatch() {
        List<MailOutboxDeliverySnapshot> snapshots = transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            List<MailOutboxMessage> messages = mailOutboxRepository.lockTerminalizationCandidates(
                now,
                MAX_ATTEMPTS,
                PageRequest.of(0, RETRY_BATCH_SIZE)
            );
            if (messages.isEmpty()) {
                return List.of();
            }

            List<MailOutboxDeliverySnapshot> batch = new ArrayList<>(messages.size());
            for (MailOutboxMessage message : messages) {
                if (message.getConsumedAt() != null || message.getFailedAt() != null) {
                    continue;
                }
                // Lease the row for a retry window so a failed cleanup write cannot spin in-place.
                message.setNextAttemptAt(now.plus(1, ChronoUnit.HOURS));
                batch.add(snapshot(message));
            }
            mailOutboxRepository.saveAll(messages);
            return batch;
        });
        return snapshots == null ? List.of() : snapshots;
    }

    private void deliverSnapshot(MailOutboxDeliverySnapshot snapshot) {
        try {
            mailSendingService.sendPlainTextEmail(
                snapshot.recipientEmail(),
                new MailDraft(snapshot.subject(), snapshot.body())
            );
            markConsumed(snapshot.id());
        } catch (RuntimeException ex) {
            handleDeliveryFailure(snapshot, ex);
        }
    }

    private void reconcileTerminalSnapshot(MailOutboxDeliverySnapshot snapshot) {
        if (snapshot.lastError() == null || snapshot.lastError().isBlank()) {
            markConsumed(snapshot.id());
            return;
        }
        markFailed(snapshot.id(), snapshot.lastError());
    }

    private void handleDeliveryFailure(MailOutboxDeliverySnapshot snapshot, RuntimeException ex) {
        String error = ex.getMessage();
        recordFailureDetails(snapshot.id(), error);
        if (snapshot.attemptCount() >= MAX_ATTEMPTS) {
            markFailed(snapshot.id(), error);
            asyncLogService.log(
                "Mail outbox exhausted retries | outboxId=" + snapshot.id()
                    + ", type=" + snapshot.mailType()
                    + ", attemptCount=" + snapshot.attemptCount()
                    + ", recipient=" + safeValue(snapshot.recipientEmail())
                    + ", locationName=" + safeValue(snapshot.locationName())
                    + ", authorizedUserId=" + snapshot.authorizedUserId()
                    + ", errorType=" + ex.getClass().getSimpleName()
                    + ", errorMessage=" + safeValue(error)
            );
        }
    }

    private void recordFailureDetails(Long outboxId, String error) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                MailOutboxMessage message = mailOutboxRepository.lockById(outboxId).orElse(null);
                if (message == null || message.getConsumedAt() != null || message.getFailedAt() != null) {
                    return;
                }
                message.setLastError(error);
                mailOutboxRepository.save(message);
            });
        } catch (RuntimeException ex) {
            asyncLogService.log(
                "Mail outbox failure details update failed | outboxId=" + outboxId
                    + ", errorType=" + ex.getClass().getSimpleName()
                    + ", errorMessage=" + safeValue(ex.getMessage())
            );
        }
    }

    private void markConsumed(Long outboxId) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                MailOutboxMessage message = mailOutboxRepository.lockById(outboxId).orElse(null);
                if (message == null || message.getConsumedAt() != null || message.getFailedAt() != null) {
                    return;
                }
                message.setConsumedAt(Instant.now());
                message.setNextAttemptAt(null);
                message.setLastError(null);
                mailOutboxRepository.save(message);
            });
        } catch (RuntimeException ex) {
            asyncLogService.log(
                "Mail outbox consumption state update failed | outboxId=" + outboxId
                    + ", errorType=" + ex.getClass().getSimpleName()
                    + ", errorMessage=" + safeValue(ex.getMessage())
            );
        }
    }

    private void markFailed(Long outboxId, String error) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                MailOutboxMessage message = mailOutboxRepository.lockById(outboxId).orElse(null);
                if (message == null || message.getConsumedAt() != null || message.getFailedAt() != null) {
                    return;
                }
                message.setLastError(error);
                message.setFailedAt(Instant.now());
                message.setNextAttemptAt(null);
                mailOutboxRepository.save(message);
            });
        } catch (RuntimeException ex) {
            asyncLogService.log(
                "Mail outbox failure state update failed | outboxId=" + outboxId
                    + ", errorType=" + ex.getClass().getSimpleName()
                    + ", errorMessage=" + safeValue(ex.getMessage())
            );
        }
    }

    private void claimForDelivery(MailOutboxMessage message, Instant now) {
        message.setAttemptCount(message.getAttemptCount() + 1);
        message.setLastAttemptAt(now);
        message.setNextAttemptAt(now.plus(1, ChronoUnit.HOURS));
        message.setLastError(null);
    }

    private boolean isEligibleForDelivery(MailOutboxMessage message, Instant now) {
        return message.getConsumedAt() == null
            && message.getFailedAt() == null
            && message.getAttemptCount() < MAX_ATTEMPTS
            && (message.getNextAttemptAt() == null || !message.getNextAttemptAt().isAfter(now));
    }

    private MailOutboxDeliverySnapshot snapshot(MailOutboxMessage message) {
        return new MailOutboxDeliverySnapshot(
            message.getId(),
            message.getMailType(),
            message.getRecipientEmail(),
            message.getLocationName(),
            message.getSubject(),
            message.getBody(),
            message.getAuthorizedUserId(),
            message.getAttemptCount(),
            message.getLastError()
        );
    }

    private String safeValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}
