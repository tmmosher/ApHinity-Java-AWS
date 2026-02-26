import Dialog from "corvu/dialog";
import {Show, createEffect, createSignal} from "solid-js";
import {LocationGraph} from "../types/Types";
import {
  EditableGraphPayload,
  createEditableGraphPayload,
  parseEditableGraphPayload,
  serializeEditableGraphPayload
} from "../util/graphEditor";

type GraphEditorModalProps = {
  isOpen: boolean;
  graph: LocationGraph | undefined;
  canUndo: boolean;
  isSaving: boolean;
  onApply: (graphId: number, payload: EditableGraphPayload) => void;
  onUndo: () => void;
  onClose: () => void;
};

export const GraphEditorModal = (props: GraphEditorModalProps) => {
  const [rawPayload, setRawPayload] = createSignal("");
  const [parseError, setParseError] = createSignal("");

  createEffect(() => {
    if (!props.isOpen || !props.graph) {
      return;
    }
    setRawPayload(
      serializeEditableGraphPayload(createEditableGraphPayload(props.graph))
    );
    setParseError("");
  });

  const close = () => {
    setParseError("");
    props.onClose();
  };

  const applyEdit = () => {
    if (props.isSaving) {
      return;
    }
    const graph = props.graph;
    if (!graph) {
      return;
    }

    try {
      const parsedPayload = parseEditableGraphPayload(rawPayload());
      props.onApply(graph.id, parsedPayload);
      setParseError("");
      props.onClose();
    } catch (error) {
      setParseError(error instanceof Error ? error.message : "Unable to parse graph JSON.");
    }
  };

  return (
    <Dialog
      open={props.isOpen}
      onOpenChange={(open) => {
        if (!open) {
          close();
        }
      }}
    >
      <Dialog.Portal >
        <Dialog.Overlay
          class="fixed inset-0 z-50 bg-black/45 data-closed:pointer-events-none"
        />
        <Dialog.Content
          class="fixed inset-0 z-[60] m-auto flex h-[min(92vh,44rem)] w-[min(96vw,56rem)] flex-col gap-4 rounded-xl border border-base-300 bg-base-100 p-4 shadow-2xl data-closed:pointer-events-none md:p-5"
        >
          <div class="flex items-start justify-between gap-3">
            <div class="space-y-1">
              <Dialog.Label id="graph-editor-title" class="text-lg font-semibold">
                Edit Graph JSON
              </Dialog.Label>
              <Dialog.Description class="text-sm text-base-content/70">
                Graph: {props.graph?.name ?? "-"}
              </Dialog.Description>
            </div>
            <Dialog.Close class="btn btn-sm btn-ghost">Close</Dialog.Close>
          </div>

          <label class="form-control flex min-h-0 flex-1">
            <span class="label-text">Graph data</span>
            <textarea
              id="graph-editor-json"
              class="textarea textarea-bordered mt-1 min-h-0 flex-1 resize-none overflow-y-auto font-mono text-xs"
              value={rawPayload()}
              disabled={props.isSaving}
              onInput={(event) => setRawPayload(event.currentTarget.value)}
            />
          </label>

          <Show when={parseError()}>
            <p class="text-sm text-error">{parseError()}</p>
          </Show>

          <div class="flex flex-wrap items-center justify-end gap-2">
            <button
              type="button"
              class={"btn btn-sm " + (props.canUndo && !props.isSaving ? "btn-outline" : "btn-disabled")}
              disabled={!props.canUndo || props.isSaving}
              onClick={props.onUndo}
            >
              Undo
            </button>
            <button
              type="button"
              class={"btn btn-sm " + (props.isSaving ? "btn-disabled" : "btn-primary")}
              disabled={props.isSaving}
              onClick={applyEdit}
            >
              {props.isSaving ? "Saving..." : "Apply Edit"}
            </button>
            <Dialog.Close class="btn btn-sm">Close</Dialog.Close>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog>
  );
};

export default GraphEditorModal;
