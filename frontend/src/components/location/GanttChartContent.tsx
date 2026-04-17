import {Show} from "solid-js";
import {GanttChartShell} from "./GanttChartShell";
import {GanttTaskCreatePopover} from "./GanttTaskCreatePopover";
import type {CreateLocationGanttTaskRequest} from "../../types/Types";

type GanttChartContentProps = {
  taskLoadError?: string;
  isLoading: boolean;
  canEdit: boolean;
  onCreateTask: (request: CreateLocationGanttTaskRequest) => Promise<void>;
  hostId: string;
  timelineTaskCount: number;
  searchQuery: string;
};

export const GanttChartContent = (props: GanttChartContentProps) => (
  <section class="flex flex-col rounded-2xl border border-base-300 bg-base-100/70 p-4 shadow-sm md:p-6">
    <Show when={props.taskLoadError}>
      {(message) => (
        <div class="mb-3 rounded-xl border border-error/25 bg-error/10 px-3 py-2 text-sm text-error">
          {message()}
        </div>
      )}
    </Show>

    <Show when={props.isLoading}>
      <p class="mb-3 text-sm text-base-content/70">Loading gantt tasks...</p>
    </Show>

    <Show when={props.canEdit}>
      <div class="mb-3 flex justify-end">
        <GanttTaskCreatePopover onCreate={props.onCreateTask} />
      </div>
    </Show>

    <div class="self-center w-fit max-w-full overflow-x-auto rounded-2xl border border-base-300 bg-base-100/80">
      <GanttChartShell
        hostId={props.hostId}
        class="w-max min-h-[32rem]"
      />
    </div>

    <Show when={!props.isLoading && props.timelineTaskCount === 0}>
      <p class="mt-3 text-sm text-base-content/70">
        {props.searchQuery ? "No gantt tasks match your search." : "No gantt tasks available for this location."}
      </p>
    </Show>
  </section>
);

export default GanttChartContent;
