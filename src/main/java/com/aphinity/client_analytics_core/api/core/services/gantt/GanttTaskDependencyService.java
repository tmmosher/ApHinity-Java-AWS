package com.aphinity.client_analytics_core.api.core.services.gantt;

import com.aphinity.client_analytics_core.api.core.entities.gantt.GanttTask;
import com.aphinity.client_analytics_core.api.core.entities.gantt.GanttTaskDependency;
import com.aphinity.client_analytics_core.api.core.repositories.gantt.GanttTaskDependencyRepository;
import com.aphinity.client_analytics_core.api.core.repositories.gantt.GanttTaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GanttTaskDependencyService {
    private final GanttTaskRepository ganttTaskRepository;
    private final GanttTaskDependencyRepository ganttTaskDependencyRepository;

    public GanttTaskDependencyService(
        GanttTaskRepository ganttTaskRepository,
        GanttTaskDependencyRepository ganttTaskDependencyRepository
    ) {
        this.ganttTaskRepository = ganttTaskRepository;
        this.ganttTaskDependencyRepository = ganttTaskDependencyRepository;
    }

    @Transactional(readOnly = true)
    public List<Long> findDependencyTaskIds(Long locationId, Long ganttTaskId) {
        return ganttTaskDependencyRepository
            .findDependencyTaskIdsByLocationIdAndGanttTaskId(locationId, ganttTaskId);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<Long>> findDependencyTaskIdsByTaskIds(Long locationId, Collection<Long> ganttTaskIds) {
        if (ganttTaskIds == null || ganttTaskIds.isEmpty()) {
            return Map.of();
        }

        List<GanttTaskDependency> dependencies = ganttTaskDependencyRepository
            .findByLocation_IdAndGanttTask_IdInOrderByGanttTask_IdAscDependencyTask_IdAsc(locationId, ganttTaskIds);

        Map<Long, List<Long>> dependencyIdsByTaskId = new LinkedHashMap<>();
        for (GanttTaskDependency dependency : dependencies) {
            dependencyIdsByTaskId
                .computeIfAbsent(dependency.getGanttTaskId(), ignored -> new ArrayList<>())
                .add(dependency.getDependencyTaskId());
        }

        dependencyIdsByTaskId.replaceAll((taskId, dependencyIds) -> List.copyOf(dependencyIds));
        return dependencyIdsByTaskId;
    }

    @Transactional
    public List<Long> replaceDependencies(GanttTask task, Collection<Long> dependencyTaskIds) {
        if (task == null || task.getId() == null || task.getLocation() == null || task.getLocation().getId() == null) {
            throw new IllegalArgumentException("Task must be persisted before dependencies can be replaced");
        }

        Long locationId = task.getLocation().getId();
        List<Long> normalizedDependencyTaskIds = normalizeDependencyTaskIds(dependencyTaskIds);
        if (normalizedDependencyTaskIds.contains(task.getId())) {
            throw invalidSelfDependency();
        }

        List<Long> currentDependencyTaskIds = findDependencyTaskIds(locationId, task.getId());
        if (currentDependencyTaskIds.equals(normalizedDependencyTaskIds)) {
            return currentDependencyTaskIds;
        }

        if (normalizedDependencyTaskIds.isEmpty()) {
            ganttTaskDependencyRepository.deleteByLocation_IdAndGanttTask_Id(locationId, task.getId());
            return List.of();
        }

        List<GanttTask> dependencyTasks = ganttTaskRepository.findByLocation_IdAndIdInOrderByIdAsc(
            locationId,
            normalizedDependencyTaskIds
        );
        if (dependencyTasks.size() != normalizedDependencyTaskIds.size()) {
            throw invalidDependencyLocation();
        }

        ganttTaskDependencyRepository.deleteByLocation_IdAndGanttTask_Id(locationId, task.getId());

        List<GanttTaskDependency> dependencies = new ArrayList<>(dependencyTasks.size());
        for (GanttTask dependencyTask : dependencyTasks) {
            GanttTaskDependency dependency = new GanttTaskDependency();
            dependency.setLocation(task.getLocation());
            dependency.setGanttTask(task);
            dependency.setDependencyTask(dependencyTask);
            dependencies.add(dependency);
        }

        ganttTaskDependencyRepository.saveAllAndFlush(dependencies);
        return normalizedDependencyTaskIds;
    }

    @Transactional
    public void deleteDependenciesForTask(Long locationId, Long ganttTaskId) {
        ganttTaskDependencyRepository.deleteByLocation_IdAndGanttTask_Id(locationId, ganttTaskId);
        ganttTaskDependencyRepository.deleteByLocationIdAndDependencyTaskId(locationId, ganttTaskId);
    }

    private List<Long> normalizeDependencyTaskIds(Collection<Long> dependencyTaskIds) {
        if (dependencyTaskIds == null || dependencyTaskIds.isEmpty()) {
            return List.of();
        }
        return dependencyTaskIds.stream()
            .filter(id -> id != null && id > 0)
            .distinct()
            .sorted()
            .toList();
    }

    private ResponseStatusException invalidSelfDependency() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task cannot depend on itself");
    }

    private ResponseStatusException invalidDependencyLocation() {
        return new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Task dependencies must belong to this location"
        );
    }
}
