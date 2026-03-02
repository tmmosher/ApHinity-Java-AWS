import { Index, Show } from "solid-js";
import { toInputValue } from "../../util/graphTraceEditor";

type CartesianTraceEditorProps = {
  heading: string;
  rowIndexes: number[];
  xValues: unknown[];
  yValues: unknown[];
  yRangeMin: unknown;
  yRangeMax: unknown;
  isBusy: boolean;
  onAddRow: () => void;
  onUpdateX: (rowIndex: number, rawValue: string) => void;
  onUpdateY: (rowIndex: number, rawValue: string) => void;
  onUpdateYRangeMin: (rawValue: string) => void;
  onUpdateYRangeMax: (rawValue: string) => void;
  onRemoveRow: (rowIndex: number) => void;
};

const CartesianTraceEditor = (props: CartesianTraceEditorProps) => (
  <section class="space-y-3">
    <div class="flex items-center justify-between gap-2">
      <h3 class="text-sm font-semibold">{props.heading} Values</h3>
      <button
        type="button"
        class={"btn btn-xs " + (props.isBusy ? "btn-disabled" : "btn-outline")}
        disabled={props.isBusy}
        onClick={props.onAddRow}
      >
        Add Row
      </button>
    </div>

    <div class="grid grid-cols-1 gap-2 rounded-lg border border-base-300 p-3 md:grid-cols-2">
      <label class="form-control">
        <span class="label-text">Y range min</span>
        <input
          class="input input-bordered input-sm mt-1"
          type="text"
          inputmode="decimal"
          placeholder="Auto"
          value={toInputValue(props.yRangeMin)}
          disabled={props.isBusy}
          onInput={(event) => props.onUpdateYRangeMin(event.currentTarget.value)}
        />
      </label>
      <label class="form-control">
        <span class="label-text">Y range max</span>
        <input
          class="input input-bordered input-sm mt-1"
          type="text"
          inputmode="decimal"
          placeholder="Auto"
          value={toInputValue(props.yRangeMax)}
          disabled={props.isBusy}
          onInput={(event) => props.onUpdateYRangeMax(event.currentTarget.value)}
        />
      </label>
    </div>

    <Show when={props.rowIndexes.length > 0} fallback={<p class="text-sm text-base-content/70">No values in this trace.</p>}>
      <ul class="space-y-2">
        <Index each={props.rowIndexes}>
          {(_, rowIndex) => (
            <li class="grid grid-cols-1 gap-2 rounded-lg border border-base-300 p-3 md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto]">
              <input
                class="input input-bordered input-sm"
                type="text"
                placeholder="X value"
                value={toInputValue(props.xValues[rowIndex])}
                disabled={props.isBusy}
                onInput={(event) => props.onUpdateX(rowIndex, event.currentTarget.value)}
              />
              <input
                class="input input-bordered input-sm"
                type="text"
                inputmode="decimal"
                placeholder="Y value"
                value={toInputValue(props.yValues[rowIndex])}
                disabled={props.isBusy}
                onInput={(event) => props.onUpdateY(rowIndex, event.currentTarget.value)}
              />
              <button
                type="button"
                class={"btn btn-xs " + (props.isBusy ? "btn-disabled" : "btn-ghost")}
                disabled={props.isBusy}
                onClick={() => props.onRemoveRow(rowIndex)}
              >
                Remove
              </button>
            </li>
          )}
        </Index>
      </ul>
    </Show>
  </section>
);

export default CartesianTraceEditor;
