import {LocationGraph, LocationGraphUpdate} from "../types/Types";

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

const normalizeGraphPayload = (payload: EditableGraphPayload): EditableGraphPayload => ({
  data: payload.data,
  layout: payload.layout ?? null,
  config: payload.config ?? null,
  style: payload.style ?? null
});

const graphPayloadSignature = (payload: EditableGraphPayload): string =>
  JSON.stringify(normalizeGraphPayload(payload));

const hasOwnKey = <T extends string>(
  value: Record<string, unknown>,
  key: T
): value is Record<T, unknown> =>
  Object.prototype.hasOwnProperty.call(value, key);

export const createEditableGraphPayload = (graph: LocationGraph): EditableGraphPayload =>
  cloneJson({
    data: graph.data,
    layout: graph.layout ?? null,
    config: graph.config ?? null,
    style: graph.style ?? null
  });

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
    layout: parseOptionalGraphObject(
      hasOwnKey(parsedPayload, "layout") ? parsedPayload.layout : null,
      "layout"
    ),
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

export const buildLocationGraphUpdates = (graphs: LocationGraph[]): LocationGraphUpdate[] =>
  graphs.map((graph) => ({
    graphId: graph.id,
    ...createEditableGraphPayload(graph)
  }));

export const applyGraphPayloadEdit = (
  currentGraphs: LocationGraph[],
  undoStack: LocationGraph[][],
  graphId: number,
  nextPayload: EditableGraphPayload
): GraphEditResult => {
  const currentGraph = currentGraphs.find((graph) => graph.id === graphId);
  if (!currentGraph) {
    return {
      nextGraphs: currentGraphs,
      nextUndoStack: undoStack,
      changed: false
    };
  }

  if (graphPayloadSignature(createEditableGraphPayload(currentGraph)) === graphPayloadSignature(nextPayload)) {
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
          data: cloneJson(nextPayload.data),
          layout: cloneJson(nextPayload.layout ?? null),
          config: cloneJson(nextPayload.config ?? null),
          style: cloneJson(nextPayload.style ?? null)
        }
      : graph
  );

  return {
    nextGraphs,
    nextUndoStack: [...undoStack, cloneLocationGraphs(currentGraphs)],
    changed: true
  };
};

export const undoGraphPayloadEdit = (
  currentGraphs: LocationGraph[],
  undoStack: LocationGraph[][]
): GraphUndoResult => {
  if (undoStack.length === 0) {
    return {
      nextGraphs: currentGraphs,
      nextUndoStack: undoStack,
      undone: false
    };
  }

  const previousGraphs = undoStack[undoStack.length - 1];
  return {
    nextGraphs: cloneLocationGraphs(previousGraphs),
    nextUndoStack: undoStack.slice(0, -1),
    undone: true
  };
};
