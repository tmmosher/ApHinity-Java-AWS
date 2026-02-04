package com.aphinity.client_analytics_core.api.auth.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class MailSendingService {
    private static final String RECOVERY_EMAIL_SUBJECT = "Password reset";

    private final JavaMailSender mailSender;

    @Value("${app.recovery.from-email:no-reply@example.com}")
    private String recoveryFromEmail;

    public MailSendingService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

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
        mailSender.send(preparator);
    }

    //TODO before sprint end
    public void sendVerificationEmail(String toEmail, String verificationUrl) {
    }

    private String buildRecoveryEmailBody(String recoveryCode, long expiresInSeconds) {
        long expiresInMinutes = Math.max(1, expiresInSeconds / 60);
        return "We received a request to reset your password.\n\n"
            + "Use this recovery code to continue:\n"
            + recoveryCode + "\n\n"
            + "This code expires in " + expiresInMinutes + " minutes.\n\n"
            + "If you did not request a password reset, you can ignore this email.";
    }
}
