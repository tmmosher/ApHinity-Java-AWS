import {GetStartedPopover} from "../help/GetStartedPopover";
import {Component, Show} from "solid-js";

type GetStartedPopoverProps = {
  canEdit: boolean;
  templateHref: string;
}

export const GanttChartToolbarContent: Component<GetStartedPopoverProps> = (props) => {
  return (
    <GetStartedPopover>
      <div class="space-y-4 p-4">
        <div class="space-y-1">
          <h3 class="text-sm font-semibold">How the gantt chart works</h3>
          <p class="text-sm leading-6 text-base-content/70">
            This tool communicates upcoming ApHinity project tasks
            to clients. When we are making improvements or additions
            to a system beyond basic service, they will be noted here.
          </p>
        </div>

        <Show when={props.canEdit}>
          <div class="space-y-2 rounded-xl border border-base-300/80 bg-base-100 px-3 py-3">
            <p class="text-xs font-medium uppercase tracking-[0.16em] text-base-content/55">Template</p>
            <p class="text-sm leading-6 text-base-content/75">
              Use the Excel template to draft or share gantt chart tasks before entering them here.
            </p>
            <a
              class="btn btn-outline btn-sm rounded-xl"
              href={props.templateHref}
              download="gantt_chart_template.xlsx"
            >
              Get a copy of the Excel template
            </a>
          </div>
        </Show>
      </div>
    </GetStartedPopover>
  );
}