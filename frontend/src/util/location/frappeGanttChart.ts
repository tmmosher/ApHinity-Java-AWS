import type {FrappeGanttOptions} from "frappe-gantt";
import type {LocationGanttTask} from "../../types/Types";

export const GANTT_CHART_HOST_ID = "location-gantt-chart-host";

export const GANTT_CHART_HOST_CLASS = "gantt-chart-host";

export type FrappeGanttChartTask = {
  id: string;
  name: string;
  start: string;
  end: string;
  progress: number;
  custom_class?: string;
  description: string | null;
};

export type TimelineTaskLike = LocationGanttTask & {
  isStaged?: true;
};

export const isStagedGanttTask = (
  task: TimelineTaskLike
): task is TimelineTaskLike & {isStaged: true} => task.isStaged === true;

export const toFrappeGanttTask = (task: TimelineTaskLike): FrappeGanttChartTask => ({
  id: String(task.id),
  name: task.title,
  start: task.startDate,
  end: task.endDate,
  progress: 0,
  custom_class: isStagedGanttTask(task) ? "gantt-task-staged" : "gantt-task-persisted",
  description: task.description
});

export const resolveFrappeGanttContainerHeight = (height: number): number => (
  Math.max(1, Math.ceil(height))
);

export const createFrappeGanttOptions = (
  containerHeight: number,
  onClick: (task: FrappeGanttChartTask) => void
): FrappeGanttOptions => ({
  readonly: true,
  popup_on: "click",
  popup: () => false,
  view_mode: "Day",
  date_format: "YYYY-MM-DD",
  scroll_to: "start",
  infinite_padding: false,
  container_height: resolveFrappeGanttContainerHeight(containerHeight),
  on_click: onClick
});
