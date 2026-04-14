package com.aphinity.client_analytics_core.api.core.services.servicecalendar;

import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventAuditAction;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventAuditLog;
import com.aphinity.client_analytics_core.logging.AsyncLogService;
import org.springframework.stereotype.Service;

@Service
public class ServiceEventAuditService {
    private final AsyncLogService asyncLogService;

    public ServiceEventAuditService(AsyncLogService asyncLogService) {
        this.asyncLogService = asyncLogService;
    }

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
        asyncLogService.log(auditLog.toLogLine());
    }
}
