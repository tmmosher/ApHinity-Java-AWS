package com.aphinity.client_analytics_core.api.notifications;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void retryPendingMailOutboxRunsEveryMinuteInPhoenixTime() throws NoSuchMethodException {
        Method method = MailOutboxRetryScheduler.class.getMethod("retryPendingMailOutbox");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertEquals("0 * * * * *", scheduled.cron());
        assertEquals("America/Phoenix", scheduled.zone());
    }
}
