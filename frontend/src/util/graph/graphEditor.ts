import {LocationGraph, LocationGraphUpdate} from "../../types/Types";
import {normalizePlotlyLayoutTitle} from "./graphLayoutTitle";
import {applyStateSnapshot, undoStateSnapshot} from "../common/stateHistory";

export type EditableGraphPayload = {
  data: Record<string, unknown>[];
  layout?: Record<string, unknown> | null;
  config?: Record<string, unknown> | null;
  style?: Record<string, unknown> | null;
};

type GraphEditResult = {
  nextGraphs: LocationGraph[];
  nextUndoStack: LocationGraph[][];
  changed: boolean;
};

type GraphUndoResult = {
  nextGraphs: LocationGraph[];
  nextUndoStack: LocationGraph[][];
  undone: boolean;
};

export type GraphBaselineEntry = {
  payloadSignature: string;
  expectedUpdatedAt: string | null;
};

export type DeletedGraphCleanupResult = {
  nextGraphs: LocationGraph[];
  nextUndoStack: LocationGraph[][];
  nextBaselineIndex: Map<number, GraphBaselineEntry>;
};

const isRecord = (value: unknown): value is Record<string, unknown> =>
  value !== null && typeof value === "object" && !Array.isArray(value);

const cloneJson = <T>(value: T): T =>
  JSON.parse(JSON.stringify(value)) as T;

const parseGraphDataEntries = (value: unknown): Record<string, unknown>[] => {
  if (!Array.isArray(value) || value.some((entry) => !isRecord(entry))) {
    throw new Error("Graph payload field \"data\" must be an array of objects.");
  }
  return value;
};

const parseOptionalGraphObject = (
  value: unknown,
  fieldName: "layout" | "config" | "style"
): Record<string, unknown> | null => {
  if (value === null || value === undefined) {
    return null;
  }
  if (!isRecord(value)) {
    throw new Error(`Graph payload field "${fieldName}" must be an object or null.`);
  }
  return value;
};

const normalizeEditableLayout = (
  layout: Record<string, unknown> | null | undefined
): Record<string, unknown> | null => {
  if (!isRecord(layout)) {
    return null;
  }

  const nextLayout = cloneJson(layout);
  if ("title" in nextLayout) {
    nextLayout.title = normalizePlotlyLayoutTitle(nextLayout.title);
  }
  return nextLayout;
};

export const getEditableGraphTitle = (
  layout: Record<string, unknown> | null | undefined
): string => {
  if (!isRecord(layout)) {
    return "";
  }

  const title = layout.title;
  if (typeof title === "string") {
    return title;
  }
  if (isRecord(title) && typeof title.text === "string") {
    return title.text;
  }
  return "";
};

export const updateEditableGraphTitle = (
  layout: Record<string, unknown> | null | undefined,
  rawTitle: string
): Record<string, unknown> | null => {
  const normalizedTitle = rawTitle.trim();
  const nextLayout = normalizeEditableLayout(layout) ?? {};

  if (!normalizedTitle) {
    delete nextLayout.title;
    return Object.keys(nextLayout).length > 0 ? nextLayout : null;
  }

  nextLayout.title = normalizePlotlyLayoutTitle({
    ...(isRecord(nextLayout.title) ? nextLayout.title : {}),
    text: normalizedTitle
  });

  return nextLayout;
};

const normalizeGraphPayload = (payload: EditableGraphPayload): EditableGraphPayload => ({
  data: payload.data,
  layout: normalizeEditableLayout(payload.layout),
  config: payload.config ?? null,
  style: payload.style ?? null
});

const graphPayloadSignature = (payload: EditableGraphPayload): string =>
  JSON.stringify(normalizeGraphPayload(payload));

function graphStateSignature(graph: LocationGraph): string {
  return JSON.stringify({
    name: graph.name,
    payload: normalizeGraphPayload({
      data: graph.data,
      layout: graph.layout ?? null,
      config: graph.config ?? null,
      style: graph.style ?? null
    })
  });
}

const hasOwnKey = <T extends string>(
  value: Record<string, unknown>,
  key: T
): value is Record<T, unknown> =>
  Object.prototype.hasOwnProperty.call(value, key);

export const createEditableGraphPayload = (graph: LocationGraph): EditableGraphPayload =>
  normalizeGraphPayload(cloneJson({
    data: graph.data,
    layout: graph.layout ?? null,
    config: graph.config ?? null,
    style: graph.style ?? null
  }));

export const serializeEditableGraphPayload = (payload: EditableGraphPayload): string =>
  JSON.stringify(normalizeGraphPayload(payload), null, 2);

export const parseEditableGraphPayload = (rawPayload: string): EditableGraphPayload => {
  let parsedPayload: unknown;
  try {
    parsedPayload = JSON.parse(rawPayload);
  } catch {
    throw new Error("Graph JSON is invalid.");
  }

  if (!isRecord(parsedPayload)) {
    throw new Error("Graph JSON must be an object.");
  }

  return {
    data: parseGraphDataEntries(parsedPayload.data),
    layout: normalizeEditableLayout(parseOptionalGraphObject(
      hasOwnKey(parsedPayload, "layout") ? parsedPayload.layout : null,
      "layout"
    )),
    config: parseOptionalGraphObject(
      hasOwnKey(parsedPayload, "config") ? parsedPayload.config : null,
      "config"
    ),
    style: parseOptionalGraphObject(
      hasOwnKey(parsedPayload, "style") ? parsedPayload.style : null,
      "style"
    )
  };
};

export const cloneLocationGraphs = (graphs: LocationGraph[]): LocationGraph[] =>
  cloneJson(graphs);

export const reconcileLocationGraphs = (
  currentGraphs: LocationGraph[],
  nextGraphs: LocationGraph[]
): LocationGraph[] => {
  if (currentGraphs.length === 0) {
    return cloneLocationGraphs(nextGraphs);
  }

  const currentById = new Map(currentGraphs.map((graph) => [graph.id, graph]));
  return nextGraphs.map((nextGraph) => {
    const currentGraph = currentById.get(nextGraph.id);
    if (!currentGraph) {
      return nextGraph;
    }
    if (graphStateSignature(currentGraph) === graphStateSignature(nextGraph)) {
      return currentGraph;
    }
    return nextGraph;
  });
};

export const buildLocationGraphUpdates = (graphs: LocationGraph[]): LocationGraphUpdate[] =>
  graphs.map((graph) => ({
    graphId: graph.id,
    ...createEditableGraphPayload(graph)
  }));

export const buildGraphBaselineIndex = (graphs: LocationGraph[]): Map<number, GraphBaselineEntry> => {
  const baselineById = new Map<number, GraphBaselineEntry>();
  for (const graph of graphs) {
    baselineById.set(graph.id, {
      payloadSignature: graphPayloadSignature(createEditableGraphPayload(graph)),
      expectedUpdatedAt: graph.updatedAt ?? null
    });
  }
  return baselineById;
};

export const pruneDeletedLocationGraphState = (
  currentGraphs: LocationGraph[],
  currentUndoStack: LocationGraph[][],
  currentBaselineIndex: Map<number, GraphBaselineEntry>,
  deletedGraphId: number
): DeletedGraphCleanupResult => {
  const nextGraphs = currentGraphs.filter((graph) => graph.id !== deletedGraphId);
  const nextBaselineIndex = new Map(currentBaselineIndex);
  nextBaselineIndex.delete(deletedGraphId);

  // Deleting a graph invalidates any local undo history that still references it.
  // The delete action is blocked while there are pending edits, so clearing the
  // stack here is a safe reset of stale client state.
  return {
    nextGraphs,
    nextUndoStack: currentUndoStack.length > 0 ? [] : currentUndoStack,
    nextBaselineIndex
  };
};

export const buildChangedLocationGraphUpdates = (
  currentGraphs: LocationGraph[],
  baselineSource: LocationGraph[] | Map<number, GraphBaselineEntry>
): LocationGraphUpdate[] => {
  const baselineById =
    baselineSource instanceof Map ? baselineSource : buildGraphBaselineIndex(baselineSource);

  const changedUpdates: LocationGraphUpdate[] = [];
  for (const currentGraph of currentGraphs) {
    const baseline = baselineById.get(currentGraph.id);
    const currentSignature = graphPayloadSignature(createEditableGraphPayload(currentGraph));
    if (baseline && baseline.payloadSignature === currentSignature) {
      continue;
    }

    changedUpdates.push({
      graphId: currentGraph.id,
      ...createEditableGraphPayload(currentGraph),
      expectedUpdatedAt: baseline?.expectedUpdatedAt ?? null
    });
  }

  return changedUpdates;
};

export const applyGraphPayloadEdit = (
  currentGraphs: LocationGraph[],
  undoStack: LocationGraph[][],
  graphId: number,
  nextPayload: EditableGraphPayload
): GraphEditResult => {
  const normalizedNextPayload = normalizeGraphPayload(nextPayload);
  const currentGraph = currentGraphs.find((graph) => graph.id === graphId);
  if (!currentGraph) {
    return {
      nextGraphs: currentGraphs,
      nextUndoStack: undoStack,
      changed: false
    };
  }

  if (graphPayloadSignature(createEditableGraphPayload(currentGraph)) === graphPayloadSignature(normalizedNextPayload)) {
    return {
      nextGraphs: currentGraphs,
      nextUndoStack: undoStack,
      changed: false
    };
  }

  const nextGraphs = currentGraphs.map((graph) =>
    graph.id === graphId
      ? {
          ...graph,
          data: cloneJson(normalizedNextPayload.data),
          layout: cloneJson(normalizedNextPayload.layout ?? null),
          config: cloneJson(normalizedNextPayload.config ?? null),
          style: cloneJson(normalizedNextPayload.style ?? null)
        }
      : graph
  );

  const result = applyStateSnapshot(
    currentGraphs,
    undoStack,
    nextGraphs,
    cloneLocationGraphs
  );

  return {
    nextGraphs: result.nextState,
    nextUndoStack: result.nextUndoStack,
    changed: result.changed
  };
};

export const undoGraphPayloadEdit = (
  currentGraphs: LocationGraph[],
  undoStack: LocationGraph[][]
): GraphUndoResult => {
  const result = undoStateSnapshot(currentGraphs, undoStack, cloneLocationGraphs);
  return {
    nextGraphs: result.nextState,
    nextUndoStack: result.nextUndoStack,
    undone: result.undone
  };
};
