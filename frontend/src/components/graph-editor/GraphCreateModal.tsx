import Dialog from "corvu/dialog";
import {For, Show, createEffect, createSignal} from "solid-js";
import type {LocationGraphType} from "../../types/Types";

const NEW_SECTION_VALUE = "__new_section__";

type GraphCreateModalProps = {
  isOpen: boolean;
  isCreating: boolean;
  sectionOptions: Array<{
    id: number;
    label: string;
  }>;
  nextSectionId: number;
  onCreate: (request: {
    graphType: LocationGraphType;
    sectionId?: number;
    createNewSection: boolean;
  }) => Promise<void>;
  onClose: () => void;
};

const GRAPH_TYPE_OPTIONS: Array<{value: LocationGraphType; label: string}> = [
  {value: "pie", label: "Pie"},
  {value: "bar", label: "Bar"},
  {value: "scatter", label: "Scatter"}
];

export const GraphCreateModal = (props: GraphCreateModalProps) => {
  const [selectedSectionId, setSelectedSectionId] = createSignal("");
  const [selectedGraphType, setSelectedGraphType] = createSignal<LocationGraphType>("pie");
  const [createError, setCreateError] = createSignal("");

  createEffect(() => {
    if (!props.isOpen) {
      return;
    }

    const firstSection = props.sectionOptions[0];
    setSelectedSectionId(firstSection ? String(firstSection.id) : NEW_SECTION_VALUE);
    setSelectedGraphType("pie");
    setCreateError("");
  });

  const close = () => {
    if (props.isCreating) {
      return;
    }
    setCreateError("");
    props.onClose();
  };

  const createGraph = async () => {
    if (props.isCreating) {
      return;
    }

    const selection = selectedSectionId();
    if (!selection) {
      setCreateError("Select a dashboard section.");
      return;
    }

    setCreateError("");

    try {
      if (selection === NEW_SECTION_VALUE) {
        await props.onCreate({
          graphType: selectedGraphType(),
          createNewSection: true
        });
        return;
      }

      const sectionId = Number(selection);
      if (!Number.isInteger(sectionId) || sectionId <= 0) {
        setCreateError("Select a dashboard section.");
        return;
      }

      await props.onCreate({
        graphType: selectedGraphType(),
        sectionId,
        createNewSection: false
      });
    } catch (error) {
      setCreateError(error instanceof Error ? error.message : "Unable to create graph.");
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
      <Dialog.Portal>
        <Dialog.Overlay class="fixed inset-0 z-50 bg-black/45 data-closed:pointer-events-none" />
        <Dialog.Content
          class="fixed inset-0 z-[60] m-auto flex h-fit w-[min(96vw,28rem)] flex-col gap-4 rounded-xl border border-base-300 bg-base-100 p-4 shadow-2xl data-closed:pointer-events-none md:p-5"
        >
          <div class="space-y-1">
            <Dialog.Label class="text-lg font-semibold">Add Graph</Dialog.Label>
            <Dialog.Description class="text-sm text-base-content/70">
              Create a new graph and place it into a dashboard section.
            </Dialog.Description>
          </div>

          <div class="space-y-4">
            <label class="form-control w-full">
              <span class="label-text text-sm">Graph type</span>
              <select
                class="select select-bordered w-full"
                value={selectedGraphType()}
                disabled={props.isCreating}
                onChange={(event) => setSelectedGraphType(event.currentTarget.value as LocationGraphType)}
              >
                <For each={GRAPH_TYPE_OPTIONS}>
                  {(option) => (
                    <option value={option.value}>{option.label}</option>
                  )}
                </For>
              </select>
            </label>

            <label class="form-control w-full">
              <span class="label-text text-sm">Section</span>
              <select
                class="select select-bordered w-full"
                value={selectedSectionId()}
                disabled={props.isCreating}
                onChange={(event) => setSelectedSectionId(event.currentTarget.value)}
              >
                <For each={props.sectionOptions}>
                  {(option) => (
                    <option value={option.id}>{option.label}</option>
                  )}
                </For>
                <option value={NEW_SECTION_VALUE}>
                  Create Section {props.nextSectionId}
                </option>
              </select>
              <span class="label-text-alt text-xs text-base-content/70">
                {selectedSectionId() === NEW_SECTION_VALUE
                  ? `A new dashboard section will be created as Section ${props.nextSectionId}.`
                  : "Choose an existing section or create a new one."}
              </span>
            </label>

            <Show when={createError()}>
              <p class="text-sm text-error">{createError()}</p>
            </Show>
          </div>

          <div class="flex flex-wrap items-center justify-end gap-2">
            <button
              type="button"
              class="btn btn-sm btn-ghost"
              disabled={props.isCreating}
              onClick={close}
            >
              Cancel
            </button>
            <button
              type="button"
              class={"btn btn-sm " + (props.isCreating ? "btn-disabled" : "btn-primary")}
              disabled={props.isCreating}
              onClick={() => void createGraph()}
            >
              {props.isCreating ? "Creating..." : "Create Graph"}
            </button>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog>
  );
};

export default GraphCreateModal;
