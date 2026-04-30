package com.aphinity.client_analytics_core.api.integration;

import com.aphinity.client_analytics_core.api.notifications.MailOutboxCommandService;
import com.aphinity.client_analytics_core.api.notifications.MailOutboxDeliveryService;
import com.aphinity.client_analytics_core.api.notifications.MailOutboxMessage;
import com.aphinity.client_analytics_core.api.notifications.MailOutboxRepository;
import com.aphinity.client_analytics_core.api.notifications.MailOutboxType;
import com.aphinity.client_analytics_core.logging.AsyncLogService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(properties = "spring.profiles.active=integration")
class MailOutboxIntegrationTest {
    @Autowired
    private MailOutboxCommandService mailOutboxCommandService;

    @Autowired
    private MailOutboxDeliveryService mailOutboxDeliveryService;

    @Autowired
    private MailOutboxRepository mailOutboxRepository;

    @MockitoBean
    private JavaMailSender mailSender;

    @MockitoBean
    private AsyncLogService asyncLogService;

    @BeforeEach
    void setUp() {
        mailOutboxRepository.deleteAll();
        reset(mailSender, asyncLogService);
    }

    @Test
    void queueWorkOrderEmailPersistsMessageAndDeliversThroughMailTransport() throws Exception {
        doAnswer(invocation -> null).when(mailSender).send(any(MimeMessagePreparator.class));

        mailOutboxCommandService.queueWorkOrderEmail(
            44L,
            "  Austin  ",
            "work-orders@example.com",
            "Generator alarm",
            "Inspect the west valve"
        );

        MailOutboxMessage saved = awaitConsumedOutboxMessage();

        ArgumentCaptor<MimeMessagePreparator> preparatorCaptor = ArgumentCaptor.forClass(MimeMessagePreparator.class);
        verify(mailSender).send(preparatorCaptor.capture());
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        preparatorCaptor.getValue().prepare(mimeMessage);

        assertEquals("Work order for Austin: Generator alarm", mimeMessage.getSubject());
        assertEquals("no-reply@test.aphinity", mimeMessage.getFrom()[0].toString());
        assertEquals("work-orders@example.com", mimeMessage.getAllRecipients()[0].toString());

        ByteArrayOutputStream rawMessage = new ByteArrayOutputStream();
        mimeMessage.writeTo(rawMessage);
        assertTrue(rawMessage.toString(StandardCharsets.UTF_8).contains("Inspect the west valve"));

        List<MailOutboxMessage> messages = mailOutboxRepository.findAll();
        assertEquals(1, messages.size());
        assertEquals(MailOutboxType.WORK_ORDER, saved.getMailType());
        assertEquals(44L, saved.getAuthorizedUserId());
        assertEquals("work-orders@example.com", saved.getRecipientEmail());
        assertEquals("Austin", saved.getLocationName());
        assertEquals("Work order for Austin: Generator alarm", saved.getSubject());
        assertTrue(saved.getBody().contains("Inspect the west valve"));
        assertEquals(1, saved.getAttemptCount());
        assertNotNull(saved.getConsumedAt());
        assertNull(saved.getFailedAt());
        assertNull(saved.getNextAttemptAt());
        assertNull(saved.getLastError());
    }

    private MailOutboxMessage awaitConsumedOutboxMessage() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (System.currentTimeMillis() < deadline) {
            MailOutboxMessage message = mailOutboxRepository.findAll().stream().findFirst().orElse(null);
            if (message != null && message.getConsumedAt() != null) {
                return message;
            }
            Thread.sleep(25L);
        }

        fail("Timed out waiting for the outbox message to be consumed");
        return null;
    }

    @Test
    void processPendingMailOutboxMarksMessageFailedAfterThirdAttempt() {
        MailOutboxMessage message = new MailOutboxMessage();
        message.setMailType(MailOutboxType.RECOVERY);
        message.setRecipientEmail("client@example.com");
        message.setLocationName(null);
        message.setSubject("Password reset");
        message.setBody("Body");
        message.setAuthorizedUserId(77L);
        message.setAttemptCount(2);
        message.setNextAttemptAt(Instant.now().minusSeconds(1));
        message.setCreatedAt(Instant.parse("2026-04-01T00:00:00Z"));
        MailOutboxMessage saved = mailOutboxRepository.saveAndFlush(message);

        doThrow(new MailException("smtp unavailable") {
        }).when(mailSender).send(any(MimeMessagePreparator.class));

        mailOutboxDeliveryService.processPendingMailOutbox();

        MailOutboxMessage reloaded = mailOutboxRepository.findById(saved.getId()).orElseThrow();
        assertEquals(3, reloaded.getAttemptCount());
        assertNotNull(reloaded.getFailedAt());
        assertNull(reloaded.getConsumedAt());
        assertNull(reloaded.getNextAttemptAt());
        assertTrue(reloaded.getLastError().contains("smtp unavailable"));
        verify(asyncLogService).log(org.mockito.ArgumentMatchers.contains("Mail outbox exhausted retries"));
    }
}
