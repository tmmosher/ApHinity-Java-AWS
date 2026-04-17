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
  updateLocationGanttTaskById: vi.fn()
}));

vi.mock("../components/location/GanttTaskCreatePopover", () => ({
  GanttTaskCreatePopover: () => <button data-gantt-task-create-trigger="">Add Task</button>
}));

vi.mock("../components/location/GanttTaskPopover", () => ({
  GanttTaskPopover: () => null
}));

import {LocationGanttChartPanel} from "../pages/authenticated/panels/location/LocationGanttChartPanel";

describe("LocationGanttChartPanel", () => {
  it("renders gantt toolbar controls and chart shell", () => {
    const html = renderToString(LocationGanttChartPanel);

    expect(html).toContain("Gantt Chart");
    expect(html).toContain("placeholder=\"Search title...\"");
    expect(html).toContain("Upload CSV");
    expect(html).toContain("Apply");
    expect(html).toContain("Undo");
    expect(html).toContain("data-gantt-task-create-trigger");
    expect(html).toContain("id=\"location-gantt-chart-host\"");
    expect(html).toContain("w-max min-h-[32rem]");
  });
});
