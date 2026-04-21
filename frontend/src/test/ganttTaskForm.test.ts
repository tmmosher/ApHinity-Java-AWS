import {describe, expect, it} from "vitest";
import {
  canEditLocationGanttTask,
  createDefaultGanttTaskDraft,
  createLocationGanttTaskRequestFromDraft,
  normalizeGanttTaskDependencyTaskIds
} from "../util/location/ganttTaskForm";

describe("ganttTaskForm", () => {
  it("allows only partner and admin roles to edit gantt tasks", () => {
    expect(canEditLocationGanttTask("admin")).toBe(true);
    expect(canEditLocationGanttTask("partner")).toBe(true);
    expect(canEditLocationGanttTask("client")).toBe(false);
    expect(canEditLocationGanttTask(undefined)).toBe(false);
  });

  it("creates a default draft that starts and ends on today", () => {
    const now = new Date("2026-04-15T10:00:00Z");
    const draft = createDefaultGanttTaskDraft(now);

    expect(draft.startDate).toBe("2026-04-15");
    expect(draft.endDate).toBe("2026-04-15");
    expect(draft.title).toBe("");
    expect(draft.dependencyTaskIds).toEqual([]);
  });

  it("normalizes a valid draft to a create request", () => {
    const request = createLocationGanttTaskRequestFromDraft({
      title: "  OPS  ",
      startDate: "2026-04-10",
      endDate: "2026-04-12",
      description: "  Operational update  ",
      dependencyTaskIds: []
    });

    expect(request).toEqual({
      title: "OPS",
      startDate: "2026-04-10",
      endDate: "2026-04-12",
      description: "Operational update",
      dependencyTaskIds: []
    });
  });

  it("normalizes gantt dependency ids by filtering invalid values and duplicates", () => {
    expect(normalizeGanttTaskDependencyTaskIds([7, 2, 7, 0, -3, Number.NaN, 5])).toEqual([2, 5, 7]);
  });

  it("rejects task ranges where end date is before start date", () => {
    expect(() => createLocationGanttTaskRequestFromDraft({
      title: "OPS",
      startDate: "2026-04-12",
      endDate: "2026-04-10",
      description: "",
      dependencyTaskIds: []
    })).toThrow("Task end date must be on or after the start date.");
  });
});
