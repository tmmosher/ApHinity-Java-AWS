import {describe, expect, it} from "vitest";
import {renderToString} from "solid-js/web";
import {GanttChartShell} from "../components/gantt/GanttChartShell";

describe("GanttChartShell", () => {
  it("renders a neutral host that the Frappe Gantt library can own", () => {
    const html = renderToString(() => GanttChartShell({}));

    expect(html).toContain('id="location-gantt-chart-host"');
    expect(html).toContain('class="gantt-chart-host"');
    expect(html).not.toContain('class="gantt-container');
  });
});
