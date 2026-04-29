import {renderToString} from "solid-js/web";
import {describe, expect, it} from "vitest";
import {GanttChartResourcesColumn} from "../components/gantt/GanttChartResourcesColumn";

describe("GanttChartResourcesColumn", () => {
  it("renders a static resources column with task scaffold buttons", () => {
    const html = renderToString(() => GanttChartResourcesColumn({
      tasks: [
        {
          id: 7,
          title: "Inspect pumps",
          startDate: "2026-04-01",
          endDate: "2026-04-03",
          description: null,
          createdAt: "2026-04-01T00:00:00Z",
          updatedAt: "2026-04-01T00:00:00Z",
          dependencyTaskIds: []
        }
      ]
    }));

    expect(html).toContain("Resources");
    expect(html).toContain("Inspect pumps");
    expect(html).toContain("data-gantt-resource-column");
    expect(html).toContain("data-gantt-resource-button");
    expect(html).toContain('data-gantt-resource-task-id="7"');
  });
});
