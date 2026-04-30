package com.aphinity.client_analytics_core.api.notifications;

import com.aphinity.client_analytics_core.logging.AsyncLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailOutboxCommandServiceTest {
    @Mock
    private MailOutboxRepository mailOutboxRepository;

    @Mock
    private MailOutboxDeliveryService mailOutboxDeliveryService;

    @Mock
    private TaskExecutor mailTaskExecutor;

    @Mock
    private AsyncLogService asyncLogService;

    private MailOutboxCommandService mailOutboxCommandService;

    @BeforeEach
    void setUp() {
        mailOutboxCommandService = new MailOutboxCommandService(
            new MailTemplateService(),
            mailOutboxRepository,
            mailOutboxDeliveryService,
            mailTaskExecutor,
            asyncLogService
        );
    }

    @Test
    void queueWorkOrderEmailPersistsSnapshotAndSubmitsDispatch() {
        when(mailOutboxRepository.saveAndFlush(any(MailOutboxMessage.class))).thenAnswer(invocation -> {
            MailOutboxMessage message = invocation.getArgument(0);
            message.setId(17L);
            return message;
        });
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(mailTaskExecutor).execute(any(Runnable.class));

        mailOutboxCommandService.queueWorkOrderEmail(
            41L,
            "Austin",
            "work-orders@example.com",
            "Generator alarm",
            "Inspect the west valve"
        );

        ArgumentCaptor<MailOutboxMessage> captor = ArgumentCaptor.forClass(MailOutboxMessage.class);
        verify(mailOutboxRepository).saveAndFlush(captor.capture());
        MailOutboxMessage saved = captor.getValue();
        assertEquals(MailOutboxType.WORK_ORDER, saved.getMailType());
        assertEquals("work-orders@example.com", saved.getRecipientEmail());
        assertEquals("Austin", saved.getLocationName());
        assertEquals("Work order for Austin: Generator alarm", saved.getSubject());
        assertTrue(saved.getBody().contains("Inspect the west valve"));
        assertEquals(41L, saved.getAuthorizedUserId());
        assertEquals(0, saved.getAttemptCount());
        assertNull(saved.getConsumedAt());
        assertNull(saved.getFailedAt());
        verify(mailTaskExecutor).execute(any(Runnable.class));
        verify(mailOutboxDeliveryService).deliverQueuedMessage(17L);
    }
}
