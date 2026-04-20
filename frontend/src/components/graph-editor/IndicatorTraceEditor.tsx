import {For} from "solid-js";
import {INDICATOR_VALUE_MAX, INDICATOR_VALUE_MIN} from "../../util/graph/graphTemplateFactory";
import {toInputValue} from "../../util/graph/graphTraceEditor";

type IndicatorTraceEditorProps = {
  value: unknown;
  valueDraft?: string;
  color: string;
  colorOptions: Record<string, string>;
  isBusy: boolean;
  onUpdateValue: (rawValue: string) => void;
  onUpdateColor: (colorHex: string) => void;
};

const IndicatorTraceEditor = (props: IndicatorTraceEditorProps) => {
  return (
    <section class="space-y-3">
      <div class="space-y-1">
        <h3 class="text-sm font-semibold">Indicator</h3>
        <p class="text-xs text-base-content/70">
          Indicator graphs use a single percentage value. Update the value and gauge color here.
        </p>
        <p class="text-xs text-base-content/60">
          Allowed range: {INDICATOR_VALUE_MIN} to {INDICATOR_VALUE_MAX}.
        </p>
      </div>

      <div class="grid grid-cols-1 gap-2 rounded-lg border border-base-300 p-3 md:grid-cols-2">
        <label class="form-control">
          <span class="label-text">Value</span>
          <input
            class={"input input-bordered input-sm mt-1" + (props.valueDraft !== undefined ? " input-error" : "")}
            aria-invalid={props.valueDraft !== undefined}
            type="text"
            inputmode="decimal"
            placeholder={`${INDICATOR_VALUE_MIN}-${INDICATOR_VALUE_MAX}`}
            value={props.valueDraft ?? toInputValue(props.value)}
            disabled={props.isBusy}
            onInput={(event) => props.onUpdateValue(event.currentTarget.value)}
          />
        </label>

        <label class="form-control">
          <span class="label-text">Gauge color</span>
          <select
            class="select select-bordered select-sm mt-1"
            value={props.color}
            disabled={props.isBusy}
            onChange={(event) => {
              const nextColor = event.currentTarget.value;
              if (nextColor.length > 0) {
                props.onUpdateColor(nextColor);
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
    </section>
  );
};

export default IndicatorTraceEditor;
