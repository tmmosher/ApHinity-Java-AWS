package com.aphinity.client_analytics_core.api.core.repositories.gantt;

import com.aphinity.client_analytics_core.api.core.entities.gantt.GanttTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GanttTaskRepository extends JpaRepository<GanttTask, Long> {
    List<GanttTask> findByLocation_IdOrderByStartDateAscEndDateAscIdAsc(Long locationId);

    @Query("""
        select ganttTask from GanttTask ganttTask
        where ganttTask.location.id = :locationId
          and (:searchTerm is null or lower(ganttTask.title) like lower(concat('%', :searchTerm, '%')))
        order by ganttTask.startDate asc, ganttTask.endDate asc, ganttTask.id asc
        """)
    List<GanttTask> findVisibleByLocationIdAndTitleSearch(
        @Param("locationId") Long locationId,
        @Param("searchTerm") String searchTerm
    );

    Optional<GanttTask> findByIdAndLocation_Id(Long ganttTaskId, Long locationId);
}
