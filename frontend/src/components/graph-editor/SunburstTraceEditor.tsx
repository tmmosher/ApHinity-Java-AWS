import {Index, Show} from "solid-js";
import {toInputValue} from "../../util/graph/graphTraceEditor";
import GraphColorPicker from "./GraphColorPicker";

type SunburstTraceEditorProps = {
  labels: string[];
  colors: string[];
  colorOptions: Record<string, string>;
  isDataEditingDisabled: boolean;
  onUpdateColor: (label: string, colorHex: string) => void;
};

const SunburstTraceEditor = (props: SunburstTraceEditorProps) => (
  <section class="space-y-3">
    <div class="space-y-1">
      <h3 class="text-sm font-semibold">Sunburst Nodes</h3>
      <p class="text-xs text-base-content/70">
        Sunburst hierarchy and values are generated from the graph data. Each shared node name appears once;
        changing its color updates every matching node.
      </p>
    </div>

    <Show
      when={props.labels.length > 0}
      fallback={<p class="text-sm text-base-content/70">No nodes in this trace.</p>}
    >
      <ul class="space-y-2">
        <Index each={props.labels}>
          {(label, labelIndex) => (
            <li class="grid grid-cols-1 items-end gap-2 rounded-lg border border-base-300 p-3 md:grid-cols-[minmax(0,1fr)_minmax(0,13rem)]">
              <label class="form-control">
                <span class="label-text">Node</span>
                <input
                  class="input input-bordered input-sm mt-1"
                  value={toInputValue(label())}
                  disabled
                  aria-label={`Sunburst node ${label()}`}
                />
              </label>
              <label class="form-control" data-graph-edit-field="data">
                <span class="label-text">Node color</span>
                <GraphColorPicker
                  value={props.colors[labelIndex] ?? ""}
                  disabled={props.isDataEditingDisabled}
                  colorOptions={props.colorOptions}
                  onChange={(nextColor) => props.onUpdateColor(label(), nextColor)}
                />
              </label>
            </li>
          )}
        </Index>
      </ul>
    </Show>
  </section>
);

export default SunburstTraceEditor;
