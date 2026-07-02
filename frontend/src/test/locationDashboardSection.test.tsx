import {renderToString} from "solid-js/web";
import {describe, expect, it, vi} from "vitest";
import type {LocationGraph, LocationSectionLayout} from "../types/Types";

const capturedChartProps: Array<Record<string, unknown>> = [];

vi.mock("../components/common/Chart", () => ({
  __esModule: true,
  default: (props: Record<string, unknown>) => {
    capturedChartProps.push(props);
    return null;
  }
}));

vi.mock("../components/graph/GraphLoadingPlaceholder", () => ({
  __esModule: true,
  default: () => null
}));

import LocationDashboardSection from "../components/location/LocationDashboardSection";

describe("LocationDashboardSection", () => {
  it("forwards the graph version to the chart renderer", () => {
    capturedChartProps.length = 0;
    const section: LocationSectionLayout = {
      section_id: 7,
      graph_ids: [101]
    };
    const graph: LocationGraph = {
      id: 101,
      name: "Water Quality Compliance",
      data: [{type: "bar", x: ["A"], y: [1]}],
      layout: {title: {text: "Newport Beach"}},
      config: {},
      style: {height: 320},
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-03T00:00:00Z"
    };
    const plotlyModule = Object.assign(() => ({}), {error: undefined});

    renderToString(() => (
      <LocationDashboardSection
        section={section}
        graphs={[graph]}
        missingGraphIds={[]}
        canEditGraphs={false}
        isGraphMutationBusy={false}
        plotlyModule={plotlyModule as never}
        onOpenGraphEditor={() => undefined}
      />
    ));

    expect(capturedChartProps).toHaveLength(1);
    expect(capturedChartProps[0]).toMatchObject({
      name: "Water Quality Compliance",
      version: "2026-01-03T00:00:00Z"
    });
  });

  it("sizes double graphs across the full section grid", () => {
    capturedChartProps.length = 0;
    const section: LocationSectionLayout = {
      section_id: 7,
      graph_ids: [101]
    };
    const graph: LocationGraph = {
      id: 101,
      name: "Water Quality Compliance",
      data: [{type: "bar", x: ["A"], y: [1]}],
      layout: {
        title: {text: "Newport Beach"},
        meta: {aphinitySize: "double"}
      },
      config: {},
      style: {height: 320},
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-03T00:00:00Z"
    };
    const plotlyModule = Object.assign(() => ({}), {error: undefined});

    const html = renderToString(() => (
      <LocationDashboardSection
        section={section}
        graphs={[graph]}
        missingGraphIds={[]}
        canEditGraphs={false}
        isGraphMutationBusy={false}
        plotlyModule={plotlyModule as never}
        onOpenGraphEditor={() => undefined}
      />
    ));

    expect(html).toContain("lg:col-span-2");
    expect(html).toContain("height:640px");
  });
});
