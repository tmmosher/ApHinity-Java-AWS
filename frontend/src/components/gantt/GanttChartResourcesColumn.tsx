import {For, Show} from "solid-js";
import {
  GANTT_CHART_HEADER_HEIGHT,
  GANTT_CHART_ROW_HEIGHT,
  type TimelineTaskLike
} from "../../util/location/frappeGanttChart";
import GanttTaskResourceButton from "./GanttTaskResourceButton";

type GanttChartResourcesColumnProps = {
  tasks: TimelineTaskLike[];
};

export const GanttChartResourcesColumn = (props: GanttChartResourcesColumnProps) => (
  <aside
    class="flex h-full min-h-0 flex-col border-base-300/80 bg-base-200/35 lg:w-80 lg:min-w-80 lg:flex-none lg:border-r"
    data-gantt-resource-column=""
  >
    <div
      class="flex items-center border-b border-base-300/80 px-4"
      style={{height: `${GANTT_CHART_HEADER_HEIGHT}px`}}
    >
      <p class="text-xs font-semibold uppercase tracking-[0.24em] text-base-content/50">
        Resources
      </p>
    </div>

    <div class="flex flex-1 min-h-0 flex-col">
      <Show
        when={props.tasks.length > 0}
        fallback={
          <div class="flex flex-1 items-center px-4 py-4 text-sm text-base-content/60">
            No gantt tasks available.
          </div>
        }
      >
        <For each={props.tasks}>
          {(task) => (
            <div
              class="flex items-center border-b border-base-300/40 px-3 last:border-b-0"
              style={{height: `${GANTT_CHART_ROW_HEIGHT}px`}}
            >
              <GanttTaskResourceButton task={task} />
            </div>
          )}
        </For>
      </Show>
    </div>
  </aside>
);

export default GanttChartResourcesColumn;
