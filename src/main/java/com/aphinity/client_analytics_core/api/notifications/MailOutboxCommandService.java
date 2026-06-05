package com.aphinity.client_analytics_core.api.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * Accepts outbound mail requests, persists them to the outbox table, and submits
 * post-commit delivery work to the async executor.
 */
@Service
public class MailOutboxCommandService {
    private static final Logger log = LoggerFactory.getLogger(MailOutboxCommandService.class);

    private final MailTemplateService mailTemplateService;
    private final MailOutboxRepository mailOutboxRepository;
    private final MailOutboxDeliveryService mailOutboxDeliveryService;
    private final TaskExecutor mailTaskExecutor;

    public MailOutboxCommandService(
        MailTemplateService mailTemplateService,
        MailOutboxRepository mailOutboxRepository,
        MailOutboxDeliveryService mailOutboxDeliveryService,
        @Qualifier("mailTaskExecutor") TaskExecutor mailTaskExecutor
    ) {
        this.mailTemplateService = mailTemplateService;
        this.mailOutboxRepository = mailOutboxRepository;
        this.mailOutboxDeliveryService = mailOutboxDeliveryService;
        this.mailTaskExecutor = mailTaskExecutor;
    }

    /**
     * Queues a verification email and schedules post-commit async delivery.
     */
    @Transactional
    public void queueVerificationEmail(Long authorizedUserId, String recipientEmail, String verificationCode, long expiresInSeconds) {
        MailDraft draft = mailTemplateService.buildVerificationDraft(verificationCode, expiresInSeconds);
        queueMail(
            MailOutboxType.VERIFICATION,
            authorizedUserId,
            recipientEmail,
            null,
            draft,
            "Unable to send verification email"
        );
    }

    /**
     * Queues a recovery email and schedules post-commit async delivery.
     */
    @Transactional
    public void queueRecoveryEmail(Long authorizedUserId, String recipientEmail, String recoveryCode, long expiresInSeconds) {
        MailDraft draft = mailTemplateService.buildRecoveryDraft(recoveryCode, expiresInSeconds);
        queueMail(
            MailOutboxType.RECOVERY,
            authorizedUserId,
            recipientEmail,
            null,
            draft,
            "Unable to send recovery email"
        );
    }

    /**
     * Queues a corrective-action work-order email and schedules post-commit async delivery.
     */
    @Transactional
    public void queueWorkOrderEmail(
        Long authorizedUserId,
        String locationName,
        String recipientEmail,
        String eventTitle,
        String description
    ) {
        MailDraft draft = mailTemplateService.buildWorkOrderDraft(locationName, eventTitle, description);
        queueMail(
            MailOutboxType.WORK_ORDER,
            authorizedUserId,
            recipientEmail,
            locationName,
            draft,
            "Unable to send work-order email"
        );
    }

    private void queueMail(
        MailOutboxType type,
        Long authorizedUserId,
        String recipientEmail,
        String locationName,
        MailDraft draft,
        String failureReason
    ) {
        MailOutboxMessage message = new MailOutboxMessage();
        message.setMailType(type);
        message.setAuthorizedUserId(authorizedUserId);
        message.setRecipientEmail(recipientEmail == null ? null : recipientEmail.strip());
        message.setLocationName(locationName == null ? null : locationName.strip());
        message.setSubject(draft.subject());
        message.setBody(draft.body());
        try {
            MailOutboxMessage saved = mailOutboxRepository.saveAndFlush(message);
            runAfterCommit(() -> submitForDelivery(saved.getId()));
        } catch (RuntimeException ex) {
            log.error(
                "Mail outbox enqueue failed | type={}, recipient={}, locationName={}, authorizedUserId={}, errorType={}, errorMessage={}",
                type,
                safeValue(recipientEmail),
                safeValue(locationName),
                authorizedUserId,
                ex.getClass().getSimpleName(),
                safeValue(ex.getMessage()),
                ex
            );
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, failureReason, ex);
        }
    }

    private void submitForDelivery(Long outboxId) {
        try {
            mailTaskExecutor.execute(() -> mailOutboxDeliveryService.deliverQueuedMessage(outboxId));
        } catch (RuntimeException ex) {
            log.error(
                "Mail outbox dispatch submission failed | outboxId={}, errorType={}, errorMessage={}",
                outboxId,
                ex.getClass().getSimpleName(),
                safeValue(ex.getMessage()),
                ex
            );
        }
    }

    private void runAfterCommit(Runnable callback) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    callback.run();
                }
            });
            return;
        }
        callback.run();
    }

    private String safeValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}
