import {renderToString} from "solid-js/web";
import {describe, expect, it} from "vitest";
import type {LocationGraph} from "../types/Types";

import TabulatorGraph, {MINIMUM_TABULATOR_WIDTH_PX} from "../components/graph/TabulatorGraph";

const tableGraph: LocationGraph = {
  id: 1,
  name: "Recent Sample Measurements",
  data: [{
    type: "table",
    name: "Recent Sample Measurements",
    header: {values: ["Facility"]},
    cells: {values: [["Newport Beach"]]},
    meta: {renderer: "tabulator"}
  }],
  layout: {},
  config: {},
  style: {height: 640},
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-02T00:00:00Z"
};

describe("TabulatorGraph", () => {
  it("renders the shared graph loading animation while Tabulator loads", () => {
    const html = renderToString(() => (
      <TabulatorGraph graph={tableGraph} class="h-full w-full" />
    ));

    expect(html).toContain("data-tabulator-loading-placeholder");
    expect(html).toContain("data-graph-loading-placeholder");
    expect(html).toContain("graph-loading-shine");
    expect(html).toContain("Graph loading: Recent Sample Measurements");
    expect(html).toContain("overflow-x-auto");
    expect(html).toContain(`min-width:${MINIMUM_TABULATOR_WIDTH_PX}px`);
  });
});
