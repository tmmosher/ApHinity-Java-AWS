import { Index, Show } from "solid-js";
import { toInputValue } from "../../util/graphTraceEditor";

type PieTraceEditorProps = {
  rowIndexes: number[];
  labels: unknown[];
  values: unknown[];
  isBusy: boolean;
  onAddRow: () => void;
  onUpdateLabel: (rowIndex: number, rawValue: string) => void;
  onUpdateValue: (rowIndex: number, rawValue: string) => void;
  onRemoveRow: (rowIndex: number) => void;
};

const PieTraceEditor = (props: PieTraceEditorProps) => (
  <section class="space-y-3">
    <div class="flex items-center justify-between gap-2">
      <h3 class="text-sm font-semibold">Pie Values</h3>
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
            <li class="grid grid-cols-1 gap-2 rounded-lg border border-base-300 p-3 md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto]">
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
