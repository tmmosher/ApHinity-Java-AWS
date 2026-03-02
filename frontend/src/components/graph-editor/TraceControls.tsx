import { For } from "solid-js";

export type TraceOption = {
  index: number;
  label: string;
};

type TraceControlsProps = {
  traceOptions: TraceOption[];
  selectedTraceIndex: number;
  selectedTraceColor: string;
  colorOptions: Record<string, string>;
  disableTraceSelect: boolean;
  disableColorSelect: boolean;
  disableRemoveTrace: boolean;
  onSelectTrace: (nextIndex: number) => void;
  onApplyColor: (colorHex: string) => void;
  onRemoveTrace: () => void;
};

const TraceControls = (props: TraceControlsProps) => (
  <div class="grid grid-cols-1 gap-2 md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto]">
    <label class="form-control">
      <span class="label-text">Trace</span>
        <select
          class="select select-bordered select-sm mt-1"
          value={String(props.selectedTraceIndex)}
          disabled={props.disableTraceSelect}
          onChange={(event) => {
            const nextIndex = Number(event.currentTarget.value);
            if (Number.isInteger(nextIndex) && nextIndex >= 0) {
              props.onSelectTrace(nextIndex);
            }
          }}
        >
        <For each={props.traceOptions}>
          {(option) => (
            <option value={String(option.index)}>{option.label}</option>
          )}
        </For>
      </select>
    </label>

    <label class="form-control">
      <span class="label-text">Trace color</span>
      <select
        class="select select-bordered select-sm mt-1"
        value={props.selectedTraceColor}
        disabled={props.disableColorSelect}
        onChange={(event) => {
          const nextColor = event.currentTarget.value;
          if (nextColor.length > 0) {
            props.onApplyColor(nextColor);
          }
        }}
      >
        <option value="">Choose a color</option>
        <For each={Object.entries(props.colorOptions)}>
          {([label, hex]) => (
            <option value={hex}>{label}</option>
          )}
        </For>
      </select>
    </label>

    <div class="flex items-end">
      <button
        type="button"
        class={"btn btn-sm " + (props.disableRemoveTrace ? "btn-disabled" : "btn-outline")}
        disabled={props.disableRemoveTrace}
        onClick={props.onRemoveTrace}
      >
        Remove Trace
      </button>
    </div>
  </div>
);

export default TraceControls;
