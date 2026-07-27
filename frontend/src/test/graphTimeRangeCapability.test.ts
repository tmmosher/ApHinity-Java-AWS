import {describe, expect, it} from "vitest";
import type {LocationGraph} from "../types/Types";
import {sectionSupportsTimeRange} from "../util/graph/graphTimeRangeCapability";

const graph = (id: number, enabled?: boolean): LocationGraph => ({
  id,
  name: `Graph ${id}`,
  data: [],
  layout: enabled === undefined ? {} : {
    meta: {aphinityImport: {sectionTimeRangeEnabled: enabled}}
  },
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z"
});

describe("graph time-range capabilities", () => {
  it("enables ordinary and legacy graphs by default", () => {
    expect(sectionSupportsTimeRange([graph(1), graph(2, true)])).toBe(true);
  });

  it("disables the entire section when any graph opts out", () => {
    expect(sectionSupportsTimeRange([graph(1), graph(2, false)])).toBe(false);
  });

  it("keeps legacy fixed-range table sections disabled until metadata is refreshed", () => {
    const legacyTable = graph(3);
    legacyTable.data = [{type: "table", header: {}, cells: {}}];
    legacyTable.layout = {meta: {aphinityImport: {renderer: "tabulator"}}};

    expect(sectionSupportsTimeRange([legacyTable])).toBe(false);
  });

  it("allows explicit configuration to enable a table renderer", () => {
    const configurableTable = graph(4, true);
    configurableTable.data = [{type: "table", header: {}, cells: {}}];

    expect(sectionSupportsTimeRange([configurableTable])).toBe(true);
  });

  it("does not offer section controls for empty sections", () => {
    expect(sectionSupportsTimeRange([])).toBe(false);
  });
});
