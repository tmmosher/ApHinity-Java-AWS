import {describe, expect, it} from "vitest";
import {LocationGraph} from "../types/Types";
import {
  applyGraphPayloadEdit,
  buildChangedLocationGraphUpdates,
  buildGraphBaselineIndex,
  buildLocationGraphUpdates,
  createEditableGraphPayload,
  parseEditableGraphPayload,
  serializeEditableGraphPayload,
  undoGraphPayloadEdit
} from "../util/graphEditor";

const baseGraphs: LocationGraph[] = [
  {
    id: 10,
    name: "Water Quality Compliance",
    data: [{type: "bar", x: ["A"], y: [9]}],
    layout: {title: {text: "Newport Beach"}},
    config: {displayModeBar: false},
    style: {height: 320},
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-02T00:00:00Z"
  },
  {
    id: 11,
    name: "Non-Compliances",
    data: [{type: "pie", labels: ["Open"], values: [3]}],
    layout: {showlegend: false},
    config: {displayModeBar: false},
    style: {height: 300},
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-02T00:00:00Z"
  }
];

describe("graphEditor", () => {
  it("serializes and parses editable payload JSON", () => {
    const payload = createEditableGraphPayload(baseGraphs[0]);
    const parsed = parseEditableGraphPayload(serializeEditableGraphPayload(payload));

    expect(parsed).toEqual({
      data: [{type: "bar", x: ["A"], y: [9]}],
      layout: {title: {text: "Newport Beach"}},
      config: {displayModeBar: false},
      style: {height: 320}
    });
  });

  it("rejects invalid graph JSON payloads", () => {
    expect(() => parseEditableGraphPayload("not-json")).toThrowError("Graph JSON is invalid.");
    expect(() => parseEditableGraphPayload(JSON.stringify({data: "bad"}))).toThrowError(
      "Graph payload field \"data\" must be an array of objects."
    );
  });

  it("applies graph edits and pushes a location-level undo snapshot", () => {
    const result = applyGraphPayloadEdit(baseGraphs, [], 10, {
      data: [{type: "bar", x: ["A"], y: [11]}],
      layout: {title: {text: "Updated"}},
      config: {displayModeBar: false},
      style: {height: 340}
    });

    expect(result.changed).toBe(true);
    expect(result.nextUndoStack).toHaveLength(1);
    expect(result.nextGraphs[0].data).toEqual([{type: "bar", x: ["A"], y: [11]}]);
    expect(result.nextGraphs[0].layout).toEqual({title: {text: "Updated"}});
  });

  it("does not push undo snapshots when payload is unchanged", () => {
    const unchangedPayload = createEditableGraphPayload(baseGraphs[0]);
    const result = applyGraphPayloadEdit(baseGraphs, [], 10, unchangedPayload);

    expect(result.changed).toBe(false);
    expect(result.nextUndoStack).toEqual([]);
    expect(result.nextGraphs).toBe(baseGraphs);
  });

  it("undoes the most recent location-level graph edit", () => {
    const edited = applyGraphPayloadEdit(baseGraphs, [], 11, {
      data: [{type: "pie", labels: ["Open"], values: [7]}],
      layout: {showlegend: false},
      config: {displayModeBar: false},
      style: {height: 300}
    });

    const undone = undoGraphPayloadEdit(edited.nextGraphs, edited.nextUndoStack);

    expect(undone.undone).toBe(true);
    expect(undone.nextUndoStack).toEqual([]);
    expect(undone.nextGraphs).toEqual(baseGraphs);
  });

  it("builds save payloads for the graph update API", () => {
    expect(buildLocationGraphUpdates(baseGraphs)).toEqual([
      {
        graphId: 10,
        data: [{type: "bar", x: ["A"], y: [9]}],
        layout: {title: {text: "Newport Beach"}},
        config: {displayModeBar: false},
        style: {height: 320}
      },
      {
        graphId: 11,
        data: [{type: "pie", labels: ["Open"], values: [3]}],
        layout: {showlegend: false},
        config: {displayModeBar: false},
        style: {height: 300}
      }
    ]);
  });

  it("builds save payloads only for changed graphs with expectedUpdatedAt values", () => {
    const editedGraphs = applyGraphPayloadEdit(baseGraphs, [], 10, {
      data: [{type: "bar", x: ["A"], y: [15]}],
      layout: {title: {text: "Updated"}},
      config: {displayModeBar: false},
      style: {height: 360}
    }).nextGraphs;

    expect(buildChangedLocationGraphUpdates(editedGraphs, baseGraphs)).toEqual([
      {
        graphId: 10,
        data: [{type: "bar", x: ["A"], y: [15]}],
        layout: {title: {text: "Updated"}},
        config: {displayModeBar: false},
        style: {height: 360},
        expectedUpdatedAt: "2026-01-02T00:00:00Z"
      }
    ]);
  });

  it("returns no save payloads when there are no graph changes", () => {
    expect(buildChangedLocationGraphUpdates(baseGraphs, baseGraphs)).toEqual([]);
  });

  it("uses precomputed baseline signatures when building changed graph updates", () => {
    const editedGraphs = applyGraphPayloadEdit(baseGraphs, [], 11, {
      data: [{type: "pie", labels: ["Open"], values: [5]}],
      layout: {showlegend: true},
      config: {displayModeBar: false},
      style: {height: 300}
    }).nextGraphs;

    const baseline = buildGraphBaselineIndex(baseGraphs);
    expect(buildChangedLocationGraphUpdates(editedGraphs, baseline)).toEqual([
      {
        graphId: 11,
        data: [{type: "pie", labels: ["Open"], values: [5]}],
        layout: {showlegend: true},
        config: {displayModeBar: false},
        style: {height: 300},
        expectedUpdatedAt: "2026-01-02T00:00:00Z"
      }
    ]);
  });
});
