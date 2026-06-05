package com.aphinity.client_analytics_core.api.core.services.gantt;

import com.aphinity.client_analytics_core.api.core.entities.gantt.GanttTask;
import com.aphinity.client_analytics_core.api.core.entities.gantt.GanttTaskAuditAction;
import com.aphinity.client_analytics_core.api.core.entities.gantt.GanttTaskAuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GanttTaskAuditService {
    private static final Logger log = LoggerFactory.getLogger(GanttTaskAuditService.class);

    public void recordCreated(Long actorUserId, GanttTask task) {
        recordCreated(actorUserId, task, List.of());
    }

    public void recordCreated(Long actorUserId, GanttTask task, List<Long> dependencyTaskIds) {
        record(actorUserId, null, task, dependencyTaskIds, GanttTaskAuditAction.CREATED);
    }

    public void recordUpdated(Long actorUserId, GanttTask task) {
        recordUpdated(actorUserId, task, List.of());
    }

    public void recordUpdated(Long actorUserId, GanttTask task, List<Long> dependencyTaskIds) {
        record(actorUserId, null, task, dependencyTaskIds, GanttTaskAuditAction.UPDATED);
    }

    public void recordDeleted(Long actorUserId, String actorIpAddress, GanttTask task) {
        recordDeleted(actorUserId, actorIpAddress, task, List.of());
    }

    public void recordDeleted(Long actorUserId, String actorIpAddress, GanttTask task, List<Long> dependencyTaskIds) {
        record(actorUserId, actorIpAddress, task, dependencyTaskIds, GanttTaskAuditAction.DELETED);
    }

    private void record(
        Long actorUserId,
        String actorIpAddress,
        GanttTask task,
        List<Long> dependencyTaskIds,
        GanttTaskAuditAction action
    ) {
        GanttTaskAuditLog auditLog = GanttTaskAuditLog.from(actorUserId, actorIpAddress, task, dependencyTaskIds, action);
        log.info(auditLog.toLogLine());
    }
}
