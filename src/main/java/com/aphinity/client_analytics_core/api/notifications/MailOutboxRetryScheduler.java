package com.aphinity.client_analytics_core.api.notifications;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Spring scheduling entrypoint for minute-level mail outbox retry processing.
 */
@Service
public class MailOutboxRetryScheduler {
    private final MailOutboxDeliveryService mailOutboxDeliveryService;

    public MailOutboxRetryScheduler(MailOutboxDeliveryService mailOutboxDeliveryService) {
        this.mailOutboxDeliveryService = mailOutboxDeliveryService;
    }

    @Scheduled(cron = "0 * * * * *", zone = "America/Phoenix")
    public void retryPendingMailOutbox() {
        mailOutboxDeliveryService.processPendingMailOutbox();
    }
}
