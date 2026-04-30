package com.aphinity.client_analytics_core.api.notifications;

import com.aphinity.client_analytics_core.api.auth.services.MailSendingService;
import com.aphinity.client_analytics_core.logging.AsyncLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailOutboxDeliveryServiceTest {
    @Mock
    private MailSendingService mailSendingService;

    @Mock
    private MailOutboxRepository mailOutboxRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private AsyncLogService asyncLogService;

    private MailOutboxDeliveryService mailOutboxDeliveryService;

    @BeforeEach
    void setUp() {
        mailOutboxDeliveryService = new MailOutboxDeliveryService(
            mailSendingService,
            mailOutboxRepository,
            transactionTemplate,
            asyncLogService
        );
    }

    @Test
    void deliverQueuedMessageConsumesSuccessfulDelivery() {
        stubTransactions();

        MailOutboxMessage pending = new MailOutboxMessage();
        pending.setId(8L);
        pending.setMailType(MailOutboxType.VERIFICATION);
        pending.setRecipientEmail("client@example.com");
        pending.setSubject("Account verification");
        pending.setBody("Body");
        pending.setAuthorizedUserId(41L);
        pending.setAttemptCount(0);
        pending.setCreatedAt(Instant.parse("2026-04-01T00:00:00Z"));

        when(mailOutboxRepository.lockById(eq(8L))).thenReturn(Optional.of(pending));

        mailOutboxDeliveryService.deliverQueuedMessage(8L);

        verify(mailSendingService).sendPlainTextEmail(eq("client@example.com"), any(MailDraft.class));
        assertEquals(1, pending.getAttemptCount());
        assertNotNull(pending.getLastAttemptAt());
        assertNotNull(pending.getConsumedAt());
        assertNull(pending.getFailedAt());
        assertNull(pending.getNextAttemptAt());
        assertNull(pending.getLastError());
    }

    @Test
    void deliverQueuedMessageRetriesAfterOneMinuteOnFailure() {
        stubTransactions();

        MailOutboxMessage pending = new MailOutboxMessage();
        pending.setId(12L);
        pending.setMailType(MailOutboxType.VERIFICATION);
        pending.setRecipientEmail("client@example.com");
        pending.setSubject("Account verification");
        pending.setBody("Body");
        pending.setAuthorizedUserId(41L);
        pending.setAttemptCount(0);
        pending.setCreatedAt(Instant.parse("2026-04-01T00:00:00Z"));

        MailException failure = new MailException("smtp unavailable") {};
        when(mailOutboxRepository.lockById(eq(12L))).thenReturn(Optional.of(pending));
        doThrow(failure).when(mailSendingService).sendPlainTextEmail(eq("client@example.com"), any(MailDraft.class));

        mailOutboxDeliveryService.deliverQueuedMessage(12L);

        assertEquals(1, pending.getAttemptCount());
        assertNotNull(pending.getLastAttemptAt());
        assertNotNull(pending.getNextAttemptAt());
        assertEquals(pending.getLastAttemptAt().plus(1, ChronoUnit.MINUTES), pending.getNextAttemptAt());
        assertNull(pending.getFailedAt());
        assertNull(pending.getConsumedAt());
        assertNotNull(pending.getLastError());
        assertTrue(pending.getLastError().contains("smtp unavailable"));
    }

    @Test
    void deliverQueuedMessageRecordsFailureAndDeadLettersAfterThirdAttempt() {
        stubTransactions();

        MailOutboxMessage pending = new MailOutboxMessage();
        pending.setId(9L);
        pending.setMailType(MailOutboxType.RECOVERY);
        pending.setRecipientEmail("client@example.com");
        pending.setSubject("Password reset");
        pending.setBody("Body");
        pending.setAuthorizedUserId(41L);
        pending.setAttemptCount(2);
        pending.setCreatedAt(Instant.parse("2026-04-01T00:00:00Z"));

        MailException failure = new MailException("smtp unavailable") {};
        when(mailOutboxRepository.lockById(eq(9L))).thenReturn(Optional.of(pending));
        doThrow(failure).when(mailSendingService).sendPlainTextEmail(eq("client@example.com"), any(MailDraft.class));

        mailOutboxDeliveryService.deliverQueuedMessage(9L);

        assertEquals(3, pending.getAttemptCount());
        assertNotNull(pending.getLastAttemptAt());
        assertNotNull(pending.getFailedAt());
        assertNull(pending.getConsumedAt());
        assertNull(pending.getNextAttemptAt());
        assertNotNull(pending.getLastError());
        assertTrue(pending.getLastError().contains("smtp unavailable"));
        verify(asyncLogService).log(org.mockito.ArgumentMatchers.contains("Mail outbox exhausted retries"));
    }

    @Test
    void processPendingMailOutboxReconcilesStrandedConsumedRow() {
        stubTransactions();

        MailOutboxMessage pending = new MailOutboxMessage();
        pending.setId(10L);
        pending.setMailType(MailOutboxType.VERIFICATION);
        pending.setRecipientEmail("client@example.com");
        pending.setSubject("Account verification");
        pending.setBody("Body");
        pending.setAuthorizedUserId(41L);
        pending.setAttemptCount(3);
        pending.setCreatedAt(Instant.parse("2026-04-01T00:00:00Z"));
        pending.setNextAttemptAt(null);

        when(mailOutboxRepository.lockDueForDelivery(any(), eq(3), any())).thenReturn(List.of());
        when(mailOutboxRepository.lockTerminalizationCandidates(any(), eq(3), any())).thenReturn(List.of(pending));
        when(mailOutboxRepository.lockById(eq(10L))).thenReturn(Optional.of(pending));

        mailOutboxDeliveryService.processPendingMailOutbox();

        assertNotNull(pending.getConsumedAt());
        assertNull(pending.getFailedAt());
        assertNull(pending.getNextAttemptAt());
        assertNull(pending.getLastError());
    }

    @Test
    void processPendingMailOutboxReconcilesStrandedFailedRow() {
        stubTransactions();

        MailOutboxMessage pending = new MailOutboxMessage();
        pending.setId(11L);
        pending.setMailType(MailOutboxType.RECOVERY);
        pending.setRecipientEmail("client@example.com");
        pending.setSubject("Password reset");
        pending.setBody("Body");
        pending.setAuthorizedUserId(41L);
        pending.setAttemptCount(3);
        pending.setCreatedAt(Instant.parse("2026-04-01T00:00:00Z"));
        pending.setLastError("smtp unavailable");
        pending.setNextAttemptAt(null);

        when(mailOutboxRepository.lockDueForDelivery(any(), eq(3), any())).thenReturn(List.of());
        when(mailOutboxRepository.lockTerminalizationCandidates(any(), eq(3), any())).thenReturn(List.of(pending));
        when(mailOutboxRepository.lockById(eq(11L))).thenReturn(Optional.of(pending));

        mailOutboxDeliveryService.processPendingMailOutbox();

        assertNotNull(pending.getFailedAt());
        assertNull(pending.getConsumedAt());
        assertNull(pending.getNextAttemptAt());
        assertEquals("smtp unavailable", pending.getLastError());
    }

    private void stubTransactions() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<TransactionStatus> consumer = invocation.getArgument(0);
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }
}
