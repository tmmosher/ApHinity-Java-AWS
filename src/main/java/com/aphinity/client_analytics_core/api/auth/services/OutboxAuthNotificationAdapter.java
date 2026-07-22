package com.aphinity.client_analytics_core.api.auth.services;

import com.aphinity.client_analytics_core.api.notifications.MailOutboxCommandService;
import org.springframework.stereotype.Component;

/** Transactional-outbox adapter for authentication notifications. */
@Component
public class OutboxAuthNotificationAdapter implements AuthNotificationPort {
    private final MailOutboxCommandService outbox;

    public OutboxAuthNotificationAdapter(MailOutboxCommandService outbox) {
        this.outbox = outbox;
    }

    @Override
    public void sendVerificationCode(Long userId, String email, String code, long ttlSeconds) {
        outbox.queueVerificationEmail(userId, email, code, ttlSeconds);
    }

    @Override
    public void sendRecoveryCode(Long userId, String email, String code, long ttlSeconds) {
        outbox.queueRecoveryEmail(userId, email, code, ttlSeconds);
    }
}
