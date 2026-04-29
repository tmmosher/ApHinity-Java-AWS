package com.aphinity.client_analytics_core.api.core.repositories.gantt;

import com.aphinity.client_analytics_core.api.core.entities.gantt.GanttTaskDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface GanttTaskDependencyRepository extends JpaRepository<GanttTaskDependency, Long> {
    List<GanttTaskDependency> findByLocation_IdAndGanttTask_IdOrderByDependencyTask_IdAsc(Long locationId, Long ganttTaskId);

    List<GanttTaskDependency> findByLocation_IdAndGanttTask_IdInOrderByGanttTask_IdAscDependencyTask_IdAsc(
        Long locationId,
        Collection<Long> ganttTaskIds
    );

    @Query("""
        select dependency.dependencyTaskId
        from GanttTaskDependency dependency
        where dependency.locationId = :locationId
          and dependency.ganttTaskId = :ganttTaskId
        order by dependency.dependencyTaskId asc
        """)
    List<Long> findDependencyTaskIdsByLocationIdAndGanttTaskId(
        @Param("locationId") Long locationId,
        @Param("ganttTaskId") Long ganttTaskId
    );

    List<GanttTaskDependency> findByLocationIdAndDependencyTaskIdOrderByGanttTaskIdAsc(
        Long locationId,
        Long dependencyTaskId
    );

    void deleteByLocation_IdAndGanttTask_Id(Long locationId, Long ganttTaskId);

    void deleteByLocationIdAndDependencyTaskId(Long locationId, Long dependencyTaskId);
}
