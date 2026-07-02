import {Index, Show} from "solid-js";
import {toInputValue} from "../../util/graph/graphTraceEditor";

type TableTraceEditorProps = {
  headers: unknown[];
  columns: unknown[][];
  rowIndexes: number[];
  isDataEditingDisabled: boolean;
  onAddColumn: () => void;
  onRemoveColumn: (columnIndex: number) => void;
  onUpdateHeader: (columnIndex: number, rawValue: string) => void;
  onAddRow: () => void;
  onUpdateCell: (rowIndex: number, columnIndex: number, rawValue: string) => void;
  onRemoveRow: (rowIndex: number) => void;
};

const TableTraceEditor = (props: TableTraceEditorProps) => (
  <section class="space-y-3">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <h3 class="text-sm font-semibold">Table Values</h3>
      <div class="flex flex-wrap items-center gap-2">
        <button
          type="button"
          class={"btn btn-xs " + (props.isDataEditingDisabled ? "btn-disabled" : "btn-outline")}
          data-graph-edit-field="data"
          disabled={props.isDataEditingDisabled}
          onClick={props.onAddColumn}
        >
          Add Column
        </button>
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
    </div>

    <Show when={props.headers.length > 0} fallback={<p class="text-sm text-base-content/70">No columns in this table.</p>}>
      <div class="space-y-3 overflow-x-auto">
        <div
          class="grid min-w-[36rem] gap-2 rounded-lg border border-base-300 p-3"
          style={{"grid-template-columns": `repeat(${props.headers.length}, minmax(9rem, 1fr)) auto`}}
        >
          <Index each={props.headers}>
            {(header, columnIndex) => (
              <input
                class="input input-bordered input-sm"
                data-graph-edit-field="data"
                placeholder={`Column ${columnIndex + 1}`}
                value={toInputValue(header())}
                disabled={props.isDataEditingDisabled}
                onInput={(event) => props.onUpdateHeader(columnIndex, event.currentTarget.value)}
              />
            )}
          </Index>
          <span class="sr-only">Actions</span>

          <Index each={props.rowIndexes}>
            {(_, rowIndex) => (
              <>
                <Index each={props.headers}>
                  {(_header, columnIndex) => (
                    <input
                      class="input input-bordered input-sm"
                      data-graph-edit-field="data"
                      placeholder="Cell value"
                      value={toInputValue(props.columns[columnIndex]?.[rowIndex])}
                      disabled={props.isDataEditingDisabled}
                      onInput={(event) => props.onUpdateCell(rowIndex, columnIndex, event.currentTarget.value)}
                    />
                  )}
                </Index>
                <button
                  type="button"
                  class={"btn btn-xs " + (props.isDataEditingDisabled ? "btn-disabled" : "btn-ghost")}
                  data-graph-edit-field="data"
                  disabled={props.isDataEditingDisabled}
                  onClick={() => props.onRemoveRow(rowIndex)}
                >
                  Remove
                </button>
              </>
            )}
          </Index>
        </div>
      </div>

      <div class="flex flex-wrap gap-2">
        <Index each={props.headers}>
          {(header, columnIndex) => (
            <button
              type="button"
              class={"btn btn-xs " + (props.isDataEditingDisabled || props.headers.length <= 1 ? "btn-disabled" : "btn-ghost")}
              data-graph-edit-field="data"
              disabled={props.isDataEditingDisabled || props.headers.length <= 1}
              onClick={() => props.onRemoveColumn(columnIndex)}
            >
              Remove {toInputValue(header()) || `Column ${columnIndex + 1}`}
            </button>
          )}
        </Index>
      </div>
    </Show>
  </section>
);

export default TableTraceEditor;
