import { Index, Show } from "solid-js";
import { toInputValue } from "../../util/graph/graphTraceEditor";

type PieTraceEditorProps = {
  rowIndexes: number[];
  labels: unknown[];
  values: unknown[];
  rowColors: string[];
  colorOptions: Record<string, string>;
  isBusy: boolean;
  onAddRow: () => void;
  onUpdateColor: (rowIndex: number, colorHex: string) => void;
  onUpdateLabel: (rowIndex: number, rawValue: string) => void;
  onUpdateValue: (rowIndex: number, rawValue: string) => void;
  onRemoveRow: (rowIndex: number) => void;
};

const PieTraceEditor = (props: PieTraceEditorProps) => (
  <section class="space-y-3">
    <div class="flex items-center justify-between gap-2">
      <div class="space-y-1">
        <h3 class="text-sm font-semibold">Pie Slices</h3>
        <p class="text-xs text-base-content/70">
          Pie graphs use one trace. Edit each slice label, value, and color directly.
        </p>
      </div>
      <button
        type="button"
        class={"btn btn-xs " + (props.isBusy ? "btn-disabled" : "btn-outline")}
        disabled={props.isBusy}
        onClick={props.onAddRow}
      >
        Add Row
      </button>
    </div>

    <Show when={props.rowIndexes.length > 0} fallback={<p class="text-sm text-base-content/70">No values in this trace.</p>}>
      <ul class="space-y-2">
        <Index each={props.rowIndexes}>
          {(_, rowIndex) => (
            <li class="grid grid-cols-1 gap-2 rounded-lg border border-base-300 p-3 md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_minmax(0,13rem)_auto]">
              <input
                class="input input-bordered input-sm"
                placeholder="Label"
                value={toInputValue(props.labels[rowIndex])}
                disabled={props.isBusy}
                onInput={(event) => props.onUpdateLabel(rowIndex, event.currentTarget.value)}
              />
              <input
                class="input input-bordered input-sm"
                type="text"
                inputmode="decimal"
                placeholder="Value"
                value={toInputValue(props.values[rowIndex])}
                disabled={props.isBusy}
                onInput={(event) => props.onUpdateValue(rowIndex, event.currentTarget.value)}
              />
              <select
                class="select select-bordered select-sm"
                value={props.rowColors[rowIndex] ?? ""}
                disabled={props.isBusy}
                onChange={(event) => {
                  const nextColor = event.currentTarget.value;
                  if (nextColor.length > 0) {
                    props.onUpdateColor(rowIndex, nextColor);
                  }
                }}
              >
                <option value="">Choose a color</option>
                <Index each={Object.entries(props.colorOptions)}>
                  {(entry) => (
                    <option value={entry()[1]}>{entry()[0]}</option>
                  )}
                </Index>
              </select>
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

export default PieTraceEditor;
