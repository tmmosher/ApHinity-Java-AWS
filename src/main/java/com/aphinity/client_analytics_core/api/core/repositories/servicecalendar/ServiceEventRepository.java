package com.aphinity.client_analytics_core.api.core.repositories.servicecalendar;

import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ServiceEventRepository extends JpaRepository<ServiceEvent, Long> {
    @Query("""
        select serviceEvent from ServiceEvent serviceEvent
        where serviceEvent.location.id = :locationId
          and serviceEvent.endEventDate >= :windowStart
          and serviceEvent.eventDate <= :windowEnd
        order by serviceEvent.eventDate asc,
                 serviceEvent.eventTime asc,
                 serviceEvent.endEventDate asc,
                 serviceEvent.endEventTime asc,
                 serviceEvent.id asc
        """)
    List<ServiceEvent> findVisibleByLocationIdAndDateWindow(
        @Param("locationId") Long locationId,
        @Param("windowStart") LocalDate windowStart,
        @Param("windowEnd") LocalDate windowEnd
    );

    Optional<ServiceEvent> findByIdAndLocation_Id(Long eventId, Long locationId);

    @Modifying(flushAutomatically = true)
    @Query("""
        update ServiceEvent correctiveAction
           set correctiveAction.correctiveActionSourceEvent = null
         where correctiveAction.location.id = :locationId
           and correctiveAction.correctiveActionSourceEvent.id = :sourceEventId
        """)
    int clearCorrectiveActionSourceEvent(
        @Param("locationId") Long locationId,
        @Param("sourceEventId") Long sourceEventId
    );
}
