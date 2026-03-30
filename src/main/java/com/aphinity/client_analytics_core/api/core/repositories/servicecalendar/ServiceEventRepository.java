package com.aphinity.client_analytics_core.api.core.repositories.servicecalendar;

import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceEventRepository extends JpaRepository<ServiceEvent, Long> {
    List<ServiceEvent> findByLocation_IdOrderByEventDateAscEventTimeAscEndEventDateAscEndEventTimeAscIdAsc(Long locationId);

    Optional<ServiceEvent> findByIdAndLocation_Id(Long eventId, Long locationId);
}
