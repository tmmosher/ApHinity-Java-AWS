package com.aphinity.client_analytics_core.api.core.services.servicecalendar;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.logging.AsyncLogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Sends corrective-action work order emails for location service events.
 */
@Service
public class ServiceCorrectiveActionEmailService {
    private final JavaMailSender mailSender;
    private final AsyncLogService asyncLogService;

    @Value("${app.work-order.from-email:${app.recovery.from-email}}")
    private String fromEmail;

    public ServiceCorrectiveActionEmailService(
        JavaMailSender mailSender,
        AsyncLogService asyncLogService
    ) {
        this.mailSender = mailSender;
        this.asyncLogService = asyncLogService;
    }

    @Async("mailTaskExecutor")
    public void sendCorrectiveActionWorkOrderEmail(
        Location location,
        ServiceEvent sourceEvent,
        ServiceEvent correctiveAction,
        AppUser actor
    ) {
        String recipient = normalizeRecipient(location.getWorkOrderEmail());
        if (recipient == null) {
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipient);
        message.setFrom(fromEmail);
        message.setSubject("Corrective Action Work Order: " + correctiveAction.getTitle());
        message.setText(buildWorkOrderBody(
            location.getName(),
            correctiveAction.getTitle(),
            correctiveAction.getDescription()
        ));

        try {
            mailSender.send(message);
        } catch (MailException ex) {
            asyncLogService.log(
                "Corrective action work-order email send failed | to=" + recipient
                    + ", locationId=" + location.getId()
                    + ", sourceEventId=" + sourceEvent.getId()
                    + ", correctiveActionId=" + correctiveAction.getId()
                    + ", actorUserId=" + actor.getId()
                    + ", errorType=" + ex.getClass().getSimpleName()
                    + ", errorMessage=" + sanitize(ex.getMessage())
            );
        }
    }

    private String buildWorkOrderBody(String locationName, String eventTitle, String description) {
        String normalizedDescription = description == null || description.isBlank()
            ? "No description provided."
            : description.strip();
        return "A corrective action work order has been created.\n\n"
            + "Location: " + locationName + "\n"
            + "Event title: " + eventTitle + "\n"
            + "Description: " + normalizedDescription + "\n";
    }

    private String normalizeRecipient(String recipient) {
        if (recipient == null) {
            return null;
        }
        String normalized = recipient.strip().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}
