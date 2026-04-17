package com.aphinity.client_analytics_core.api.core.services.servicecalendar;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.core.entities.location.LocationUser;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.response.dashboard.AccountRole;
import com.aphinity.client_analytics_core.api.core.services.AccountRoleService;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationUserRepository;
import com.aphinity.client_analytics_core.logging.AsyncLogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class ServiceCorrectiveActionEmailService {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_TIME;

    private final JavaMailSender mailSender;
    private final LocationUserRepository locationUserRepository;
    private final AccountRoleService accountRoleService;
    private final AsyncLogService asyncLogService;

    @Value("${app.work-order.from-email:${app.recovery.from-email}}")
    private String fromEmail;

    public ServiceCorrectiveActionEmailService(
        JavaMailSender mailSender,
        LocationUserRepository locationUserRepository,
        AccountRoleService accountRoleService,
        AsyncLogService asyncLogService
    ) {
        this.mailSender = mailSender;
        this.locationUserRepository = locationUserRepository;
        this.accountRoleService = accountRoleService;
        this.asyncLogService = asyncLogService;
    }

    public void sendCorrectiveActionWorkOrderEmail(
        Long locationId,
        ServiceEvent sourceEvent,
        ServiceEvent correctiveAction,
        AppUser actor
    ) {
        List<String> recipients = locationUserRepository.findByLocationIdWithUser(locationId).stream()
            .map(LocationUser::getUser)
            .filter(user -> user != null && user.getEmail() != null)
            .filter(user -> accountRoleService.resolveAccountRole(user) == AccountRole.CLIENT)
            .map(AppUser::getEmail)
            .map(email -> email.strip().toLowerCase(Locale.ROOT))
            .filter(email -> !email.isBlank())
            .distinct()
            .toList();
        if (recipients.isEmpty()) {
            return;
        }

        String subject = "Corrective Action Work Order: " + correctiveAction.getTitle();
        String body = buildWorkOrderBody(sourceEvent, correctiveAction, actor);

        for (String recipient : recipients) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipient);
            message.setFrom(fromEmail);
            message.setSubject(subject);
            message.setText(body);

            try {
                mailSender.send(message);
            } catch (MailException ex) {
                asyncLogService.log(
                    "Corrective action work-order email send failed | to=" + recipient
                        + ", locationId=" + locationId
                        + ", sourceEventId=" + sourceEvent.getId()
                        + ", correctiveActionId=" + correctiveAction.getId()
                        + ", actorUserId=" + actor.getId()
                        + ", errorType=" + ex.getClass().getSimpleName()
                        + ", errorMessage=" + sanitize(ex.getMessage())
                );
            }
        }
    }

    private String buildWorkOrderBody(
        ServiceEvent sourceEvent,
        ServiceEvent correctiveAction,
        AppUser actor
    ) {
        String actorName = actor.getName() == null || actor.getName().isBlank()
            ? actor.getEmail()
            : actor.getName().strip();
        String sourceEventTitle = sourceEvent.getTitle();
        String sourceWindow = sourceEvent.getEventDate().format(DATE_FORMATTER)
            + " " + sourceEvent.getEventTime().format(TIME_FORMATTER)
            + " - "
            + sourceEvent.getEndEventDate().format(DATE_FORMATTER)
            + " " + sourceEvent.getEndEventTime().format(TIME_FORMATTER);
        String correctiveWindow = correctiveAction.getEventDate().format(DATE_FORMATTER)
            + " " + correctiveAction.getEventTime().format(TIME_FORMATTER)
            + " - "
            + correctiveAction.getEndEventDate().format(DATE_FORMATTER)
            + " " + correctiveAction.getEndEventTime().format(TIME_FORMATTER);
        String description = correctiveAction.getDescription() == null || correctiveAction.getDescription().isBlank()
            ? "No description provided."
            : correctiveAction.getDescription();

        return "A corrective action work order has been created.\n\n"
            + "Source event: " + sourceEventTitle + "\n"
            + "Source window: " + sourceWindow + "\n\n"
            + "Corrective action: " + correctiveAction.getTitle() + "\n"
            + "Corrective window: " + correctiveWindow + "\n"
            + "Description: " + description + "\n\n"
            + "Created by: " + actorName + "\n";
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}
