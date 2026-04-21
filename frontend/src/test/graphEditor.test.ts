import {describe, expect, it} from "vitest";
import {LocationGraph} from "../types/Types";
import {
  applyGraphPayloadEdit,
  buildChangedLocationGraphUpdates,
  buildGraphBaselineIndex,
  buildLocationGraphUpdates,
  createEditableGraphPayload,
  getEditableGraphTitle,
  parseEditableGraphPayload,
  pruneDeletedLocationGraphState,
  reconcileLocationGraphRefreshState,
  reconcileLocationGraphs,
  serializeEditableGraphPayload,
  updateEditableGraphTitle,
  undoGraphPayloadEdit
} from "../util/graph/graphEditor";

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
      layout: {title: {text: "Newport Beach", x: 0.02, xanchor: "left"}},
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
    expect(result.nextGraphs[0].layout).toEqual({title: {text: "Updated", x: 0.02, xanchor: "left"}});
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
        layout: {title: {text: "Newport Beach", x: 0.02, xanchor: "left"}},
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
        layout: {title: {text: "Updated", x: 0.02, xanchor: "left"}},
        config: {displayModeBar: false},
        style: {height: 360},
        expectedUpdatedAt: "2026-01-02T00:00:00Z"
      }
    ]);
  });

  it("returns no save payloads when there are no graph changes", () => {
    expect(buildChangedLocationGraphUpdates(baseGraphs, baseGraphs)).toEqual([]);
  });

  it("prunes deleted graphs from the local dashboard caches", () => {
    const baselineIndex = buildGraphBaselineIndex(baseGraphs);
    const cleanupResult = pruneDeletedLocationGraphState(
      baseGraphs,
      [baseGraphs],
      baselineIndex,
      11
    );

    expect(cleanupResult.nextGraphs).toHaveLength(1);
    expect(cleanupResult.nextGraphs[0]).toBe(baseGraphs[0]);
    expect(cleanupResult.nextUndoStack).toEqual([]);
    expect(cleanupResult.nextBaselineIndex.has(11)).toBe(false);
    expect(cleanupResult.nextBaselineIndex.get(10)).toMatchObject({
      expectedUpdatedAt: "2026-01-02T00:00:00Z"
    });
  });

  it("reconciles refreshed graphs without replacing unchanged graph objects", () => {
    const refreshedGraphs: LocationGraph[] = [
      {
        ...baseGraphs[0],
        layout: {title: "Newport Beach"}
      },
      {
        ...baseGraphs[1],
        name: "Updated Non-Compliances"
      },
      {
        ...baseGraphs[0],
        id: 12,
        name: "New Plot",
        createdAt: "2026-01-05T00:00:00Z",
        updatedAt: "2026-01-05T00:00:00Z"
      }
    ];

    const reconciled = reconcileLocationGraphs(baseGraphs, refreshedGraphs);

    expect(reconciled).toHaveLength(3);
    expect(reconciled[0]).toBe(baseGraphs[0]);
    expect(reconciled[1]).toBe(refreshedGraphs[1]);
    expect(reconciled[2]).toBe(refreshedGraphs[2]);
  });

  it("rebases refreshed graph state without discarding local undo history", () => {
    const editedGraphs = applyGraphPayloadEdit(baseGraphs, [], 10, {
      data: [{type: "bar", x: ["A"], y: [15]}],
      layout: {title: {text: "Updated"}},
      config: {displayModeBar: false},
      style: {height: 360}
    }).nextGraphs;
    const currentUndoStack = [baseGraphs];
    const refreshedGraphs: LocationGraph[] = [
      {
        ...baseGraphs[0],
        updatedAt: "2026-01-03T00:00:00Z"
      },
      {
        ...baseGraphs[1],
        name: "Updated Non-Compliances",
        updatedAt: "2026-01-04T00:00:00Z"
      }
    ];

    const refreshResult = reconcileLocationGraphRefreshState(
      editedGraphs,
      currentUndoStack,
      buildGraphBaselineIndex(baseGraphs),
      refreshedGraphs
    );

    expect(refreshResult.nextGraphs[0]).toBe(editedGraphs[0]);
    expect(refreshResult.nextGraphs[1]).toBe(refreshedGraphs[1]);
    expect(refreshResult.nextUndoStack).toHaveLength(1);
    expect(refreshResult.nextUndoStack[0][0]).toEqual(baseGraphs[0]);
    expect(refreshResult.nextUndoStack[0][1]).toEqual(refreshedGraphs[1]);
    expect(refreshResult.nextBaselineIndex.get(10)).toMatchObject({
      expectedUpdatedAt: "2026-01-02T00:00:00Z"
    });
    expect(refreshResult.nextBaselineIndex.get(11)).toMatchObject({
      expectedUpdatedAt: "2026-01-04T00:00:00Z"
    });
  });

  it("reads graph titles from string and object Plotly layout shapes", () => {
    expect(getEditableGraphTitle({title: "Legacy title"})).toBe("Legacy title");
    expect(getEditableGraphTitle({title: {text: "Current title", x: 0.02}})).toBe("Current title");
    expect(getEditableGraphTitle({showlegend: false})).toBe("");
  });

  it("updates graph titles while preserving existing layout title metadata", () => {
    expect(updateEditableGraphTitle({showlegend: false}, "Updated title")).toEqual({
      showlegend: false,
      title: {text: "Updated title", x: 0.02, xanchor: "left"}
    });

    expect(updateEditableGraphTitle({title: {text: "Old title", x: 0.5, y: 0.9}}, "Next title")).toEqual({
      title: {text: "Next title", x: 0.02, xanchor: "left", y: 0.9}
    });
  });

  it("removes the graph title when the draft is cleared", () => {
    expect(updateEditableGraphTitle({title: {text: "Old"}, showlegend: false}, "   ")).toEqual({
      showlegend: false
    });
    expect(updateEditableGraphTitle({title: {text: "Old"}}, "")).toBeNull();
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
