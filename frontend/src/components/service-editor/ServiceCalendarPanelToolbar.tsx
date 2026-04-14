import {Show, type JSX} from "solid-js";
import ServiceCalendarIntroPopover from "./ServiceCalendarIntroPopover";

type ServiceCalendarPanelToolbarProps = {
  templateHref: string;
  spreadsheetUploadInputRef: (element: HTMLInputElement) => void;
  isMutationBusy: boolean;
  isApplyingImportedEvents: boolean;
  hasStagedImportedEvents: boolean;
  hasPendingImportedEventChanges: boolean;
  stagedEventCount: number;
  pendingMutationCount: number;
  onSpreadsheetInputChange: JSX.EventHandler<HTMLInputElement, Event>;
  onApply: () => void;
  onUndo: () => void;
};

const toolbarActionButtonClass =
  "btn h-11 min-h-11 rounded-2xl px-4 text-sm font-medium shadow-sm transition duration-150 ease-out " +
  "motion-reduce:transform-none motion-reduce:transition-none hover:-translate-y-px active:translate-y-px active:scale-[0.98]";

const toolbarUploadInputClassInactive =
  "btn hover:btn-soft h-11 min-h-11 rounded-2xl text-sm md:w-auto flex-initial";

export const ServiceCalendarPanelToolbar = (props: ServiceCalendarPanelToolbarProps) => (
  <div class="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
    <div class="space-y-1">
      <h2 class="flex h-11 items-center text-xl font-semibold tracking-tight">Service Calendar</h2>
    </div>
    <div class="flex flex-col items-stretch gap-3 md:items-end">
      <div class="flex flex-wrap items-center gap-3">
        <ServiceCalendarIntroPopover templateHref={props.templateHref} />
        <label for="calendar_file_upload"
               class={toolbarUploadInputClassInactive}>
            {/*got this svg from https://lucide.dev/icons/upload*/}
          <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="lucide lucide-upload-icon lucide-upload"><path d="M12 3v12"/><path d="m17 8-5-5-5 5"/><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/></svg>
        </label>
        <input
          ref={props.spreadsheetUploadInputRef}
          type="file"
          accept=".xlsx"
          style="display:none"
          aria-label="Import service calendar spreadsheet"
          data-service-calendar-upload-input=""
          disabled={props.isMutationBusy}
          onChange={props.onSpreadsheetInputChange}
          id="calendar_file_upload"
        />
        <button
          type="button"
          class={toolbarActionButtonClass + " " + (
            props.hasStagedImportedEvents && !props.isMutationBusy ? "btn-primary" : "btn-disabled"
          )}
          disabled={!props.hasStagedImportedEvents || props.isMutationBusy}
          data-service-calendar-apply=""
          onClick={props.onApply}
        >
          {props.isApplyingImportedEvents ? "Applying..." : "Apply"}
        </button>
        <button
          type="button"
          class={toolbarActionButtonClass + " " + (
            props.hasPendingImportedEventChanges && !props.isMutationBusy ? "btn-outline" : "btn-disabled"
          )}
          disabled={!props.hasPendingImportedEventChanges || props.isMutationBusy}
          data-service-calendar-undo=""
          onClick={props.onUndo}
        >
          Undo
        </button>
      </div>
      <Show when={props.hasPendingImportedEventChanges}>
        <p class="text-right text-xs text-base-content/70">
          {props.stagedEventCount} staged service event{props.stagedEventCount === 1 ? "" : "s"} -{" "}
          {props.pendingMutationCount} pending calendar mutation{props.pendingMutationCount === 1 ? "" : "s"}
        </p>
      </Show>
    </div>
  </div>
);

export default ServiceCalendarPanelToolbar;
