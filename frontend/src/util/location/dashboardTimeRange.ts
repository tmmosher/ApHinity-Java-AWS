import type {LocationGraph, LocationGraphTimeRange} from "../../types/Types";

export type DashboardTimeRange = LocationGraphTimeRange;

export const dashboardTimeRangeOptions: Array<{
  value: DashboardTimeRange;
  label: string;
  description: string;
}> = [
  {
    value: "oneMonth",
    label: "1 Month",
    description: "Recent month"
  },
  {
    value: "threeMonths",
    label: "3 Months",
    description: "Rolling quarter"
  },
  {
    value: "allTime",
    label: "All Data",
    description: "Full history"
  }
];

const isGraphDataList = (value: unknown): value is Record<string, unknown>[] =>
  Array.isArray(value) && value.every((entry) => entry !== null && typeof entry === "object" && !Array.isArray(entry));

export const resolveLocationGraphDataForTimeRange = (
  graph: LocationGraph,
  timeRange: DashboardTimeRange
): Record<string, unknown>[] => {
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
  if (resolvedData === graph.data) {
    return graph;
  }
  return {
    ...graph,
    data: resolvedData
  };
};
