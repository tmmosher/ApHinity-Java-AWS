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
      description: null
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
      description: "Draft"
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
      scroll_to: "start",
      infinite_padding: false,
      container_height: 513
    });
  });
});
