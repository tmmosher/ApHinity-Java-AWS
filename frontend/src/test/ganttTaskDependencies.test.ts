import {describe, expect, it} from "vitest";
import type {LocationGanttTask} from "../types/Types";
import {
  createAvailableGanttTaskDependencyTasks,
  createGanttTaskDependencyOptions
} from "../util/location/ganttTaskDependencies";

const createTask = (
  id: number,
  title: string,
  startDate: string,
  endDate: string
): LocationGanttTask => ({
  id,
  title,
  startDate,
  endDate,
  description: null,
  createdAt: "2026-03-01T00:00:00Z",
  updatedAt: "2026-03-01T00:00:00Z",
  dependencyTaskIds: []
});

describe("ganttTaskDependencies", () => {
  it("filters out the current task and already selected dependencies from available options", () => {
    const tasks = [
      createTask(1, "Alpha", "2026-04-01", "2026-04-02"),
      createTask(2, "Beta", "2026-04-03", "2026-04-04"),
      createTask(3, "Gamma", "2026-04-05", "2026-04-06")
    ];

    expect(createAvailableGanttTaskDependencyTasks(tasks, 2, [3], "")).toEqual([
      tasks[0]
    ]);
  });

  it("filters available dependency options by a case-insensitive title search", () => {
    const tasks = [
      createTask(1, "Alpha", "2026-04-01", "2026-04-02"),
      createTask(2, "Beta", "2026-04-03", "2026-04-04"),
      createTask(3, "Gamma", "2026-04-05", "2026-04-06")
    ];

    expect(createAvailableGanttTaskDependencyTasks(tasks, undefined, [], "ga").map((task) => task.id)).toEqual([3]);
  });

  it("returns available dependency tasks in a stable title-first order", () => {
    const tasks = [
      createTask(3, "Gamma", "2026-04-05", "2026-04-06"),
      createTask(1, "Alpha", "2026-04-01", "2026-04-02"),
      createTask(2, "Beta", "2026-04-03", "2026-04-04")
    ];

    expect(createAvailableGanttTaskDependencyTasks(tasks, undefined, [], "").map((task) => task.id)).toEqual([1, 2, 3]);
  });

  it("resolves selected dependency labels from backend tasks and keeps missing ids visible", () => {
    const tasks = [
      createTask(1, "Alpha", "2026-04-01", "2026-04-02"),
      createTask(3, "Gamma", "2026-04-05", "2026-04-06")
    ];
    const tasksById = new Map(tasks.map((task) => [task.id, task] as const));

    expect(createGanttTaskDependencyOptions(tasksById, [3, 7])).toEqual([
      {
        id: 3,
        title: "Gamma"
      },
      {
        id: 7,
        title: "Task #7",
        isMissing: true
      }
    ]);
  });
});
