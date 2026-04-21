package com.aphinity.client_analytics_core.api.core.repositories.gantt;

import com.aphinity.client_analytics_core.api.core.entities.gantt.GanttTaskDependency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface GanttTaskDependencyRepository extends JpaRepository<GanttTaskDependency, Long> {
    List<GanttTaskDependency> findByLocation_IdAndGanttTask_IdOrderByDependencyTask_IdAsc(Long locationId, Long ganttTaskId);

    List<GanttTaskDependency> findByLocation_IdAndGanttTask_IdInOrderByGanttTask_IdAscDependencyTask_IdAsc(
        Long locationId,
        Collection<Long> ganttTaskIds
    );

    void deleteByLocation_IdAndGanttTask_Id(Long locationId, Long ganttTaskId);
}
