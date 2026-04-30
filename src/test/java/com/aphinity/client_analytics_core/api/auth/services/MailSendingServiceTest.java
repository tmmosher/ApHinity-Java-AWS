package com.aphinity.client_analytics_core.api.auth.services;

import com.aphinity.client_analytics_core.api.notifications.MailTemplateService;
import com.aphinity.client_analytics_core.logging.AsyncLogService;
import jakarta.mail.Session;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MailSendingServiceTest {
    @Mock
    private JavaMailSender mailSender;

    @Mock
    private AsyncLogService logService;

    private MailSendingService mailSendingService;

    @BeforeEach
    void setUp() {
        mailSendingService = new MailSendingService(mailSender, logService, new MailTemplateService());
        ReflectionTestUtils.setField(mailSendingService, "serviceFromEmail", "no-reply@example.com");
    }

    @Test
    void sendRecoveryEmailBuildsExpectedMessageBodyAndMinimumExpiry() throws Exception {
        mailSendingService.sendRecoveryEmail("client@example.com", "ABC123", 30);

        ArgumentCaptor<MimeMessagePreparator> captor = ArgumentCaptor.forClass(MimeMessagePreparator.class);
        verify(mailSender).send(captor.capture());

        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        captor.getValue().prepare(mimeMessage);

        assertEquals("Password reset", mimeMessage.getSubject());
        assertEquals("client@example.com", mimeMessage.getAllRecipients()[0].toString());
        ByteArrayOutputStream rawOutput = new ByteArrayOutputStream();
        mimeMessage.writeTo(rawOutput);
        String body = rawOutput.toString(StandardCharsets.UTF_8);
        assertTrue(body.contains("ABC123"));
        assertTrue(body.contains("expires in 1 minutes"));
    }

    @Test
    void sendVerificationEmailLogsAndRethrowsGenericMailException() {
        MailException exception = new MailException("smtp\ndown") {
        };
        doThrow(exception).when(mailSender).send(any(MimeMessagePreparator.class));

        assertThrows(MailException.class, () ->
            mailSendingService.sendVerificationEmail("client@example.com\nalias", "V-123", 300)
        );

        ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
        verify(logService).log(logCaptor.capture());
        String logLine = logCaptor.getValue();
        assertTrue(logLine.contains("Mail send failed"));
        assertTrue(logLine.contains("smtp\\ndown"));
        assertTrue(logLine.contains("client@example.com\\nalias"));
    }

    @Test
    void sendVerificationEmailBuildsExpectedMessageBodyAndMinimumExpiry() throws Exception {
        mailSendingService.sendVerificationEmail("client@example.com", "VERIFY-123", 5);

        ArgumentCaptor<MimeMessagePreparator> captor = ArgumentCaptor.forClass(MimeMessagePreparator.class);
        verify(mailSender).send(captor.capture());

        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        captor.getValue().prepare(mimeMessage);

        assertEquals("Account verification", mimeMessage.getSubject());
        assertEquals("client@example.com", mimeMessage.getAllRecipients()[0].toString());
        ByteArrayOutputStream rawOutput = new ByteArrayOutputStream();
        mimeMessage.writeTo(rawOutput);
        String body = rawOutput.toString(StandardCharsets.UTF_8);
        assertTrue(body.contains("VERIFY-123"));
        assertTrue(body.contains("expires in 1 minutes"));
    }

    @Test
    void sendRecoveryEmailLogsMailSendExceptionWithoutOptionalDiagnosticCollections() {
        MailSendException exception = mock(MailSendException.class);
        org.mockito.Mockito.when(exception.getMessage()).thenReturn(null);
        org.mockito.Mockito.when(exception.getMessageExceptions()).thenReturn(new Exception[0]);
        org.mockito.Mockito.when(exception.getFailedMessages()).thenReturn(null);
        org.mockito.Mockito.when(exception.getCause()).thenReturn(null);

        doThrow(exception).when(mailSender).send(any(MimeMessagePreparator.class));

        assertThrows(MailSendException.class, () ->
            mailSendingService.sendRecoveryEmail("client@example.com", "R-123", 300)
        );

        ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
        verify(logService).log(logCaptor.capture());
        String logLine = logCaptor.getValue();
        assertTrue(logLine.contains("Mail send failed"));
        assertFalse(logLine.contains("messageExceptions="));
        assertFalse(logLine.contains("failedMessages="));
    }

    @Test
    void sendRecoveryEmailLogsMailSendExceptionDetailsAndRethrows() {
        MailSendException exception = mock(MailSendException.class);
        Map<Object, Exception> failedMessages = new HashMap<>();
        failedMessages.put("mimeMessage-1", new IllegalArgumentException("invalid recipient"));

        org.mockito.Mockito.when(exception.getMessage()).thenReturn("send failed");
        org.mockito.Mockito.when(exception.getMessageExceptions())
            .thenReturn(new Exception[]{new IllegalStateException("mailbox unavailable")});
        org.mockito.Mockito.when(exception.getFailedMessages()).thenReturn(failedMessages);
        org.mockito.Mockito.when(exception.getCause()).thenReturn(new RuntimeException("transport"));

        doThrow(exception).when(mailSender).send(any(MimeMessagePreparator.class));

        assertThrows(MailSendException.class, () ->
            mailSendingService.sendRecoveryEmail("client@example.com", "R-123", 300)
        );

        ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
        verify(logService).log(logCaptor.capture());
        String logLine = logCaptor.getValue();
        assertTrue(logLine.contains("Mail send failed"));
        assertTrue(logLine.contains("messageExceptions="));
        assertTrue(logLine.contains("failedMessages="));
        assertTrue(logLine.contains("causeChain="));
    }

    @Test
    void sendRecoveryEmailLogsMimeMessageDiagnosticsForFailedTransportPayloads() throws Exception {
        MailSendException exception = mock(MailSendException.class);
        MimeMessage failedMimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        failedMimeMessage.setFrom(new InternetAddress("no-reply@example.com"));
        failedMimeMessage.setRecipients(Message.RecipientType.TO, "client@example.com");
        failedMimeMessage.setSubject("Password reset");
        failedMimeMessage.setText("body", StandardCharsets.UTF_8.name());

        Map<Object, Exception> failedMessages = new HashMap<>();
        failedMessages.put(failedMimeMessage, new IllegalStateException("smtp unavailable"));

        org.mockito.Mockito.when(exception.getMessage()).thenReturn("send failed");
        org.mockito.Mockito.when(exception.getMessageExceptions()).thenReturn(new Exception[0]);
        org.mockito.Mockito.when(exception.getFailedMessages()).thenReturn(failedMessages);
        org.mockito.Mockito.when(exception.getCause()).thenReturn(null);

        doThrow(exception).when(mailSender).send(any(MimeMessagePreparator.class));

        assertThrows(MailSendException.class, () ->
            mailSendingService.sendRecoveryEmail("client@example.com", "R-123", 300)
        );

        ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
        verify(logService).log(logCaptor.capture());
        String logLine = logCaptor.getValue();
        assertTrue(logLine.contains("Mail send failed"));
        assertTrue(logLine.contains("MimeMessage{"));
        assertTrue(logLine.contains("subject=Password reset"));
        assertTrue(logLine.contains("from=[no-reply@example.com]"));
        assertTrue(logLine.contains("to=[client@example.com]"));
    }
}
