import {Show, createEffect, createSignal, createUniqueId, type Accessor, type Signal} from "solid-js";
import {useDashboardTimeRange} from "../../context/DashboardTimeRangeContext";
import {toast} from "solid-toast";

export type LocationDashboardSectionTimeRangeControlProps = {
  monthRange: Accessor<number>;
  hasOverride: Signal<boolean>;
  loading: boolean;
  error?: string;
  disabledReason?: string;
  onApply: (monthRange: number) => void;
  onReset: () => void;
};

/** Folded-corner presentation adapter for independently ranged dashboard sections. */
export const LocationDashboardSectionTimeRangeControl = (
  props: LocationDashboardSectionTimeRangeControlProps
) => {
  const dashboardTimeRange = useDashboardTimeRange();
  const [open, setOpen] = createSignal(false);
  const [monthRange, setMonthRange] = createSignal(props.monthRange() > 0 ? props.monthRange() : 1);
  const inputId = createUniqueId();

  createEffect(() => {
    if (props.monthRange() > 0) {
      setMonthRange(props.monthRange());
    }
    if (props.disabledReason) {
      setOpen(false);
    }
  });

  const apply = () => {
    const nextRange = monthRange();
    if (!Number.isInteger(nextRange) || nextRange <= 0) {
      toast.error("Month range invalid.");
      return;
    }
    if (nextRange === dashboardTimeRange.monthRange()) {
      props.onReset();
    } else {
      props.onApply(nextRange);
    }
  };

  return (
    <div class="absolute right-0 top-0 z-40" data-section-time-range-control="">
      <button
        type="button"
        class={
          "relative block h-12 w-12 z-10 overflow-hidden rounded-tr-xl text-primary-content transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50 "
          + (props.disabledReason
            ? "cursor-not-allowed bg-base-300 text-base-content/40"
            : props.hasOverride[0]()
              ? "bg-secondary hover:brightness-110"
              : "bg-primary hover:brightness-110")
        }
        style={{"clip-path": "polygon(33.33% 0, 100% 0, 100% 66.67%)"}}
        aria-label="Set section date range"
        aria-expanded={open()}
        disabled={Boolean(props.disabledReason)}
        title={props.disabledReason}
        data-section-time-range-trigger=""
        onClick={() => setOpen((current) => !current)}
      >
        <span class="absolute right-1.5 top-1 text-sm" aria-hidden="true">◷</span>
      </button>
      <Show when={open()}>
        <div
          class="absolute right-2 top-10 w-64 rounded-xl border border-base-300 bg-base-100 p-3 text-left shadow-xl"
          data-section-time-range-popover=""
        >
          <p class="text-xs font-semibold uppercase tracking-wide text-base-content/60">Section date range</p>
          <label class="mt-2 block text-xs text-base-content/70" for={inputId}>Prior months</label>
          <input
            id={inputId}
            type="number"
            min="1"
            step="1"
            value={monthRange()}
            class="input input-bordered input-sm mt-1 w-full"
            disabled={props.loading}
            onInput={(event) => setMonthRange(Number(event.currentTarget.value))}
          />
          <Show when={props.error}>
            {(message) => <p class="mt-2 text-xs text-error" role="alert">{message()}</p>}
          </Show>
          <div class="mt-3 flex justify-end gap-2">
            <Show when={props.hasOverride[0]()}>
              <button
                type="button"
                class="btn btn-ghost btn-xs"
                disabled={props.loading}
                onClick={() => {
                  props.onReset();
                  setOpen(false);
                }}
              >
                Reset
              </button>
            </Show>
            <button
              type="button"
              class="btn btn-primary btn-xs"
              disabled={props.loading || !Number.isInteger(monthRange()) || monthRange() <= 0}
              onClick={apply}
            >
              {props.loading ? "Loading…" : "Apply"}
            </button>
          </div>
        </div>
      </Show>
    </div>
  );
};

export default LocationDashboardSectionTimeRangeControl;
