import {Show, type JSX} from "solid-js";
import {GanttChartToolbarContent} from "./GanttChartToolbarContent";

type GanttChartToolbarProps = {
  canEdit: boolean;
  templateHref: string;
  isImportingSpreadsheet: boolean;
  isApplyingImports: boolean;
  hasStagedTasks: boolean;
  hasPendingTaskChanges: boolean;
  spreadsheetUploadInputRef: (element: HTMLInputElement) => void;
  onSpreadsheetUploadChange: JSX.EventHandler<HTMLInputElement, Event>;
  onApply: () => void;
  onUndo: () => void;
};

const toolbarActionButtonClass =
  "btn h-11 min-h-11 rounded-2xl px-4 text-sm font-medium shadow-sm transition duration-150 ease-out " +
  "motion-reduce:transform-none motion-reduce:transition-none hover:-translate-y-px active:translate-y-px active:scale-[0.98]";

const toolbarUploadInputClassInactive =
  "btn hover:btn-soft h-11 min-h-11 rounded-2xl text-sm md:w-auto flex-initial";

export const GanttChartToolbar = (props: GanttChartToolbarProps) => {
  const isBusy = props.isImportingSpreadsheet || props.isApplyingImports;

  return (
    <section
      class="rounded-2xl border border-base-300 bg-base-100/70 p-6 shadow-sm"
      data-template-href={props.templateHref}
    >
      <div class="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div class="space-y-1">
          <h2 class="flex h-11 items-center text-xl font-semibold tracking-tight ">Gantt Chart</h2>
        </div>

        <div class="flex flex-col gap-3 md:items-end">
          <div class="flex flex-wrap items-center gap-3">
            <GanttChartToolbarContent
              templateHref={props.templateHref}
              canEdit={props.canEdit}
            />

            <Show when={props.canEdit}>
              <label
                for="gantt_spreadsheet_upload"
                class={toolbarUploadInputClassInactive + (isBusy ? " btn-disabled" : "")}
              >
                <span class="sr-only">Upload Excel spreadsheet</span>
                {/*got this svg from https://lucide.dev/icons/upload*/}
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  width="24"
                  height="24"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  class="lucide lucide-upload-icon lucide-upload"
                >
                  <path d="M12 3v12"/>
                  <path d="m17 8-5-5-5 5"/>
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                </svg>
              </label>
              <input
                id="gantt_spreadsheet_upload"
                type="file"
                ref={props.spreadsheetUploadInputRef}
                class="hidden"
                accept=".xlsx"
                disabled={isBusy}
                onChange={props.onSpreadsheetUploadChange}
              />
              <button
                type="button"
                class={toolbarActionButtonClass + " " + (
                  props.hasStagedTasks && !isBusy ? "btn-primary" : "btn-disabled"
                )}
                disabled={!props.hasStagedTasks || isBusy}
                onClick={props.onApply}
              >
                {props.isApplyingImports ? "Applying..." : "Apply"}
              </button>
              <button
                type="button"
                class={toolbarActionButtonClass + " " + (
                  props.hasPendingTaskChanges && !isBusy ? "btn-outline" : "btn-disabled"
                )}
                disabled={!props.hasPendingTaskChanges || isBusy}
                onClick={props.onUndo}
              >
                Undo
              </button>
            </Show>
          </div>
        </div>
      </div>
    </section>
  );
};

export default GanttChartToolbar;
