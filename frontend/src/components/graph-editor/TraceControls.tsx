import { For, Index } from "solid-js";

export type TraceOption = {
  index: number;
  label: string;
};

type TraceControlsProps = {
  traceOptions: TraceOption[];
  selectedTraceIndex: number;
  traceNameDraft: string;
  selectedTraceColor: string;
  colorOptions: Record<string, string>;
  disableAddTrace: boolean;
  disableTraceSelect: boolean;
  disableColorSelect: boolean;
  disableTraceNameInput: boolean;
  disableRenameTrace: boolean;
  disableRemoveTrace: boolean;
  onAddTrace: () => void;
  onSelectTrace: (nextIndex: number) => void;
  onChangeTraceName: (nextName: string) => void;
  onApplyColor: (colorHex: string) => void;
  onRenameTrace: () => void;
  onRemoveTrace: () => void;
};

const TraceControls = (props: TraceControlsProps) => (
  <div class="space-y-2">
    <div class="grid grid-cols-1 gap-2 md:grid-cols-3">
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
          <Index each={props.traceOptions}>
            {(option, index) => (
              <option value={String(index)}>{option().label}</option>
            )}
          </Index>
        </select>
      </label>

      <label class="form-control">
        <span class="label-text">Trace name</span>
        <input
          class="input input-bordered input-sm mt-1"
          type="text"
          value={props.traceNameDraft}
          disabled={props.disableTraceNameInput}
          onInput={(event) => props.onChangeTraceName(event.currentTarget.value)}
        />
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
    </div>

    <div class="flex flex-wrap items-center gap-2">
      <button
        type="button"
        class={"btn btn-sm " + (props.disableAddTrace ? "btn-disabled" : "btn-outline")}
        disabled={props.disableAddTrace}
        onClick={props.onAddTrace}
      >
        Add Trace
      </button>
      <button
        type="button"
        class={"btn btn-sm " + (props.disableRenameTrace ? "btn-disabled" : "btn-outline")}
        disabled={props.disableRenameTrace}
        onClick={props.onRenameTrace}
      >
        Rename Trace
      </button>
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
