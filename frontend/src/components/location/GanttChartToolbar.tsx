import {Show, type JSX} from "solid-js";
import {GanttChartToolbarContent} from "../gantt/GanttChartToolbarContent";

type GanttChartToolbarProps = {
  canEdit: boolean;
  isImportingCsv: boolean;
  isApplyingImports: boolean;
  hasStagedTasks: boolean;
  hasPendingTaskChanges: boolean;
  csvUploadInputRef: (element: HTMLInputElement) => void;
  onCsvUploadChange: JSX.EventHandler<HTMLInputElement, Event>;
  onApply: () => void;
  onUndo: () => void;
};

export const GanttChartToolbar = (props: GanttChartToolbarProps) => (
  <section class="rounded-2xl border border-base-300 bg-base-100/70 p-6 shadow-sm">
    <div class="flex flex-wrap items-center gap-3">
      <h2 class="text-xl font-semibold tracking-tight">Gantt Chart</h2>
    </div>

    <div class="flex flex-col gap-4 md:flex-row md:items-centered md:justify-between">
      <div class="mt-3 flex flex-wrap items-center gap-2 text-xs text-base-content/70">
        <GanttChartToolbarContent
          templateHref="gantt_chart_template.xlsx"
          canEdit={props.canEdit}/>
      </div>

      <Show when={props.canEdit}>
        <div class="mt-3 flex flex-wrap items-center gap-2">
          <label
            for="gantt_csv_upload"
            class={"btn btn-outline btn-sm " + ((props.isImportingCsv || props.isApplyingImports) ? "btn-disabled" : "")}
          >
            Upload CSV
          </label>
          <input
            id="gantt_csv_upload"
            type="file"
            ref={props.csvUploadInputRef}
            class="hidden"
            accept=".csv"
            disabled={props.isImportingCsv || props.isApplyingImports}
            onChange={props.onCsvUploadChange}
          />
          <button
            type="button"
            class={"btn btn-primary btn-sm " + ((!props.hasStagedTasks || props.isApplyingImports) ? "btn-disabled" : "")}
            disabled={!props.hasStagedTasks || props.isApplyingImports}
            onClick={props.onApply}
          >
            {props.isApplyingImports ? "Applying..." : "Apply"}
          </button>
          <button
            type="button"
            class={"btn btn-outline btn-sm " + ((!props.hasPendingTaskChanges || props.isApplyingImports) ? "btn-disabled" : "")}
            disabled={!props.hasPendingTaskChanges || props.isApplyingImports}
            onClick={props.onUndo}
          >
            Undo
          </button>
        </div>
      </Show>
    </div>
  </section>
);

export default GanttChartToolbar;
