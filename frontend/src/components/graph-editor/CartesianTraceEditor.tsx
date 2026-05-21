import { Index, Show } from "solid-js";
import { toInputValue } from "../../util/graph/graphTraceEditor";

type CartesianTraceEditorProps = {
  heading: string;
  rowIndexes: number[];
  xValues: unknown[];
  yValues: unknown[];
  rowColors?: string[];
  colorOptions?: Record<string, string>;
  xLabel?: string;
  yLabel?: string;
  rangeLabel?: string;
  barOrientation?: "h" | "v";
  yRangeMin: unknown;
  yRangeMax: unknown;
  yAxisTitle?: string;
  xDrafts: Record<number, string>;
  yDrafts: Record<number, string>;
  xInputMode?: "decimal";
  yInputMode?: "decimal";
  yRangeMinDraft?: string;
  yRangeMaxDraft?: string;
  isBusy: boolean;
  onUpdateBarOrientation?: (nextOrientation: "h" | "v") => void;
  onAddRow: () => void;
  onUpdateX: (rowIndex: number, rawValue: string) => void;
  onUpdateY: (rowIndex: number, rawValue: string) => void;
  onUpdateColor?: (rowIndex: number, colorHex: string) => void;
  onUpdateYRangeMin: (rawValue: string) => void;
  onUpdateYRangeMax: (rawValue: string) => void;
  onUpdateYAxisTitle: (rawValue: string) => void;
  onRemoveRow: (rowIndex: number) => void;
};

const CartesianTraceEditor = (props: CartesianTraceEditorProps) => {
  const xDrafts = () => props.xDrafts ?? ({} as Record<number, string>);
  const yDrafts = () => props.yDrafts ?? ({} as Record<number, string>);
  const xLabel = () => props.xLabel ?? "X";
  const yLabel = () => props.yLabel ?? "Y";
  const rangeLabel = () => props.rangeLabel ?? "Y axis";

  return (
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

      <div class={"grid grid-cols-1 gap-2 rounded-lg border border-base-300 p-3 " + (props.onUpdateBarOrientation ? "md:grid-cols-3" : "md:grid-cols-2")}>
        <Show when={props.onUpdateBarOrientation}>
          <label class="form-control">
            <span class="label-text">Orientation</span>
            <select
              class="select select-bordered select-sm mt-1"
              value={props.barOrientation ?? "h"}
              disabled={props.isBusy}
              onChange={(event) => props.onUpdateBarOrientation?.(event.currentTarget.value === "v" ? "v" : "h")}
            >
              <option value="h">Horizontal</option>
              <option value="v">Vertical</option>
            </select>
          </label>
        </Show>
        <label class="form-control">
          <span class="label-text">{rangeLabel()} range min</span>
          <input
            class={"input input-bordered input-sm mt-1" + (props.yRangeMinDraft !== undefined ? " input-error" : "")}
            aria-invalid={props.yRangeMinDraft !== undefined}
            type="text"
            inputmode="decimal"
            placeholder="Auto"
            value={props.yRangeMinDraft ?? toInputValue(props.yRangeMin)}
            disabled={props.isBusy}
            onInput={(event) => props.onUpdateYRangeMin(event.currentTarget.value)}
          />
        </label>
        <label class="form-control">
          <span class="label-text">{rangeLabel()} range max</span>
          <input
            class={"input input-bordered input-sm mt-1" + (props.yRangeMaxDraft !== undefined ? " input-error" : "")}
            aria-invalid={props.yRangeMaxDraft !== undefined}
            type="text"
            inputmode="decimal"
            placeholder="Auto"
            value={props.yRangeMaxDraft ?? toInputValue(props.yRangeMax)}
            disabled={props.isBusy}
            onInput={(event) => props.onUpdateYRangeMax(event.currentTarget.value)}
          />
        </label>
        <label class="form-control md:col-span-full">
          <span class="label-text">{rangeLabel()} title</span>
          <input
            class="input input-bordered input-sm mt-1"
            type="text"
            placeholder={`Optional ${rangeLabel().toLowerCase()} title`}
            value={props.yAxisTitle ?? ""}
            disabled={props.isBusy}
            onInput={(event) => props.onUpdateYAxisTitle(event.currentTarget.value)}
          />
        </label>
      </div>

      <Show when={props.rowIndexes.length > 0} fallback={<p class="text-sm text-base-content/70">No values in this trace.</p>}>
        <ul class="space-y-2">
          <Index each={props.rowIndexes}>
            {(_, rowIndex) => (
              <li
                class={
                  "grid grid-cols-1 gap-2 rounded-lg border border-base-300 p-3 " +
                  (props.onUpdateColor && props.colorOptions
                    ? "md:grid-cols-[minmax(0,13rem)_minmax(0,1fr)_minmax(0,1fr)_auto]"
                    : "md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto]")
                }
              >
                <Show when={props.onUpdateColor && props.colorOptions}>
                  <select
                    class="select select-bordered select-sm"
                    value={props.rowColors?.[rowIndex] ?? ""}
                    disabled={props.isBusy}
                    onChange={(event) => {
                      const nextColor = event.currentTarget.value;
                      if (nextColor.length > 0) {
                        props.onUpdateColor?.(rowIndex, nextColor);
                      }
                    }}
                  >
                    <option value="">Choose a color</option>
                    <Index each={Object.entries(props.colorOptions ?? {})}>
                      {(entry) => (
                        <option value={entry()[1]}>{entry()[0]}</option>
                      )}
                    </Index>
                  </select>
                </Show>
                <input
                  class={"input input-bordered input-sm" + (xDrafts()[rowIndex] !== undefined ? " input-error" : "")}
                  aria-invalid={xDrafts()[rowIndex] !== undefined}
                  type="text"
                  inputmode={props.xInputMode}
                  placeholder={`${xLabel()} value`}
                  value={xDrafts()[rowIndex] ?? toInputValue(props.xValues[rowIndex])}
                  disabled={props.isBusy}
                  onInput={(event) => props.onUpdateX(rowIndex, event.currentTarget.value)}
                />
                <input
                  class={"input input-bordered input-sm" + (yDrafts()[rowIndex] !== undefined ? " input-error" : "")}
                  aria-invalid={yDrafts()[rowIndex] !== undefined}
                  type="text"
                  inputmode={props.yInputMode}
                  placeholder={`${yLabel()} value`}
                  value={yDrafts()[rowIndex] ?? toInputValue(props.yValues[rowIndex])}
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
};

export default CartesianTraceEditor;
