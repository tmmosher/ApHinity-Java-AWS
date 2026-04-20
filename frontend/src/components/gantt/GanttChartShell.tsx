import type {JSX} from "solid-js";
import {GANTT_CHART_HOST_CLASS, GANTT_CHART_HOST_ID} from "../../util/location/frappeGanttChart";

type GanttChartShellProps = {
  hostId?: string;
  class?: string;
  style?: JSX.CSSProperties;
};

export const GanttChartShell = (props: GanttChartShellProps) => (
  <div
    id={props.hostId ?? GANTT_CHART_HOST_ID}
    class={`${GANTT_CHART_HOST_CLASS} ${props.class ?? ""}`.trim()}
    style={props.style}
  />
);
