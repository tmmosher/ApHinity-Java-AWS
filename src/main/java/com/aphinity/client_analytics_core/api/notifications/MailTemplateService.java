package com.aphinity.client_analytics_core.api.notifications;

import org.springframework.stereotype.Service;

/**
 * Composes the plain-text mail templates used by auth and work-order notifications.
 */
@Service
public class MailTemplateService {
    private static final String RECOVERY_SUBJECT = "Password reset";
    private static final String VERIFICATION_SUBJECT = "Account verification";

    /**
     * Builds the password-recovery email payload.
     */
    public MailDraft buildRecoveryDraft(String recoveryCode, long expiresInSeconds) {
        return new MailDraft(
            RECOVERY_SUBJECT,
            buildRecoveryBody(recoveryCode, expiresInSeconds)
        );
    }

    /**
     * Builds the verification email payload.
     */
    public MailDraft buildVerificationDraft(String verificationCode, long expiresInSeconds) {
        return new MailDraft(
            VERIFICATION_SUBJECT,
            buildVerificationBody(verificationCode, expiresInSeconds)
        );
    }

    /**
     * Builds the corrective-action work-order email payload.
     */
    public MailDraft buildWorkOrderDraft(String locationName, String eventTitle, String description) {
        String normalizedLocationName = normalizeLocationName(locationName);
        String normalizedEventTitle = normalizeText(eventTitle, "Corrective action");
        String normalizedDescription = normalizeDescription(description);
        return new MailDraft(
            "Work order for " + normalizedLocationName + ": " + normalizedEventTitle,
            "A corrective action work order has been created.\n\n"
                + "Location: " + normalizedLocationName + "\n"
                + "Event title: " + normalizedEventTitle + "\n"
                + "Description: " + normalizedDescription + "\n"
        );
    }

    private String buildRecoveryBody(String recoveryCode, long expiresInSeconds) {
        long expiresInMinutes = Math.max(1, expiresInSeconds / 60);
        return "We received a request to reset your password.\n\n"
            + "Use this recovery code to continue:\n"
            + recoveryCode + "\n\n"
            + "This code expires in " + expiresInMinutes + " minutes.\n\n"
            + "If you did not request a password reset, you can ignore this email.";
    }

    private String buildVerificationBody(String verificationCode, long expiresInSeconds) {
        long expiresInMinutes = Math.max(1, expiresInSeconds / 60);
        return "Welcome to ApHinity Management Systems!\n\n"
            + "Use this verification code in your profile to continue:\n"
            + verificationCode + "\n\n"
            + "This code expires in " + expiresInMinutes + " minutes.\n\n"
            + "If you did not request verification, you can ignore this email.";
    }

    private String normalizeLocationName(String locationName) {
        String normalized = normalizeText(locationName, "Unknown location");
        return normalized.isBlank() ? "Unknown location" : normalized;
    }

    private String normalizeDescription(String description) {
        String normalized = normalizeText(description, "No description provided.");
        return normalized.isBlank() ? "No description provided." : normalized;
    }

    private String normalizeText(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.strip();
        return normalized.isBlank() ? fallback : normalized;
    }
}
