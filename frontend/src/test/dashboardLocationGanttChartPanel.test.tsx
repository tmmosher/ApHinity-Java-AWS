import {renderToString} from "solid-js/web";
import {describe, expect, it, vi} from "vitest";

vi.mock("frappe-gantt", () => ({
  default: class MockGantt {
    update_options(): void {}
    refresh(): void {}
    get_bar(): undefined {
      return undefined;
    }
  }
}));

vi.mock("@solidjs/router", () => ({
  useParams: () => ({locationId: "42"})
}));

vi.mock("../context/ApiHostContext", () => ({
  useApiHost: () => "https://example.test"
}));

vi.mock("../context/ProfileContext", () => ({
  useProfile: () => ({
    profile: () => ({role: "partner"})
  })
}));

vi.mock("../util/location/locationGanttTaskApi", () => ({
  createLocationGanttTaskById: vi.fn(),
  createLocationGanttTasksBulkById: vi.fn(),
  deleteLocationGanttTaskById: vi.fn(),
  fetchLocationGanttTasksById: vi.fn(async () => []),
  getLocationGanttTaskTemplateDownloadUrl: vi.fn(() => (
    "https://example.test/api/core/locations/42/gantt-tasks/template"
  )),
  updateLocationGanttTaskById: vi.fn()
}));

vi.mock("../components/gantt/GanttTaskCreatePopover", () => ({
  GanttTaskCreatePopover: (_props: Record<string, unknown>) => (
    <button data-gantt-task-create-trigger="">Add Task</button>
  )
}));

vi.mock("../components/gantt/GanttTaskPopover", () => ({
  GanttTaskPopover: (_props: Record<string, unknown>) => null
}));

import {LocationGanttChartPanel} from "../pages/authenticated/panels/location/LocationGanttChartPanel";

describe("LocationGanttChartPanel", () => {
  it("renders gantt toolbar controls and chart shell", () => {
    const html = renderToString(LocationGanttChartPanel);

    expect(html).toContain("Gantt Chart");
    expect(html).toContain("placeholder=\"Search title...\"");
    expect(html).toContain("Upload Excel spreadsheet");
    expect(html).not.toContain("Upload CSV");
    expect(html).toContain("accept=\".xlsx\"");
    expect(html).toContain("Apply");
    expect(html).toContain("Undo");
    expect(html).toContain("https://example.test/api/core/locations/42/gantt-tasks/template");
    expect(html).toContain("Get a copy of the Excel template");
    expect(html).toContain("data-gantt-task-create-trigger");
    expect(html).toContain("id=\"location-gantt-chart-host\"");
    expect(html).toContain("w-max min-h-[32rem]");
  });
});
