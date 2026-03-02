import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderPlotlyChart } from "../components/Chart";
import type { LocationGraph } from "../types/Types";
import {
  applyGraphPayloadEdit,
  createEditableGraphPayload,
  type EditableGraphPayload
} from "../util/graphEditor";
import {
  removeTraceWithPlotly,
  setTraceColor,
  updateCartesianX,
  updateCartesianY
} from "../util/graphTraceEditor";

type MinimalDiv = {
  style: Record<string, string>;
  remove: ReturnType<typeof vi.fn>;
};

type MinimalDocument = {
  createElement: ReturnType<typeof vi.fn>;
  documentElement: {
    getAttribute: ReturnType<typeof vi.fn>;
  };
  body: {
    appendChild: ReturnType<typeof vi.fn>;
  };
};

const createDocumentStub = () => {
  const appended: MinimalDiv[] = [];
  const documentStub: MinimalDocument = {
    createElement: vi.fn(() => {
      const element: MinimalDiv = {
        style: {},
        remove: vi.fn()
      };
      return element;
    }),
    documentElement: {
      getAttribute: vi.fn(() => null)
    },
    body: {
      appendChild: vi.fn((element: MinimalDiv) => {
        appended.push(element);
      })
    }
  };

  return { documentStub, appended };
};

describe("graph editing integration", () => {
  let originalDocument: unknown;

  beforeEach(() => {
    originalDocument = (globalThis as { document?: unknown }).document;
  });

  afterEach(() => {
    if (originalDocument === undefined) {
      Reflect.deleteProperty(globalThis, "document");
    } else {
      Object.defineProperty(globalThis, "document", {
        configurable: true,
        value: originalDocument
      });
    }
  });

  it("applies cartesian edits and renders the updated trace payload", async () => {
    const baseGraph: LocationGraph = {
      id: 10,
      name: "Flow Rate",
      data: [{ type: "scatter", name: "Daily", x: [1], y: [9] }],
      layout: { title: { text: "Baseline" } },
      config: { displayModeBar: false },
      style: { height: 280 },
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-02T00:00:00Z"
    };

    const payload = createEditableGraphPayload(baseGraph);
    const editedTrace = setTraceColor(
      updateCartesianY(
        updateCartesianX(payload.data[0], 0, "2"),
        0,
        "14"
      ),
      "scatter",
      "#dc2626"
    );
    payload.data = [editedTrace];

    const editResult = applyGraphPayloadEdit([baseGraph], [], 10, payload);
    expect(editResult.changed).toBe(true);
    expect(baseGraph.data[0]).toEqual({ type: "scatter", name: "Daily", x: [1], y: [9] });

    const react = vi.fn().mockResolvedValue(undefined);
    await renderPlotlyChart(
      { react } as unknown as { react: (...args: unknown[]) => Promise<unknown> },
      { id: "chart-root" } as unknown as HTMLDivElement,
      editResult.nextGraphs[0].data as any,
      editResult.nextGraphs[0].layout as any,
      editResult.nextGraphs[0].config as any
    );

    expect(react).toHaveBeenCalledTimes(1);
    const [, renderedData] = react.mock.calls[0];
    expect(renderedData).toEqual([
      {
        type: "scatter",
        name: "Daily",
        x: [2],
        y: [14],
        marker: { color: "#dc2626" },
        line: { color: "#dc2626" }
      }
    ]);
  });

  it("removes traces through Plotly.deleteTraces without mutating the active editor payload", async () => {
    const { documentStub, appended } = createDocumentStub();
    Object.defineProperty(globalThis, "document", {
      configurable: true,
      value: documentStub
    });

    const payload: EditableGraphPayload = {
      data: [
        {
          type: "bar",
          name: "A",
          x: ["Jan"],
          y: [3],
          customdata: [[10]]
        },
        {
          type: "bar",
          name: "B",
          x: ["Feb"],
          y: [5]
        }
      ],
      layout: { title: { text: "Throughput" } },
      config: { displayModeBar: false },
      style: null
    };

    const newPlot = vi.fn(async (_element, traces: Array<Record<string, unknown>>) => {
      const nested = traces[0].customdata as number[][];
      nested[0][0] = 999;
    });
    const deleteTraces = vi.fn().mockResolvedValue(undefined);
    const purge = vi.fn();

    const nextData = await removeTraceWithPlotly(
      { newPlot, deleteTraces, purge } as any,
      payload,
      0
    );

    expect(newPlot).toHaveBeenCalledTimes(1);
    expect(deleteTraces).toHaveBeenCalledTimes(1);
    expect(deleteTraces).toHaveBeenCalledWith(expect.anything(), [0]);
    expect(nextData).toEqual([payload.data[1]]);
    expect((payload.data[0].customdata as number[][])[0][0]).toBe(10);
    expect(appended).toHaveLength(1);
    expect(appended[0].remove).toHaveBeenCalledTimes(1);
    expect(purge).toHaveBeenCalledTimes(1);
  });

  it("keeps rendered output aligned with deleted traces after local apply", async () => {
    const { documentStub } = createDocumentStub();
    Object.defineProperty(globalThis, "document", {
      configurable: true,
      value: documentStub
    });

    const graph: LocationGraph = {
      id: 44,
      name: "Inspections",
      data: [
        { type: "bar", name: "Open", x: ["Q1"], y: [8] },
        { type: "bar", name: "Closed", x: ["Q1"], y: [5] }
      ],
      layout: { title: { text: "Open vs Closed" } },
      config: { displayModeBar: false },
      style: { height: 260 },
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-02T00:00:00Z"
    };

    const deleteTracePlotly = {
      newPlot: vi.fn().mockResolvedValue(undefined),
      deleteTraces: vi.fn().mockResolvedValue(undefined),
      purge: vi.fn()
    };

    const payload = createEditableGraphPayload(graph);
    payload.data = await removeTraceWithPlotly(deleteTracePlotly as any, payload, 0);

    const editResult = applyGraphPayloadEdit([graph], [], graph.id, payload);
    expect(editResult.changed).toBe(true);
    expect(editResult.nextGraphs[0].data).toHaveLength(1);
    expect(editResult.nextGraphs[0].data[0].name).toBe("Closed");

    const react = vi.fn().mockResolvedValue(undefined);
    await renderPlotlyChart(
      { react } as any,
      { id: "chart-root" } as unknown as HTMLDivElement,
      editResult.nextGraphs[0].data as any
    );

    const [, renderedData] = react.mock.calls[0];
    expect(renderedData).toEqual([{ type: "bar", name: "Closed", x: ["Q1"], y: [5] }]);
  });
});
