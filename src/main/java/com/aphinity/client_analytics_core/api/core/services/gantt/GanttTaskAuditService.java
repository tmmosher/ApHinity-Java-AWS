package com.aphinity.client_analytics_core.api.core.services.gantt;

import com.aphinity.client_analytics_core.api.core.entities.gantt.GanttTask;
import com.aphinity.client_analytics_core.api.core.entities.gantt.GanttTaskAuditAction;
import com.aphinity.client_analytics_core.api.core.entities.gantt.GanttTaskAuditLog;
import com.aphinity.client_analytics_core.logging.AsyncLogService;
import org.springframework.stereotype.Service;

@Service
public class GanttTaskAuditService {
    private final AsyncLogService asyncLogService;

    public GanttTaskAuditService(AsyncLogService asyncLogService) {
        this.asyncLogService = asyncLogService;
    }

    public void recordCreated(Long actorUserId, GanttTask task) {
        record(actorUserId, null, task, GanttTaskAuditAction.CREATED);
    }

    public void recordUpdated(Long actorUserId, GanttTask task) {
        record(actorUserId, null, task, GanttTaskAuditAction.UPDATED);
    }

    public void recordDeleted(Long actorUserId, String actorIpAddress, GanttTask task) {
        record(actorUserId, actorIpAddress, task, GanttTaskAuditAction.DELETED);
    }

    private void record(
        Long actorUserId,
        String actorIpAddress,
        GanttTask task,
        GanttTaskAuditAction action
    ) {
        GanttTaskAuditLog auditLog = GanttTaskAuditLog.from(actorUserId, actorIpAddress, task, action);
        asyncLogService.log(auditLog.toLogLine());
    }
}
