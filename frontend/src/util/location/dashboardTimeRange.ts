import type {LocationGraph, LocationGraphTimeRange} from "../../types/Types";

export type DashboardTimeRange = LocationGraphTimeRange;

export const dashboardTimeRangeOptions: Array<{
  value: DashboardTimeRange;
  label: string;
  description: string;
}> = [
  {
    value: "threeMonths",
    label: "3 Months",
    description: "Recent quarter"
  },
  {
    value: "twelveMonths",
    label: "12 Months",
    description: "Rolling year"
  },
  {
    value: "allTime",
    label: "All Data",
    description: "Full history"
  }
];

const isGraphDataList = (value: unknown): value is Record<string, unknown>[] =>
  Array.isArray(value) && value.every((entry) => entry !== null && typeof entry === "object" && !Array.isArray(entry));

const ISO_DATE_PATTERN = /^(\d{4})-(\d{1,2})-(\d{1,2})$/;

const isRecord = (value: unknown): value is Record<string, unknown> =>
  value !== null && typeof value === "object" && !Array.isArray(value);

const normalizeIsoDateValue = (value: unknown): string | null => {
  if (typeof value !== "string") {
    return null;
  }
  const normalized = value.trim();
  const match = ISO_DATE_PATTERN.exec(normalized);
  if (!match) {
    return null;
  }

  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  if (!Number.isInteger(year) || !Number.isInteger(month) || !Number.isInteger(day)) {
    return null;
  }

  const parsed = new Date(Date.UTC(year, month - 1, day));
  if (
    Number.isNaN(parsed.getTime()) ||
    parsed.getUTCFullYear() !== year ||
    parsed.getUTCMonth() + 1 !== month ||
    parsed.getUTCDate() !== day
  ) {
    return null;
  }

  return [
    String(year).padStart(4, "0"),
    String(month).padStart(2, "0"),
    String(day).padStart(2, "0")
  ].join("-");
};

const addUtcDays = (isoDate: string, days: number): string => {
  const [year, month, day] = isoDate.split("-").map(Number);
  const nextDate = new Date(Date.UTC(year, month - 1, day + days));
  return [
    String(nextDate.getUTCFullYear()).padStart(4, "0"),
    String(nextDate.getUTCMonth() + 1).padStart(2, "0"),
    String(nextDate.getUTCDate()).padStart(2, "0")
  ].join("-");
};

const resolveAllTimeGraphData = (graph: LocationGraph): Record<string, unknown>[] => graph.data;

const findMatchingSourceTrace = (
  sourceData: Record<string, unknown>[],
  selectedTrace: Record<string, unknown>,
  traceIndex: number
): Record<string, unknown> | null => {
  const sourceByIndex = sourceData[traceIndex];
  if (
    isRecord(sourceByIndex) &&
    sourceByIndex.type === selectedTrace.type
  ) {
    return sourceByIndex;
  }

  const sourceByIdentity = sourceData.find((candidate) =>
    isRecord(candidate) &&
    candidate.type === selectedTrace.type &&
    candidate.name === selectedTrace.name
  );
  return sourceByIdentity ?? null;
};

const prependContextPointToTrace = (
  selectedTrace: Record<string, unknown>,
  sourceTrace: Record<string, unknown>
): {
  trace: Record<string, unknown>;
  selectedWindow: {start: string; end: string} | null;
  hasContextPoint: boolean;
} => {
  const selectedX = Array.isArray(selectedTrace.x) ? selectedTrace.x : null;
  if (!selectedX || selectedX.length === 0) {
    return {trace: selectedTrace, selectedWindow: null, hasContextPoint: false};
  }

  const normalizedSelectedDates = selectedX
    .map(normalizeIsoDateValue)
    .filter((value): value is string => value !== null);
  if (normalizedSelectedDates.length !== selectedX.length) {
    return {trace: selectedTrace, selectedWindow: null, hasContextPoint: false};
  }

  const selectedWindow = {
    start: normalizedSelectedDates.reduce((earliest, value) => earliest.localeCompare(value) <= 0 ? earliest : value),
    end: normalizedSelectedDates.reduce((latest, value) => latest.localeCompare(value) >= 0 ? latest : value)
  };

  const sourceX = Array.isArray(sourceTrace.x) ? sourceTrace.x : null;
  if (!sourceX || sourceX.length === 0) {
    return {trace: selectedTrace, selectedWindow, hasContextPoint: false};
  }

  let previousPointIndex = -1;
  for (let index = 0; index < sourceX.length; index += 1) {
    const normalizedDate = normalizeIsoDateValue(sourceX[index]);
    if (normalizedDate !== null && normalizedDate.localeCompare(selectedWindow.start) < 0) {
      previousPointIndex = index;
    }
  }

  if (previousPointIndex < 0) {
    return {trace: selectedTrace, selectedWindow, hasContextPoint: false};
  }

  const nextTrace: Record<string, unknown> = {...selectedTrace};
  let changed = false;
  for (const [key, value] of Object.entries(selectedTrace)) {
    if (!Array.isArray(value) || value.length !== selectedX.length) {
      continue;
    }

    const sourceValue = Array.isArray(sourceTrace[key]) ? sourceTrace[key] as unknown[] : null;
    nextTrace[key] = [
      sourceValue && sourceValue.length > previousPointIndex ? sourceValue[previousPointIndex] : null,
      ...value
    ];
    changed = true;
  }

  return {
    trace: changed ? nextTrace : selectedTrace,
    selectedWindow,
    hasContextPoint: changed
  };
};

const buildTimeRangeDisplayData = (
  graph: LocationGraph,
  timeRange: DashboardTimeRange,
  resolvedData: Record<string, unknown>[]
): {
  data: Record<string, unknown>[];
  selectedWindow: {start: string; end: string} | null;
} => {
  if (timeRange === "allTime" || resolvedData === graph.data) {
    return {data: resolvedData, selectedWindow: null};
  }

  const allTimeData = resolveAllTimeGraphData(graph);
  let changed = false;
  let earliestWindowStart: string | null = null;
  let latestWindowEnd: string | null = null;

  const displayData = resolvedData.map((trace, traceIndex) => {
    if (!isRecord(trace)) {
      return trace;
    }

    const traceType = String(trace.type ?? "").toLowerCase();
    if (traceType !== "scatter" && traceType !== "scattergl") {
      return trace;
    }

    const sourceTrace = findMatchingSourceTrace(allTimeData, trace, traceIndex);
    if (!sourceTrace) {
      return trace;
    }

    const contextualized = prependContextPointToTrace(trace, sourceTrace);
    if (contextualized.hasContextPoint && contextualized.selectedWindow) {
      earliestWindowStart = earliestWindowStart === null || contextualized.selectedWindow.start.localeCompare(earliestWindowStart) < 0
        ? contextualized.selectedWindow.start
        : earliestWindowStart;
      latestWindowEnd = latestWindowEnd === null || contextualized.selectedWindow.end.localeCompare(latestWindowEnd) > 0
        ? contextualized.selectedWindow.end
        : latestWindowEnd;
    }
    if (contextualized.trace !== trace) {
      changed = true;
    }
    return contextualized.trace;
  });

  return {
    data: changed ? displayData : resolvedData,
    selectedWindow: earliestWindowStart && latestWindowEnd
      ? {start: earliestWindowStart, end: latestWindowEnd}
      : null
  };
};

const applySelectedDateWindowToLayout = (
  layout: LocationGraph["layout"],
  selectedWindow: {start: string; end: string} | null
): LocationGraph["layout"] => {
  if (!selectedWindow) {
    return layout;
  }

  const nextEnd = selectedWindow.start === selectedWindow.end
    ? addUtcDays(selectedWindow.end, 1)
    : selectedWindow.end;
  const nextXAxis = {
    ...(isRecord(layout?.xaxis) ? layout.xaxis : {}),
    range: [selectedWindow.start, nextEnd],
    autorange: false
  };
  return {
    ...(isRecord(layout) ? layout : {}),
    xaxis: nextXAxis
  };
};

export const resolveLocationGraphDataForTimeRange = (
  graph: LocationGraph,
  timeRange: DashboardTimeRange
): Record<string, unknown>[] => {
  if (timeRange === "allTime") {
    return graph.data;
  }

  const rangedData = graph.timeRangeData?.[timeRange];
  if (isGraphDataList(rangedData)) {
    return rangedData;
  }
  return graph.data;
};

export const materializeLocationGraphForTimeRange = (
  graph: LocationGraph,
  timeRange: DashboardTimeRange
): LocationGraph => {
  const resolvedData = resolveLocationGraphDataForTimeRange(graph, timeRange);
  const display = buildTimeRangeDisplayData(graph, timeRange, resolvedData);
  const nextLayout = applySelectedDateWindowToLayout(graph.layout, display.selectedWindow);
  if (display.data === graph.data && nextLayout === graph.layout) {
    return graph;
  }
  return {
    ...graph,
    data: display.data,
    layout: nextLayout
  };
};

export const reconcileLocationGraphUploadState = (
  currentGraphs: LocationGraph[],
  uploadedGraphs: LocationGraph[]
): LocationGraph[] => {
  if (uploadedGraphs.length === 0) {
    return currentGraphs;
  }

  const uploadedGraphById = new Map(uploadedGraphs.map((graph) => [graph.id, graph]));
  let changed = false;

  const nextGraphs = currentGraphs.map((currentGraph) => {
    const uploadedGraph = uploadedGraphById.get(currentGraph.id);
    if (!uploadedGraph) {
      return currentGraph;
    }

    changed = true;
    return {
      ...currentGraph,
      ...uploadedGraph,
      data: uploadedGraph.data,
      timeRangeData: uploadedGraph.timeRangeData,
      layout: uploadedGraph.layout,
      config: uploadedGraph.config,
      style: uploadedGraph.style,
      updatedAt: uploadedGraph.updatedAt
    };
  });

  return changed ? nextGraphs : currentGraphs;
};

export const cloneLocationGraphs = (graphs: LocationGraph[]): LocationGraph[] =>
  graphs.map((graph) => ({
    ...graph,
    data: structuredClone(graph.data),
    timeRangeData: structuredClone(graph.timeRangeData),
    layout: structuredClone(graph.layout),
    config: structuredClone(graph.config),
    style: structuredClone(graph.style)
  }));
