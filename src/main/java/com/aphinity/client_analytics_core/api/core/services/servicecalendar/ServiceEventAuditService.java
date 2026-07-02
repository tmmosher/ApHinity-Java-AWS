package com.aphinity.client_analytics_core.api.core.services.servicecalendar;

import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventAuditAction;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventAuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Emits structured audit log entries for service calendar mutations.
 * Audit records are log-backed and include actor IP only for delete operations,
 * where retaining request metadata is most useful after the row is removed.
 */
@Service
public class ServiceEventAuditService {
    private static final Logger log = LoggerFactory.getLogger(ServiceEventAuditService.class);

    public void recordCreated(Long actorUserId, ServiceEvent serviceEvent) {
        record(actorUserId, null, serviceEvent, ServiceEventAuditAction.CREATED);
    }

    public void recordUpdated(Long actorUserId, ServiceEvent serviceEvent) {
        record(actorUserId, null, serviceEvent, ServiceEventAuditAction.UPDATED);
    }

    public void recordDeleted(Long actorUserId, String actorIpAddress, ServiceEvent serviceEvent) {
        record(actorUserId, actorIpAddress, serviceEvent, ServiceEventAuditAction.DELETED);
    }

    private void record(
        Long actorUserId,
        String actorIpAddress,
        ServiceEvent serviceEvent,
        ServiceEventAuditAction action
    ) {
        ServiceEventAuditLog auditLog = ServiceEventAuditLog.from(actorUserId, actorIpAddress, serviceEvent, action);
        log.info(auditLog.toLogLine());
    }
}
