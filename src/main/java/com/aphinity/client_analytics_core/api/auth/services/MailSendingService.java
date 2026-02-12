package com.aphinity.client_analytics_core.api.auth.services;

import com.aphinity.client_analytics_core.logging.AsyncLogService;
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

/**
 * Handles outbound auth-related emails and structured diagnostics for mail failures.
 */
@Service
public class MailSendingService {
    private static final String RECOVERY_EMAIL_SUBJECT = "Password reset";
    private static final int MAX_CAUSE_DEPTH = 6;

    private final JavaMailSender mailSender;
    private final AsyncLogService logService;

    @Value("${app.recovery.from-email}")
    private String recoveryFromEmail;

    public MailSendingService(JavaMailSender mailSender, AsyncLogService logService) {
        this.mailSender = mailSender;
        this.logService = logService;
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
        MimeMessagePreparator preparator = mimeMessage -> {
            MimeMessageHelper helper = new MimeMessageHelper(
                mimeMessage,
                true,
                StandardCharsets.UTF_8.name()
            );
            helper.setTo(toEmail);
            helper.setFrom(recoveryFromEmail);
            helper.setSubject(RECOVERY_EMAIL_SUBJECT);
            helper.setText(buildRecoveryEmailBody(recoveryCode, expiresInSeconds), false);
        };
        try {
            mailSender.send(preparator);
        } catch (MailException ex) {
            // Log high-signal diagnostic metadata for operations without exposing stack traces to clients.
            logService.log(
                "Recovery email send failed | to=" + safeValue(toEmail)
                    + ", from=" + safeValue(recoveryFromEmail)
                    + ", details={" + describeMailException(ex) + "}"
            );
            throw ex;
        }
    }

    //TODO before sprint end
    /**
     * Placeholder for account verification email flow.
     */
    public void sendVerificationEmail(String toEmail, String verificationUrl) {
    }

    /**
     * Builds the plain-text recovery message body.
     */
    private String buildRecoveryEmailBody(String recoveryCode, long expiresInSeconds) {
        long expiresInMinutes = Math.max(1, expiresInSeconds / 60);
        return "We received a request to reset your password.\n\n"
            + "Use this recovery code to continue:\n"
            + recoveryCode + "\n\n"
            + "This code expires in " + expiresInMinutes + " minutes.\n\n"
            + "If you did not request a password reset, you can ignore this email.";
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
        details.append(", causeChain=").append(buildCauseChain(ex, MAX_CAUSE_DEPTH));
        return details.toString();
    }

    /**
     * Appends message-level failures emitted by JavaMail when available.
     */
    private void appendMessageExceptions(StringBuilder details, MailSendException ex) {
        Exception[] messageExceptions = ex.getMessageExceptions();
        if (messageExceptions == null || messageExceptions.length == 0) {
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
                "mimeMessage=" + String.valueOf(entry.getKey())
                    + ", error=" + formatThrowable(entry.getValue())
            );
        }
        details.append(", failedMessages=").append(formatted);
    }

    /**
     * Builds a bounded cause chain string to avoid unbounded log payloads.
     */
    private String buildCauseChain(Throwable throwable, int maxDepth) {
        if (throwable == null) {
            return "";
        }
        List<String> chain = new ArrayList<>();
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < maxDepth) {
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

    /**
     * Sanitizes values for single-line structured logs.
     */
    private String safeValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}
