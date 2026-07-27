import type {LocationGraphTimeRange} from "../../types/Types";

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

export const monthRangeForDashboardTimeRange = (timeRange: DashboardTimeRange): number => {
  if (timeRange === "threeMonths") {
    return 3;
  }
  if (timeRange === "twelveMonths") {
    return 12;
  }
  return -1;
};

export type CommonDashboardTimeRangeSelectionPort = {
  invalidateGraphCache: () => void;
  setGraphTimeRange: (timeRange: DashboardTimeRange) => void;
};

/** Resets the complete dashboard slate before selecting any common range, including the current one. */
export const selectCommonDashboardTimeRange = (
  timeRange: DashboardTimeRange,
  port: CommonDashboardTimeRangeSelectionPort
): void => {
  port.invalidateGraphCache();
  port.setGraphTimeRange(timeRange);
};
