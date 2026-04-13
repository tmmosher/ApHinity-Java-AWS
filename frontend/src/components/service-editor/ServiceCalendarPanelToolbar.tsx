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

export const ServiceCalendarPanelToolbar = (props: ServiceCalendarPanelToolbarProps) => (
  <div class="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
    <div class="space-y-1">
      <h2 class="text-xl font-semibold tracking-tight">Service Calendar</h2>
    </div>
    <div class="flex flex-col items-stretch gap-3 md:items-end">
      <div class="flex flex-wrap items-center gap-3">
        <ServiceCalendarIntroPopover templateHref={props.templateHref} />
        <input
          ref={props.spreadsheetUploadInputRef}
          type="file"
          accept=".xlsx"
          class="file-input file-input-bordered file-input-sm w-full min-w-[18rem] rounded-2xl md:w-auto"
          aria-label="Import service calendar spreadsheet"
          data-service-calendar-upload-input=""
          disabled={props.isMutationBusy}
          onChange={props.onSpreadsheetInputChange}
        />
        <button
          type="button"
          class={"btn btn-sm rounded-2xl " + (
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
          class={"btn btn-sm rounded-2xl " + (
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
          {props.pendingMutationCount} pending import mutation{props.pendingMutationCount === 1 ? "" : "s"}
        </p>
      </Show>
    </div>
  </div>
);

export default ServiceCalendarPanelToolbar;
