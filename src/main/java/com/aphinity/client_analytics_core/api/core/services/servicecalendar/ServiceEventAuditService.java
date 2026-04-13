package com.aphinity.client_analytics_core.api.core.services.servicecalendar;

import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventAuditAction;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventAuditLog;
import com.aphinity.client_analytics_core.api.core.repositories.servicecalendar.ServiceEventAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ServiceEventAuditService {
    private static final Logger log = LoggerFactory.getLogger(ServiceEventAuditService.class);

    private final ServiceEventAuditLogRepository serviceEventAuditLogRepository;

    public ServiceEventAuditService(ServiceEventAuditLogRepository serviceEventAuditLogRepository) {
        this.serviceEventAuditLogRepository = serviceEventAuditLogRepository;
    }

    public void recordCreated(Long actorUserId, ServiceEvent serviceEvent) {
        record(actorUserId, serviceEvent, ServiceEventAuditAction.CREATED);
    }

    public void recordUpdated(Long actorUserId, ServiceEvent serviceEvent) {
        record(actorUserId, serviceEvent, ServiceEventAuditAction.UPDATED);
    }

    public void recordDeleted(Long actorUserId, ServiceEvent serviceEvent) {
        record(actorUserId, serviceEvent, ServiceEventAuditAction.DELETED);
    }

    private void record(Long actorUserId, ServiceEvent serviceEvent, ServiceEventAuditAction action) {
        ServiceEventAuditLog auditLog = new ServiceEventAuditLog();
        auditLog.setServiceEventId(serviceEvent.getId());
        auditLog.setLocationId(serviceEvent.getLocation().getId());
        auditLog.setActorUserId(actorUserId);
        auditLog.setAction(action);
        auditLog.setTitle(serviceEvent.getTitle());
        auditLog.setResponsibility(serviceEvent.getResponsibility());
        auditLog.setEventDate(serviceEvent.getEventDate());
        auditLog.setEventTime(serviceEvent.getEventTime());
        auditLog.setEndEventDate(serviceEvent.getEndEventDate());
        auditLog.setEndEventTime(serviceEvent.getEndEventTime());
        auditLog.setDescription(serviceEvent.getDescription());
        auditLog.setStatus(serviceEvent.getStatus());
        auditLog.setServiceEventCreatedAt(serviceEvent.getCreatedAt());
        auditLog.setServiceEventUpdatedAt(serviceEvent.getUpdatedAt());
        serviceEventAuditLogRepository.save(auditLog);

        log.info(
            "Recorded service event audit actorUserId={} action={} eventId={} locationId={} title={} responsibility={} " +
                "eventDate={} eventTime={} endEventDate={} endEventTime={} status={} description={}",
            actorUserId,
            action,
            serviceEvent.getId(),
            serviceEvent.getLocation().getId(),
            serviceEvent.getTitle(),
            serviceEvent.getResponsibility(),
            serviceEvent.getEventDate(),
            serviceEvent.getEventTime(),
            serviceEvent.getEndEventDate(),
            serviceEvent.getEndEventTime(),
            serviceEvent.getStatus(),
            serviceEvent.getDescription()
        );
    }
}
