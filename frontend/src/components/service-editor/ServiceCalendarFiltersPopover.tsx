import Popover from "corvu/popover";
import {For, Show, type Component} from "solid-js";
import type {
  ServiceEventResponsibility,
  ServiceEventStatus
} from "../../types/Types";
import type {ServiceCalendarFilters} from "../../util/location/serviceCalendarFilters";

type ServiceCalendarFiltersPopoverProps = {
  filters: ServiceCalendarFilters;
  activeFilterCount: number;
  onUpdate: (updater: (current: ServiceCalendarFilters) => ServiceCalendarFilters) => void;
};

const FILTER_POPOVER_PROPS = {
  placement: "bottom-start" as const,
  trapFocus: false,
  restoreFocus: false,
  closeOnOutsideFocus: false
};

const RESPONSIBILITY_FILTER_OPTIONS: ReadonlyArray<{
  value: ServiceEventResponsibility;
  label: string;
}> = [
  {value: "client", label: "Client"},
  {value: "partner", label: "Partner"}
];

const STATUS_FILTER_OPTIONS: ReadonlyArray<{
  value: ServiceEventStatus;
  label: string;
}> = [
  {value: "completed", label: "Completed"},
  {value: "upcoming", label: "Upcoming"},
  {value: "overdue", label: "Overdue"},
  {value: "current", label: "Current"}
];

const filterTriggerClass =
  "btn btn-sm h-11 min-h-11 gap-2 rounded-2xl border border-base-300 bg-base-100/80 px-4 text-sm text-base-content/75 " +
  "shadow-sm transition duration-150 ease-out motion-reduce:transform-none motion-reduce:transition-none " +
  "hover:-translate-y-px hover:bg-base-200/80 hover:text-base-content active:translate-y-px active:scale-[0.98]";

const filterPanelSectionClass = "space-y-3 rounded-xl border border-base-300/80 bg-base-100 px-3 py-3";

export const ServiceCalendarFiltersPopover: Component<ServiceCalendarFiltersPopoverProps> = (props) => (
  <Popover {...FILTER_POPOVER_PROPS}>
    <Popover.Trigger
      type="button"
      class={filterTriggerClass}
      aria-label="Open calendar filters"
      data-service-calendar-filter-trigger=""
      onClick={(event) => event.stopPropagation()}
    >
      <span>Filters</span>
      <Show when={props.activeFilterCount > 0}>
        <span class="badge badge-primary badge-sm">{props.activeFilterCount}</span>
      </Show>
    </Popover.Trigger>

    <Popover.Portal>
      <Popover.Content
        class="z-50 w-[min(92vw,24rem)] rounded-2xl border border-base-300 bg-base-100 shadow-2xl"
        data-service-calendar-filter-menu=""
      >
        <div class="space-y-4 p-4">
          <div class="space-y-1">
            <Popover.Label class="text-sm font-semibold">Calendar Filters</Popover.Label>
            <Popover.Description class="text-xs leading-5 text-base-content/65">
              Enable a filter, then choose a value to narrow the events rendered on this calendar.
            </Popover.Description>
          </div>

          <div class={filterPanelSectionClass}>
            <label class="flex items-center gap-2 text-sm font-medium">
              <input
                type="checkbox"
                class="checkbox checkbox-primary checkbox-sm"
                checked={props.filters.responsibility.enabled}
                data-service-calendar-filter-responsibility-toggle=""
                onChange={(event) => props.onUpdate((current) => ({
                  ...current,
                  responsibility: {
                    ...current.responsibility,
                    enabled: event.currentTarget.checked
                  }
                }))}
              />
              <span>Responsibility</span>
            </label>

            <label class="form-control w-full">
              <span class="label-text text-xs font-medium uppercase tracking-[0.16em] text-base-content/55">
                Value
              </span>
              <select
                class="select select-bordered w-full"
                value={props.filters.responsibility.value}
                disabled={!props.filters.responsibility.enabled}
                data-service-calendar-filter-responsibility-value=""
                onChange={(event) => props.onUpdate((current) => ({
                  ...current,
                  responsibility: {
                    ...current.responsibility,
                    value: event.currentTarget.value as ServiceCalendarFilters["responsibility"]["value"]
                  }
                }))}
              >
                <option value="">Select responsibility</option>
                <For each={RESPONSIBILITY_FILTER_OPTIONS}>
                  {(option) => <option value={option.value}>{option.label}</option>}
                </For>
              </select>
            </label>
          </div>

          <div class={filterPanelSectionClass}>
            <label class="flex items-center gap-2 text-sm font-medium">
              <input
                type="checkbox"
                class="checkbox checkbox-primary checkbox-sm"
                checked={props.filters.date.enabled}
                data-service-calendar-filter-date-toggle=""
                onChange={(event) => props.onUpdate((current) => ({
                  ...current,
                  date: {
                    ...current.date,
                    enabled: event.currentTarget.checked
                  }
                }))}
              />
              <span>Date</span>
            </label>

            <label class="form-control w-full">
              <span class="label-text text-xs font-medium uppercase tracking-[0.16em] text-base-content/55">
                Value
              </span>
              <input
                type="date"
                class="input input-bordered w-full"
                value={props.filters.date.value}
                disabled={!props.filters.date.enabled}
                data-service-calendar-filter-date-value=""
                onInput={(event) => props.onUpdate((current) => ({
                  ...current,
                  date: {
                    ...current.date,
                    value: event.currentTarget.value
                  }
                }))}
              />
            </label>
          </div>

          <div class={filterPanelSectionClass}>
            <label class="flex items-center gap-2 text-sm font-medium">
              <input
                type="checkbox"
                class="checkbox checkbox-primary checkbox-sm"
                checked={props.filters.status.enabled}
                data-service-calendar-filter-status-toggle=""
                onChange={(event) => props.onUpdate((current) => ({
                  ...current,
                  status: {
                    ...current.status,
                    enabled: event.currentTarget.checked
                  }
                }))}
              />
              <span>Status</span>
            </label>

            <label class="form-control w-full">
              <span class="label-text text-xs font-medium uppercase tracking-[0.16em] text-base-content/55">
                Value
              </span>
              <select
                class="select select-bordered w-full"
                value={props.filters.status.value}
                disabled={!props.filters.status.enabled}
                data-service-calendar-filter-status-value=""
                onChange={(event) => props.onUpdate((current) => ({
                  ...current,
                  status: {
                    ...current.status,
                    value: event.currentTarget.value as ServiceCalendarFilters["status"]["value"]
                  }
                }))}
              >
                <option value="">Select status</option>
                <For each={STATUS_FILTER_OPTIONS}>
                  {(option) => <option value={option.value}>{option.label}</option>}
                </For>
              </select>
            </label>
          </div>
        </div>
      </Popover.Content>
    </Popover.Portal>
  </Popover>
);

export default ServiceCalendarFiltersPopover;
