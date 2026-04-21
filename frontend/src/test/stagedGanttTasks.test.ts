import {describe, expect, it} from "vitest";
import {
  buildGanttTaskRequestsFromStagedTasks,
  deleteStagedGanttTask,
  editStagedGanttTask,
  stageImportedGanttTasks,
  undoStagedGanttTaskMutation
} from "../util/location/stagedGanttTasks";

describe("stagedGanttTasks", () => {
  it("stages imported gantt tasks with deterministic negative ids", () => {
    const result = stageImportedGanttTasks([], [], [
      {
        title: "OPS",
        startDate: "2026-04-01",
        endDate: "2026-04-04",
        description: null,
        dependencyTaskIds: [7, 2, 7]
      },
      {
        title: "QMS",
        startDate: "2026-04-05",
        endDate: "2026-04-06",
        description: "Validation",
        dependencyTaskIds: []
      }
    ]);

    expect(result.changed).toBe(true);
    expect(result.nextTasks).toHaveLength(2);
    expect(result.nextTasks[0].id).toBe(-1);
    expect(result.nextTasks[1].id).toBe(-2);
    expect(result.nextTasks[0].dependencyTaskIds).toEqual([2, 7]);
    expect(result.nextTasks[1].dependencyTaskIds).toEqual([]);
    expect(result.nextUndoStack).toEqual([[]]);
  });

  it("edits and deletes staged tasks while preserving undo snapshots", () => {
    const staged = stageImportedGanttTasks([], [], [{
      title: "OPS",
      startDate: "2026-04-01",
      endDate: "2026-04-04",
      description: null,
      dependencyTaskIds: [9]
    }]);

    const edited = editStagedGanttTask(staged.nextTasks, staged.nextUndoStack, -1, {
      title: "OPS Updated",
      startDate: "2026-04-02",
      endDate: "2026-04-05",
      description: "Updated",
      dependencyTaskIds: [4, 1, 4]
    });
    expect(edited.nextTasks[0].title).toBe("OPS Updated");
    expect(edited.nextTasks[0].dependencyTaskIds).toEqual([1, 4]);
    expect(edited.nextUndoStack).toHaveLength(2);

    const deleted = deleteStagedGanttTask(edited.nextTasks, edited.nextUndoStack, -1);
    expect(deleted.nextTasks).toEqual([]);
    expect(deleted.nextUndoStack).toHaveLength(3);

    const undone = undoStagedGanttTaskMutation(deleted.nextTasks, deleted.nextUndoStack);
    expect(undone.undone).toBe(true);
    expect(undone.nextTasks).toHaveLength(1);
    expect(undone.nextTasks[0].title).toBe("OPS Updated");
  });

  it("builds API requests from staged tasks", () => {
    const staged = stageImportedGanttTasks([], [], [{
      title: "OPS",
      startDate: "2026-04-01",
      endDate: "2026-04-04",
      description: "Desc",
      dependencyTaskIds: [11, 7, 11]
    }]).nextTasks;

    expect(buildGanttTaskRequestsFromStagedTasks(staged)).toEqual([{
      title: "OPS",
      startDate: "2026-04-01",
      endDate: "2026-04-04",
      description: "Desc",
      dependencyTaskIds: [7, 11]
    }]);
  });
});
