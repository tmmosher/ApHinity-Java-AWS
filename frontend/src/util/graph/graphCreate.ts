import type {LocationGraph, LocationGraphType, LocationGraphUpdate} from "../../types/Types";

const DEFAULT_SCATTER_GRAPH_Y_AXIS_TITLE = "% Compliance";

type GraphCreateModalRequest = {
  graphType: LocationGraphType;
  sectionId?: number;
  createNewSection: boolean;
  yAxisTitle?: string;
};

type CreateLocationGraphApiRequest = {
  sectionId?: number;
  createNewSection?: boolean;
  graphType: LocationGraphType;
};

const isRecord = (value: unknown): value is Record<string, unknown> =>
  value !== null && typeof value === "object" && !Array.isArray(value);

const cloneJson = <T>(value: T): T =>
  JSON.parse(JSON.stringify(value)) as T;

export const normalizeGraphCreateYAxisTitle = (rawTitle: string | undefined): string => {
  const normalizedTitle = rawTitle?.trim() ?? "";
  return normalizedTitle || DEFAULT_SCATTER_GRAPH_Y_AXIS_TITLE;
};

export const buildCreateLocationGraphRequest = (
  request: GraphCreateModalRequest
): CreateLocationGraphApiRequest => ({
  graphType: request.graphType,
  sectionId: request.sectionId,
  createNewSection: request.createNewSection
});

export const getGraphYAxisTitle = (
  layout: Record<string, unknown> | null | undefined
): string => {
  if (!isRecord(layout) || !isRecord(layout.yaxis)) {
    return "";
  }

  const title = layout.yaxis.title;
  if (typeof title === "string") {
    return title;
  }
  if (isRecord(title) && typeof title.text === "string") {
    return title.text;
  }
  return "";
};

export const updateGraphYAxisTitle = (
  layout: Record<string, unknown> | null | undefined,
  rawTitle: string | undefined
): Record<string, unknown> => {
  const nextLayout = isRecord(layout) ? cloneJson(layout) : {};
  const nextYAxis = isRecord(nextLayout.yaxis) ? {...nextLayout.yaxis} : {};
  const normalizedTitle = normalizeGraphCreateYAxisTitle(rawTitle);

  nextYAxis.title = isRecord(nextYAxis.title)
    ? {
        ...nextYAxis.title,
        text: normalizedTitle
      }
    : normalizedTitle;

  nextLayout.yaxis = nextYAxis;
  return nextLayout;
};

export const buildPostCreateGraphUpdate = (
  graph: LocationGraph,
  request: GraphCreateModalRequest
): LocationGraphUpdate | null => {
  if (request.graphType !== "scatter") {
    return null;
  }

  const normalizedTitle = normalizeGraphCreateYAxisTitle(request.yAxisTitle);
  if (getGraphYAxisTitle(graph.layout) === normalizedTitle) {
    return null;
  }

  return {
    graphId: graph.id,
    data: cloneJson(graph.data),
    layout: updateGraphYAxisTitle(graph.layout, normalizedTitle),
    config: cloneJson(graph.config ?? null),
    style: cloneJson(graph.style ?? null),
    expectedUpdatedAt: graph.updatedAt ?? null
  };
};

export {DEFAULT_SCATTER_GRAPH_Y_AXIS_TITLE};
export type {GraphCreateModalRequest};
