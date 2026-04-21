import type {LocationGanttTask} from "../../types/Types";
import {normalizeGanttTaskDependencyTaskIds} from "./ganttTaskForm";

export type GanttTaskDependencyOption = {
  id: number;
  title: string;
  isMissing?: true;
};

const createGanttTaskDependencyOption = (
  taskId: number,
  title: string,
  isMissing = false
): GanttTaskDependencyOption => ({
  id: taskId,
  title,
  ...(isMissing ? {isMissing: true as const} : {})
});

const sortGanttTasksForDependencies = (
  tasks: readonly LocationGanttTask[]
): LocationGanttTask[] => (
  [...tasks].sort((left, right) => (
    left.title.localeCompare(right.title)
    || left.startDate.localeCompare(right.startDate)
    || left.id - right.id
  ))
);

export const createAvailableGanttTaskDependencyTasks = (
  tasks: readonly LocationGanttTask[],
  currentTaskId: number | undefined,
  selectedDependencyTaskIds: readonly number[],
  searchQuery: string
): LocationGanttTask[] => {
  const normalizedSearchQuery = searchQuery.trim().toLowerCase();
  const selectedTaskIdSet = new Set(normalizeGanttTaskDependencyTaskIds(selectedDependencyTaskIds));

  return sortGanttTasksForDependencies(tasks.filter((task) => {
    if (currentTaskId !== undefined && task.id === currentTaskId) {
      return false;
    }

    if (selectedTaskIdSet.has(task.id)) {
      return false;
    }

    return !normalizedSearchQuery || task.title.toLowerCase().includes(normalizedSearchQuery);
  }));
};

export const createGanttTaskDependencyOptions = (
  tasksById: ReadonlyMap<number, LocationGanttTask>,
  dependencyTaskIds: readonly number[]
): GanttTaskDependencyOption[] => (
  normalizeGanttTaskDependencyTaskIds(dependencyTaskIds)
    .map((taskId) => {
      const task = tasksById.get(taskId);
      return task
        ? createGanttTaskDependencyOption(task.id, task.title)
        : createGanttTaskDependencyOption(taskId, `Task #${taskId}`, true);
    })
    .sort((left, right) => left.title.localeCompare(right.title) || left.id - right.id)
);
