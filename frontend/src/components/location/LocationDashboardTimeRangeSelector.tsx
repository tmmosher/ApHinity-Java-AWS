import {For, type Accessor} from "solid-js";
import {
  dashboardTimeRangeOptions,
  type DashboardTimeRange
} from "../../util/location/dashboardTimeRange";

type LocationDashboardTimeRangeSelectorProps = {
  selectedRange: Accessor<DashboardTimeRange>;
  onSelectRange: (timeRange: DashboardTimeRange) => void;
};

export const LocationDashboardTimeRangeSelector = (props: LocationDashboardTimeRangeSelectorProps) => (
  <nav
    class="w-fit max-w-full overflow-hidden rounded-2xl border border-base-300 bg-base-200/40 shadow-sm"
    aria-label="Dashboard date range selector"
  >
    <div class="inline-grid grid-cols-3 divide-x divide-base-300">
      <For each={dashboardTimeRangeOptions}>
        {(option) => {
          const active = () => props.selectedRange() === option.value;
          return (
            <button
              type="button"
              class={
                "flex min-h-11 min-w-28 flex-col items-center justify-center px-3 py-2 text-center text-xs font-semibold tracking-tight transform-gpu transition duration-150 ease-out motion-reduce:transform-none motion-reduce:transition-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/40 sm:min-w-32 sm:text-sm " +
                (active()
                  ? "bg-primary text-primary-content shadow-sm"
                  : "bg-transparent text-base-content/70 hover:-translate-y-px hover:bg-base-100/70 hover:shadow-sm active:translate-y-px active:scale-[0.98]")
              }
              classList={{
                "cursor-default": active()
              }}
              aria-pressed={active()}
              onClick={() => props.onSelectRange(option.value)}
            >
              <span>{option.label}</span>
              <span class="text-[10px] font-medium opacity-80 sm:text-xs">{option.description}</span>
            </button>
          );
        }}
      </For>
    </div>
  </nav>
);

export default LocationDashboardTimeRangeSelector;
