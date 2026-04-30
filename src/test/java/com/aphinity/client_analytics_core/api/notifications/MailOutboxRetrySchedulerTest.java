package com.aphinity.client_analytics_core.api.notifications;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MailOutboxRetrySchedulerTest {
    @Mock
    private MailOutboxDeliveryService mailOutboxDeliveryService;

    @Test
    void retryPendingMailOutboxDelegatesToDeliveryService() {
        MailOutboxRetryScheduler scheduler = new MailOutboxRetryScheduler(mailOutboxDeliveryService);

        scheduler.retryPendingMailOutbox();

        verify(mailOutboxDeliveryService).processPendingMailOutbox();
    }
}
