import type {
  AccountRole,
  CreateLocationGanttTaskRequest,
  LocationGanttTask
} from "../../types/Types";
import {compareDates, formatDateInputValue, parseDateValue} from "./dateUtility";

export type GanttTaskDraft = {
  title: string;
  startDate: string;
  endDate: string;
  description: string;
  dependencyTaskIds: number[];
};

export const GANTT_TASK_TITLE_MIN_LENGTH = 3;
export const GANTT_TASK_TITLE_MAX_LENGTH = 60;

export const canEditLocationGanttTask = (role: AccountRole | undefined): boolean => (
  role === "admin" || role === "partner"
);

export const createDefaultGanttTaskDraft = (now: Date = new Date()): GanttTaskDraft => {
  const today = formatDateInputValue(now);
  return {
    title: "",
    startDate: today,
    endDate: today,
    description: "",
    dependencyTaskIds: []
  };
};

export const createGanttTaskDraftFromTask = (task: LocationGanttTask): GanttTaskDraft => ({
  title: task.title,
  startDate: task.startDate,
  endDate: task.endDate,
  description: task.description ?? "",
  dependencyTaskIds: normalizeGanttTaskDependencyTaskIds(task.dependencyTaskIds)
});

export const createLocationGanttTaskRequestFromDraft = (
  draft: GanttTaskDraft
): CreateLocationGanttTaskRequest => {
  const normalizedTitle = draft.title.trim();
  if (!normalizedTitle) {
    throw new Error("Task title is required.");
  }
  if (
    normalizedTitle.length < GANTT_TASK_TITLE_MIN_LENGTH
    || normalizedTitle.length > GANTT_TASK_TITLE_MAX_LENGTH
  ) {
    throw new Error("Task title must be between 3 and 60 characters.");
  }

  if (!draft.startDate || !draft.endDate) {
    throw new Error("Task start and end dates are required.");
  }

  if (
    compareDates(
      parseDateValue(draft.endDate, "Enter valid task dates."),
      parseDateValue(draft.startDate, "Enter valid task dates.")
    ) < 0
  ) {
    throw new Error("Task end date must be on or after the start date.");
  }

  return {
    title: normalizedTitle,
    startDate: draft.startDate,
    endDate: draft.endDate,
    description: draft.description.trim() ? draft.description.trim() : null,
    dependencyTaskIds: normalizeGanttTaskDependencyTaskIds(draft.dependencyTaskIds)
  };
};

export const normalizeGanttTaskDependencyTaskIds = (
  dependencyTaskIds: readonly number[] | null | undefined
): number[] => {
  if (!dependencyTaskIds || dependencyTaskIds.length === 0) {
    return [];
  }

  return [...new Set(
    dependencyTaskIds.filter((dependencyTaskId): dependencyTaskId is number => (
      typeof dependencyTaskId === "number"
      && Number.isFinite(dependencyTaskId)
      && dependencyTaskId > 0
    ))
  )].sort((left, right) => left - right);
};
