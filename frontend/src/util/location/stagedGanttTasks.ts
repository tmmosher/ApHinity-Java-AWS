import type {CreateLocationGanttTaskRequest} from "../../types/Types";
import {normalizeGanttTaskDependencyTaskIds} from "./ganttTaskForm";
import {applyStateSnapshot, undoStateSnapshot} from "../common/stateHistory";
import type {TimelineTaskLike} from "./frappeGanttChart";

export type StagedLocationGanttTask = TimelineTaskLike & {
  isStaged: true;
};

type StagedGanttTaskMutationResult = {
  nextTasks: StagedLocationGanttTask[];
  nextUndoStack: StagedLocationGanttTask[][];
  changed: boolean;
};

type StagedGanttTaskUndoResult = {
  nextTasks: StagedLocationGanttTask[];
  nextUndoStack: StagedLocationGanttTask[][];
  undone: boolean;
};

export const cloneStagedGanttTasks = (
  tasks: StagedLocationGanttTask[]
): StagedLocationGanttTask[] => (
  JSON.parse(JSON.stringify(tasks)) as StagedLocationGanttTask[]
);

const stagedTaskSignature = (task: StagedLocationGanttTask): string => JSON.stringify({
  id: task.id,
  title: task.title,
  startDate: task.startDate,
  endDate: task.endDate,
  description: task.description,
  dependencyTaskIds: normalizeGanttTaskDependencyTaskIds(task.dependencyTaskIds)
});

const stagedTaskListSignature = (tasks: readonly StagedLocationGanttTask[]): string => (
  JSON.stringify(tasks.map(stagedTaskSignature))
);

const nextStagedTaskId = (tasks: readonly StagedLocationGanttTask[]): number => (
  tasks.reduce((lowestId, task) => Math.min(lowestId, task.id), 0) - 1
);

const createStagedTaskFromRequest = (
  request: CreateLocationGanttTaskRequest,
  id: number
): StagedLocationGanttTask => {
  const timestamp = new Date().toISOString();
  return {
    id,
    title: request.title,
    startDate: request.startDate,
    endDate: request.endDate,
    description: request.description,
    dependencyTaskIds: normalizeGanttTaskDependencyTaskIds(request.dependencyTaskIds),
    createdAt: timestamp,
    updatedAt: timestamp,
    isStaged: true
  };
};

const updateStagedTaskSnapshot = (
  currentTasks: StagedLocationGanttTask[],
  undoStack: StagedLocationGanttTask[][],
  nextTasks: StagedLocationGanttTask[]
): StagedGanttTaskMutationResult => {
  const result = applyStateSnapshot(
    currentTasks,
    undoStack,
    nextTasks,
    cloneStagedGanttTasks,
    (left, right) => stagedTaskListSignature(left) === stagedTaskListSignature(right)
  );

  return {
    nextTasks: result.nextState,
    nextUndoStack: result.nextUndoStack,
    changed: result.changed
  };
};

export const stageImportedGanttTasks = (
  currentTasks: StagedLocationGanttTask[],
  undoStack: StagedLocationGanttTask[][],
  requests: readonly CreateLocationGanttTaskRequest[]
): StagedGanttTaskMutationResult => {
  let nextId = nextStagedTaskId(currentTasks);
  const nextTasks = [
    ...currentTasks,
    ...requests.map((request) => {
      const stagedTask = createStagedTaskFromRequest(request, nextId);
      nextId -= 1;
      return stagedTask;
    })
  ];

  return updateStagedTaskSnapshot(currentTasks, undoStack, nextTasks);
};

export const editStagedGanttTask = (
  currentTasks: StagedLocationGanttTask[],
  undoStack: StagedLocationGanttTask[][],
  taskId: number,
  request: CreateLocationGanttTaskRequest
): StagedGanttTaskMutationResult => {
  const nextTasks = currentTasks.map((task) => (
    task.id === taskId
      ? {
          ...task,
          title: request.title,
          startDate: request.startDate,
          endDate: request.endDate,
          description: request.description,
          dependencyTaskIds: normalizeGanttTaskDependencyTaskIds(request.dependencyTaskIds),
          updatedAt: new Date().toISOString()
        }
      : task
  ));

  return updateStagedTaskSnapshot(currentTasks, undoStack, nextTasks);
};

export const deleteStagedGanttTask = (
  currentTasks: StagedLocationGanttTask[],
  undoStack: StagedLocationGanttTask[][],
  taskId: number
): StagedGanttTaskMutationResult => {
  const nextTasks = currentTasks.filter((task) => task.id !== taskId);
  return updateStagedTaskSnapshot(currentTasks, undoStack, nextTasks);
};

export const undoStagedGanttTaskMutation = (
  currentTasks: StagedLocationGanttTask[],
  undoStack: StagedLocationGanttTask[][]
): StagedGanttTaskUndoResult => {
  const result = undoStateSnapshot(currentTasks, undoStack, cloneStagedGanttTasks);
  return {
    nextTasks: result.nextState,
    nextUndoStack: result.nextUndoStack,
    undone: result.undone
  };
};

export const buildGanttTaskRequestsFromStagedTasks = (
  tasks: readonly StagedLocationGanttTask[]
): CreateLocationGanttTaskRequest[] => (
  tasks.map((task) => ({
    title: task.title,
    startDate: task.startDate,
    endDate: task.endDate,
    description: task.description ?? null,
    dependencyTaskIds: normalizeGanttTaskDependencyTaskIds(task.dependencyTaskIds)
  }))
);
