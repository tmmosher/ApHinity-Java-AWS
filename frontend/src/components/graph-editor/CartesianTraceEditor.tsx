import { Index, Show } from "solid-js";
import { toInputValue } from "../../util/graph/graphTraceEditor";
import GraphColorPicker from "./GraphColorPicker";

export type CartesianAxisTitleControl = {
  key: string;
  label: string;
  value: string;
  placeholder: string;
  onUpdate: (rawValue: string) => void;
};

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
  axisTitleControls?: CartesianAxisTitleControl[];
  xDrafts: Record<number, string>;
  yDrafts: Record<number, string>;
  xInputMode?: "decimal";
  yInputMode?: "decimal";
  yRangeMinDraft?: string;
  yRangeMaxDraft?: string;
  isBusy: boolean;
  isDataEditingDisabled: boolean;
  onUpdateBarOrientation?: (nextOrientation: "h" | "v") => void;
  onAddRow: () => void;
  onUpdateX: (rowIndex: number, rawValue: string) => void;
  onUpdateY: (rowIndex: number, rawValue: string) => void;
  onUpdateColor?: (rowIndex: number, colorHex: string) => void;
  onUpdateYRangeMin: (rawValue: string) => void;
  onUpdateYRangeMax: (rawValue: string) => void;
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
          class={"btn btn-xs " + (props.isDataEditingDisabled ? "btn-disabled" : "btn-outline")}
          data-graph-edit-field="data"
          disabled={props.isDataEditingDisabled}
          onClick={props.onAddRow}
        >
          Add Row
        </button>
      </div>

      <div class={"grid grid-cols-1 gap-2 rounded-lg border border-base-300 p-3 " + (props.onUpdateBarOrientation ? "md:grid-cols-3" : "md:grid-cols-2")}>
        <Show when={props.onUpdateBarOrientation}>
          <label class="form-control" data-graph-edit-field="data">
            <span class="label-text">Orientation</span>
            <select
              class="select select-bordered select-sm mt-1"
              value={props.barOrientation ?? "h"}
              disabled={props.isDataEditingDisabled}
              onChange={(event) => props.onUpdateBarOrientation?.(event.currentTarget.value === "v" ? "v" : "h")}
            >
              <option value="h">Horizontal</option>
              <option value="v">Vertical</option>
            </select>
          </label>
        </Show>
        <label class="form-control" data-graph-edit-field="layout">
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
        <label class="form-control" data-graph-edit-field="layout">
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
        <Index each={props.axisTitleControls ?? []}>
          {(axisTitleControl) => (
            <label class="form-control" data-graph-edit-field="layout">
              <span class="label-text">{axisTitleControl().label}</span>
              <input
                class="input input-bordered input-sm mt-1"
                type="text"
                placeholder={axisTitleControl().placeholder}
                value={axisTitleControl().value}
                disabled={props.isBusy}
                onInput={(event) => axisTitleControl().onUpdate(event.currentTarget.value)}
              />
            </label>
          )}
        </Index>
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
                  <div data-graph-edit-field="data">
                    <GraphColorPicker
                      value={props.rowColors?.[rowIndex] ?? ""}
                      disabled={props.isDataEditingDisabled}
                      colorOptions={props.colorOptions ?? {}}
                      onChange={(nextColor) => props.onUpdateColor?.(rowIndex, nextColor)}
                    />
                  </div>
                </Show>
                <input
                  class={"input input-bordered input-sm" + (xDrafts()[rowIndex] !== undefined ? " input-error" : "")}
                  aria-invalid={xDrafts()[rowIndex] !== undefined}
                  type="text"
                  inputmode={props.xInputMode}
                  placeholder={`${xLabel()} value`}
                  value={xDrafts()[rowIndex] ?? toInputValue(props.xValues[rowIndex])}
                  data-graph-edit-field="data"
                  disabled={props.isDataEditingDisabled}
                  onInput={(event) => props.onUpdateX(rowIndex, event.currentTarget.value)}
                />
                <input
                  class={"input input-bordered input-sm" + (yDrafts()[rowIndex] !== undefined ? " input-error" : "")}
                  aria-invalid={yDrafts()[rowIndex] !== undefined}
                  type="text"
                  inputmode={props.yInputMode}
                  placeholder={`${yLabel()} value`}
                  value={yDrafts()[rowIndex] ?? toInputValue(props.yValues[rowIndex])}
                  data-graph-edit-field="data"
                  disabled={props.isDataEditingDisabled}
                  onInput={(event) => props.onUpdateY(rowIndex, event.currentTarget.value)}
                />
                <button
                  type="button"
                  class={"btn btn-xs " + (props.isDataEditingDisabled ? "btn-disabled" : "btn-ghost")}
                  data-graph-edit-field="data"
                  disabled={props.isDataEditingDisabled}
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
