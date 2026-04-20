import {type JSX, Show} from "solid-js";
import {GanttChartShell} from "./GanttChartShell";
import {GanttTaskCreatePopover} from "./GanttTaskCreatePopover";
import type {CreateLocationGanttTaskRequest} from "../../types/Types";

type GanttChartContentProps = {
  onSearchInput: JSX.EventHandler<HTMLInputElement, InputEvent>;
  searchDraft: string;
  taskLoadError?: string;
  isLoading: boolean;
  canEdit: boolean;
  onCreateTask: (request: CreateLocationGanttTaskRequest) => Promise<void>;
  hostId: string;
  timelineTaskCount: number;
  searchQuery: string;
};

export const GanttChartContent = (props: GanttChartContentProps) => (
  <section class="flex w-full flex-col rounded-2xl border border-base-300 bg-base-100/70 p-4 shadow-sm md:p-6">
    <Show when={props.taskLoadError}>
      {(message) => (
        <div class="mb-3 rounded-xl border border-error/25 bg-error/10 px-3 py-2 text-sm text-error">
          {message()}
        </div>
      )}
    </Show>

    <Show when={props.canEdit}>
      <div class="p-4 mb-3 flex space-x-4 align-middle justify-end">
        <label class="input input-bordered ml-auto rounded-2xl flex h-10 min-h-10 w-full items-center gap-2 md:w-72">
          <input
            type="search"
            class="grow"
            placeholder="Search title..."
            value={props.searchDraft}
            onInput={props.onSearchInput}
          />
        </label>
        <GanttTaskCreatePopover onCreate={props.onCreateTask} />
      </div>
    </Show>

    <div class="w-full overflow-x-auto rounded-2xl border border-base-300 bg-base-100/80">
      {/* Frappe Gantt sets the SVG width to 100% and only expands it when the chart is wider.
          Keep the host shrink-wrapped so the SVG matches the actual grid width instead of leaving a blank gutter. */}
      <GanttChartShell
        hostId={props.hostId}
        class="w-max min-h-[32rem]"
      />
    </div>
  </section>
);

export default GanttChartContent;
