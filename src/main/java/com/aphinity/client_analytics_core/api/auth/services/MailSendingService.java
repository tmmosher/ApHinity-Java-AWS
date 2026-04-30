package com.aphinity.client_analytics_core.api.auth.services;

import com.aphinity.client_analytics_core.api.notifications.MailDraft;
import com.aphinity.client_analytics_core.api.notifications.MailTemplateService;
import com.aphinity.client_analytics_core.logging.AsyncLogService;
import jakarta.mail.Address;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles outbound auth-related emails and structured diagnostics for mail failures.
 * High-level callers should prefer the mail outbox command and delivery services; this
 * class only performs the low-level transport step and diagnostic logging.
 */
@Service
public class MailSendingService {
    private static final int MAX_CAUSE_DEPTH = 6;

    private final JavaMailSender mailSender;
    private final AsyncLogService logService;
    private final MailTemplateService mailTemplateService;

    @Value("${app.recovery.from-email}")
    private String serviceFromEmail;

    public MailSendingService(
        JavaMailSender mailSender,
        AsyncLogService logService,
        MailTemplateService mailTemplateService
    ) {
        this.mailSender = mailSender;
        this.logService = logService;
        this.mailTemplateService = mailTemplateService;
    }

    /**
     * Sends a password recovery code email.
     *
     * @param toEmail recipient email address
     * @param recoveryCode one-time recovery code
     * @param expiresInSeconds time-to-live for the code in seconds
     * @throws MailException when the underlying mail transport fails
     */
    public void sendRecoveryEmail(String toEmail, String recoveryCode, long expiresInSeconds) {
        sendPlainTextEmail(toEmail, mailTemplateService.buildRecoveryDraft(recoveryCode, expiresInSeconds));
    }

    /**
     * Sends verification email. Similar in structure to sendRecoveryEmail.
     */
    public void sendVerificationEmail(String toEmail, String verificationCode, long expiresInSeconds) {
        sendPlainTextEmail(toEmail, mailTemplateService.buildVerificationDraft(verificationCode, expiresInSeconds));
    }

    /**
     * Sends an already composed plain-text mail draft.
     *
     * @param toEmail recipient email address
     * @param draft composed mail content
     */
    public void sendPlainTextEmail(String toEmail, MailDraft draft) {
        sendPlainTextEmail(toEmail, draft.subject(), draft.body());
    }

    private void sendPlainTextEmail(String toEmail, String subject, String body) {
        MimeMessagePreparator preparator = mimeMessage -> {
            MimeMessageHelper helper = new MimeMessageHelper(
                mimeMessage,
                true,
                StandardCharsets.UTF_8.name()
            );
            helper.setTo(toEmail);
            helper.setFrom(serviceFromEmail);
            helper.setSubject(subject);
            helper.setText(body, false);
        };
        try {
            mailSender.send(preparator);
        } catch (MailException ex) {
            // Log high-signal diagnostic metadata for operators. Client-facing errors stay sanitized separately.
            logService.log(
                "Mail send failed | to=" + safeValue(toEmail)
                    + ", from=" + safeValue(serviceFromEmail)
                    + ", details={" + describeMailException(ex) + "}"
            );
            throw ex;
        }
    }

    /**
     * Produces a compact, machine-parsable description of mail send failures.
     */
    private String describeMailException(MailException ex) {
        StringBuilder details = new StringBuilder();
        details.append("type=").append(ex.getClass().getName());
        String message = safeValue(ex.getMessage());
        if (!message.isBlank()) {
            details.append(", message=").append(message);
        }
        if (ex instanceof MailSendException mailSendException) {
            appendMessageExceptions(details, mailSendException);
            appendFailedMessages(details, mailSendException.getFailedMessages());
        }
        details.append(", causeChain=").append(buildCauseChain(ex));
        return details.toString();
    }

    /**
     * Appends message-level failures emitted by JavaMail when available.
     */
    private void appendMessageExceptions(StringBuilder details, MailSendException ex) {
        Exception[] messageExceptions = ex.getMessageExceptions();
        if (messageExceptions.length == 0) {
            return;
        }
        List<String> formatted = new ArrayList<>();
        for (Exception messageException : messageExceptions) {
            formatted.add(formatThrowable(messageException));
        }
        details.append(", messageExceptions=").append(formatted);
    }

    /**
     * Appends failed mime message diagnostics keyed by the attempted message object.
     */
    private void appendFailedMessages(StringBuilder details, Map<Object, Exception> failedMessages) {
        if (failedMessages == null || failedMessages.isEmpty()) {
            return;
        }
        List<String> formatted = new ArrayList<>();
        for (Map.Entry<Object, Exception> entry : failedMessages.entrySet()) {
            formatted.add(
                "mimeMessage=" + describeFailedMessage(entry.getKey())
                    + ", error=" + formatThrowable(entry.getValue())
            );
        }
        details.append(", failedMessages=").append(formatted);
    }

    /**
     * Produces a descriptive representation of a failed transport payload.
     */
    private String describeFailedMessage(Object message) {
        if (message instanceof jakarta.mail.internet.MimeMessage mimeMessage) {
            return describeMimeMessage(mimeMessage);
        }
        return safeValue(String.valueOf(message));
    }

    /**
     * Extracts a compact, operator-friendly summary from a MIME message.
     */
    private String describeMimeMessage(jakarta.mail.internet.MimeMessage mimeMessage) {
        try {
            return "MimeMessage{"
                + "subject=" + safeValue(mimeMessage.getSubject())
                + ", from=" + formatAddresses(mimeMessage.getFrom())
                + ", to=" + formatAddresses(mimeMessage.getAllRecipients())
                + ", contentType=" + safeValue(mimeMessage.getContentType())
                + "}";
        } catch (Exception ex) {
            return "MimeMessage{describeFailed="
                + ex.getClass().getSimpleName()
                + "(" + safeValue(ex.getMessage()) + ")}";
        }
    }

    private String formatAddresses(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return "[]";
        }
        return addresses.length == 1
            ? "[" + safeValue(addresses[0].toString()) + "]"
            : addressesAsString(addresses);
    }

    private String addressesAsString(Address[] addresses) {
        return java.util.Arrays.stream(addresses)
            .map(address -> safeValue(address.toString()))
            .collect(Collectors.joining(", ", "[", "]"));
    }

    /**
     * Builds a bounded cause chain string to avoid unbounded log payloads.
     */
    private String buildCauseChain(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        List<String> chain = new ArrayList<>();
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < MailSendingService.MAX_CAUSE_DEPTH) {
            chain.add(formatThrowable(current));
            current = current.getCause();
            depth++;
        }
        if (current != null) {
            chain.add("...");
        }
        return String.join(" -> ", chain);
    }

    /**
     * Converts a throwable to a concise {@code Type(message)} string.
     */
    private String formatThrowable(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        String type = throwable.getClass().getSimpleName();
        String message = safeValue(throwable.getMessage());
        if (message.isBlank()) {
            return type;
        }
        return type + "(" + message + ")";
    }

    private String safeValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}
