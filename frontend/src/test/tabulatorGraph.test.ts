import {describe, expect, it} from "vitest";
import type {LocationGraph} from "../types/Types";
import {createTabulatorGraphModel, isTabulatorGraph} from "../util/graph/tabulatorGraph";

const graph: LocationGraph = {
  id: 1,
  name: "Recent Sample Measurements",
  data: [{
    type: "table",
    name: "Recent Sample Measurements",
    header: {values: ["Facility", "Point Of Use", "Measurement", "Observed", "Value", "CA Status", "Follow-ups"]},
    cells: {
      values: [
        ["Newport Beach"],
        ["Sink 1"],
        ["HPC"],
        ["2026-06-15"],
        ["12"],
        ["Active"],
        [2]
      ]
    },
    customdata: [{
      rowIdentifier: "newport|sink-1",
      caStatus: "Active",
      followUps: [
        {date: "2026-06-20", value: "4"},
        {date: "2026-06-25", value: "1"}
      ]
    }],
    meta: {renderer: "tabulator"}
  }],
  layout: {},
  config: {},
  style: {height: 640},
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-02T00:00:00Z"
};

describe("tabulatorGraph helpers", () => {
  it("detects metadata-tagged table graphs", () => {
    expect(isTabulatorGraph(graph)).toBe(true);
  });

  it("maps table traces into Tabulator columns and rows", () => {
    const model = createTabulatorGraphModel(graph);

    expect(model.columns.map((column) => column.field)).toEqual([
      "facility",
      "point_of_use",
      "measurement",
      "observed",
      "value",
      "ca_status",
      "follow_ups"
    ]);
    expect(model.rows).toEqual([expect.objectContaining({
      rowIdentifier: "newport|sink-1",
      facility: "Newport Beach",
      point_of_use: "Sink 1",
      measurement: "HPC",
      ca_status: "Active",
      followUps: [
        {date: "2026-06-20", value: "4"},
        {date: "2026-06-25", value: "1"}
      ]
    })]);
  });
});
