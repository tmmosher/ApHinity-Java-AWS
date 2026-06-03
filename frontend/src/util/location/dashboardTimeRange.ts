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
