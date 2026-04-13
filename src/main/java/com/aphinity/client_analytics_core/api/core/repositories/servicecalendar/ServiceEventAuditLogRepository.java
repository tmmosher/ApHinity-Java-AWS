package com.aphinity.client_analytics_core.api.core.repositories.servicecalendar;

import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceEventAuditLogRepository extends JpaRepository<ServiceEventAuditLog, Long> {
    List<ServiceEventAuditLog> findByServiceEventIdOrderByRecordedAtAsc(Long serviceEventId);
}
