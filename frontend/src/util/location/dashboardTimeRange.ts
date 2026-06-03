import type {LocationGraph, LocationGraphTimeRange} from "../../types/Types";

export type DashboardTimeRange = LocationGraphTimeRange;

export const dashboardTimeRangeOptions: Array<{
  value: DashboardTimeRange;
  label: string;
}> = [
  {
    value: "threeMonths",
    label: "3 Months",
  },
  {
    value: "twelveMonths",
    label: "12 Months",
  },
  {
    value: "allTime",
    label: "All Data",
  }
];

const ISO_DATE_PATTERN = /^(\d{4})-(\d{1,2})-(\d{1,2})$/;
const RANGE_CONTEXT_MONTHS = 1;
const DISPLAY_MARGIN_DAYS = 7;

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

const getCurrentIsoDate = (): string => {
  const currentDate = new Date();
  return [
    String(currentDate.getFullYear()).padStart(4, "0"),
    String(currentDate.getMonth() + 1).padStart(2, "0"),
    String(currentDate.getDate()).padStart(2, "0")
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

const addUtcMonths = (isoDate: string, months: number): string => {
  const [year, month, day] = isoDate.split("-").map(Number);
  const monthStart = new Date(Date.UTC(year, month - 1 + months, 1));
  const targetYear = monthStart.getUTCFullYear();
  const targetMonth = monthStart.getUTCMonth();
  const lastDayOfTargetMonth = new Date(Date.UTC(targetYear, targetMonth + 1, 0)).getUTCDate();
  const nextDate = new Date(Date.UTC(targetYear, targetMonth, Math.min(day, lastDayOfTargetMonth)));
  return [
    String(nextDate.getUTCFullYear()).padStart(4, "0"),
    String(nextDate.getUTCMonth() + 1).padStart(2, "0"),
    String(nextDate.getUTCDate()).padStart(2, "0")
  ].join("-");
};

const firstDayOfMonth = (isoDate: string): string => {
  const [year, month] = isoDate.split("-").map(Number);
  return [
    String(year).padStart(4, "0"),
    String(month).padStart(2, "0"),
    "01"
  ].join("-");
};

const resolveSelectedWindow = (
  timeRange: DashboardTimeRange,
  anchorDate: string = getCurrentIsoDate()
): {start: string; end: string} | null => {
  const normalizedAnchorDate = normalizeIsoDateValue(anchorDate);
  if (timeRange === "allTime" || normalizedAnchorDate === null) {
    return null;
  }

  if (timeRange === "threeMonths") {
    return {
      start: firstDayOfMonth(addUtcMonths(normalizedAnchorDate, -3)),
      end: normalizedAnchorDate
    };
  }

  return {
    start: addUtcMonths(normalizedAnchorDate, -12),
    end: normalizedAnchorDate
  };
};

const isTimeSeriesTrace = (trace: Record<string, unknown>): boolean => {
  const traceType = String(trace.type ?? "").trim().toLowerCase();
  const xValues = Array.isArray(trace.x) ? trace.x : [];
  return (traceType === "scatter" || traceType === "scattergl")
    && xValues.length > 0
    && xValues.every((value) => normalizeIsoDateValue(value) !== null);
};

const filterTraceToWindow = (
  trace: Record<string, unknown>,
  dataWindow: {start: string; end: string}
): Record<string, unknown> => {
  if (!isTimeSeriesTrace(trace)) {
    return trace;
  }

  const xValues = Array.isArray(trace.x) ? trace.x : [];
  const sortedIncludedIndices = xValues
    .map((value, index) => ({
      index,
      date: normalizeIsoDateValue(value)
    }))
    .filter((entry): entry is {index: number; date: string} =>
      entry.date !== null
      && entry.date.localeCompare(dataWindow.start) >= 0
      && entry.date.localeCompare(dataWindow.end) <= 0
    )
    .sort((left, right) => left.date.localeCompare(right.date));

  const nextTrace: Record<string, unknown> = {};
  let changed = false;
  for (const [key, value] of Object.entries(trace)) {
    if (!Array.isArray(value) || value.length !== xValues.length) {
      nextTrace[key] = value;
      continue;
    }

    const nextValues = sortedIncludedIndices.map(({index}) => value[index]);
    nextTrace[key] = nextValues;
    changed = changed
      || nextValues.length !== value.length
      || nextValues.some((nextValue, index) => nextValue !== value[index]);
  }

  return changed ? nextTrace : trace;
};

const buildTimeRangeDisplayData = (
  graph: LocationGraph,
  timeRange: DashboardTimeRange
): {
  data: Record<string, unknown>[];
  selectedWindow: {start: string; end: string} | null;
} => {
  const selectedWindow = resolveSelectedWindow(timeRange);
  if (selectedWindow === null) {
    return {data: graph.data, selectedWindow: null};
  }

  const dataWindow = {
    start: addUtcMonths(selectedWindow.start, -RANGE_CONTEXT_MONTHS),
    end: selectedWindow.end
  };
  const displayData = graph.data.map((trace) =>
    isRecord(trace) ? filterTraceToWindow(trace, dataWindow) : trace
  );
  const changed = displayData.some((trace, index) => trace !== graph.data[index]);

  return {
    data: changed ? displayData : graph.data,
    selectedWindow
  };
};

const applySelectedDateWindowToLayout = (
  layout: LocationGraph["layout"],
  selectedWindow: {start: string; end: string} | null
): LocationGraph["layout"] => {
  if (!selectedWindow) {
    return layout;
  }

  const nextXAxis = {
    ...(isRecord(layout?.xaxis) ? layout.xaxis : {}),
    range: [
      addUtcDays(selectedWindow.start, -DISPLAY_MARGIN_DAYS),
      addUtcDays(selectedWindow.end, DISPLAY_MARGIN_DAYS)
    ],
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

  return buildTimeRangeDisplayData(graph, timeRange).data;
};

export const materializeLocationGraphForTimeRange = (
  graph: LocationGraph,
  timeRange: DashboardTimeRange
): LocationGraph => {
  const display = buildTimeRangeDisplayData(graph, timeRange);
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
