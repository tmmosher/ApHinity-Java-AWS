import type {FrappeGanttOptions, FrappeGanttViewModeConfig} from "frappe-gantt";
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
  dependencies: string[];
};

export type TimelineTaskLike = LocationGanttTask & {
  isStaged?: true;
};

export const isStagedGanttTask = (
  task: TimelineTaskLike
): task is TimelineTaskLike & {isStaged: true} => task.isStaged === true;

const DAY_VIEW_MODE: FrappeGanttViewModeConfig = {
  name: "Day",
  // Frappe Gantt reads `step` during initialization, so a custom Day mode
  // needs the built-in Day timing fields as well as the padding override.
  // Keep the default left gutter but extend the right edge so the timeline
  // does not end abruptly at the last task.
  padding: ["7d", "14d"] as const,
  step: "1d",
  date_format: "YYYY-MM-DD",
  lower_text: (date, previousDate, lang) => (
    !previousDate || date.getDate() !== previousDate.getDate()
      ? String(date.getDate()).padStart(2, "0")
      : ""
  ),
  upper_text: (date, previousDate, lang) => {
    if (previousDate && date.getMonth() === previousDate.getMonth()) {
      return "";
    }

    const monthName = new Intl.DateTimeFormat(lang, {month: "long"}).format(date);
    return monthName.charAt(0).toUpperCase() + monthName.slice(1);
  },
  thick_line: (date) => date.getDay() === 1
};

export const toFrappeGanttTask = (task: TimelineTaskLike): FrappeGanttChartTask => ({
  id: String(task.id),
  name: task.title,
  start: task.startDate,
  end: task.endDate,
  progress: 0,
  custom_class: isStagedGanttTask(task) ? "gantt-task-staged" : "gantt-task-persisted",
  description: task.description,
  dependencies: task.dependencyTaskIds.map((dependencyTaskId) => String(dependencyTaskId))
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
  view_modes: [DAY_VIEW_MODE],
  date_format: "YYYY-MM-DD",
  scroll_to: "today",
  infinite_padding: false,
  container_height: resolveFrappeGanttContainerHeight(containerHeight),
  on_click: onClick
});
