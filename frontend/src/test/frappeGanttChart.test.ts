import {describe, expect, it} from "vitest";
import {
  createFrappeGanttOptions,
  GANTT_CHART_HOST_CLASS,
  GANTT_CHART_HOST_ID,
  resolveFrappeGanttContainerHeight,
  toFrappeGanttTask
} from "../util/location/frappeGanttChart";

describe("frappeGanttChart", () => {
  it("maps persisted gantt tasks into the shape Frappe Gantt expects", () => {
    expect(
      toFrappeGanttTask({
        id: 42,
        title: "Maintenance Window",
        startDate: "2026-04-01",
        endDate: "2026-04-10",
        description: null,
        dependencyTaskIds: [],
        createdAt: "2026-03-01T00:00:00Z",
        updatedAt: "2026-03-02T00:00:00Z"
      })
    ).toEqual({
      id: "42",
      name: "Maintenance Window",
      start: "2026-04-01",
      end: "2026-04-10",
      progress: 0,
      custom_class: "gantt-task-persisted",
      description: null,
      dependencies: []
    });
  });

  it("marks staged tasks with a distinct CSS class", () => {
    expect(
      toFrappeGanttTask({
        id: -7,
        title: "Imported draft",
        startDate: "2026-04-11",
        endDate: "2026-04-12",
        description: "Draft",
        dependencyTaskIds: [9, 4],
        createdAt: "2026-03-01T00:00:00Z",
        updatedAt: "2026-03-02T00:00:00Z",
        isStaged: true
      })
    ).toEqual({
      id: "-7",
      name: "Imported draft",
      start: "2026-04-11",
      end: "2026-04-12",
      progress: 0,
      custom_class: "gantt-task-staged",
      description: "Draft",
      dependencies: ["9", "4"]
    });
  });

  it("maps dependency ids into Frappe Gantt dependency strings", () => {
    expect(
      toFrappeGanttTask({
        id: 99,
        title: "Dependent task",
        startDate: "2026-04-11",
        endDate: "2026-04-12",
        description: "Draft",
        dependencyTaskIds: [12, 7, 12],
        createdAt: "2026-03-01T00:00:00Z",
        updatedAt: "2026-03-02T00:00:00Z"
      })
    ).toMatchObject({
      id: "99",
      dependencies: ["12", "7", "12"]
    });
  });

  it("keeps the chart shell separate from Frappe Gantt's own container class", () => {
    expect(GANTT_CHART_HOST_CLASS).not.toBe("gantt-container");
    expect(GANTT_CHART_HOST_ID).toBe("location-gantt-chart-host");
  });

  it("rounds the chart container height up to the nearest pixel", () => {
    expect(resolveFrappeGanttContainerHeight(474.583)).toBe(475);
    expect(resolveFrappeGanttContainerHeight(0)).toBe(1);
  });

  it("creates a chart option set that fills the measured host height", () => {
    const options = createFrappeGanttOptions(512.2, () => undefined);

    expect(options).toMatchObject({
      readonly: true,
      popup_on: "click",
      view_mode: "Day",
      date_format: "YYYY-MM-DD",
      scroll_to: "today",
      infinite_padding: false,
      container_height: 513,
      view_modes: [
        {
          name: "Day",
          padding: ["7d", "14d"],
          step: "1d",
          date_format: "YYYY-MM-DD"
        }
      ]
    });
  });
});
