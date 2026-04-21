import {Show} from "solid-js";
import {GanttTaskDependenciesDialog} from "./GanttTaskDependenciesDialog";
import {
  GANTT_TASK_TITLE_MAX_LENGTH,
  type GanttTaskDraft
} from "../../util/location/ganttTaskForm";

export type UpdateGanttTaskDraftFn = <Key extends keyof GanttTaskDraft>(
  key: Key,
  value: GanttTaskDraft[Key]
) => void;

type GanttTaskEditorBodyProps = {
  draft: GanttTaskDraft;
  isSaving: boolean;
  isDeleting?: boolean;
  submissionError?: string;
  deletionError?: string;
  saveLabel: string;
  deleteLabel?: string;
  onCancel: () => void;
  onSubmit: () => void;
  onDelete?: () => void;
  dependencyDialogApiHost: string;
  dependencyDialogLocationId: string;
  dependencyDialogCurrentTaskId?: number;
  dependencyDialogCurrentTaskTitle: string;
  updateDraft: UpdateGanttTaskDraftFn;
};

export const GanttTaskEditorBody = (props: GanttTaskEditorBodyProps) => {
  const isDeleting = () => props.isDeleting ?? false;
  const isBusy = () => props.isSaving || isDeleting();

  return (
    <form
      class="space-y-3"
      onSubmit={(event) => {
        event.preventDefault();
        props.onSubmit();
      }}
    >
      <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <label class="form-control w-full sm:col-span-2">
          <div class="mb-1 flex items-center justify-between gap-2">
            <span class="label-text text-sm">Title</span>
            <GanttTaskDependenciesDialog
              apiHost={props.dependencyDialogApiHost}
              locationId={props.dependencyDialogLocationId}
              currentTaskId={props.dependencyDialogCurrentTaskId}
              currentTaskTitle={props.dependencyDialogCurrentTaskTitle}
              selectedDependencyTaskIds={props.draft.dependencyTaskIds}
              disabled={isBusy()}
              onApply={(dependencyTaskIds) => {
                props.updateDraft("dependencyTaskIds", dependencyTaskIds);
              }}
            />
          </div>
          <input
            type="text"
            class="input input-bordered w-full"
            maxlength={GANTT_TASK_TITLE_MAX_LENGTH}
            value={props.draft.title}
            disabled={isBusy()}
            onInput={(event) => {
              props.updateDraft("title", event.currentTarget.value);
            }}
            required
          />
        </label>

        <label class="form-control w-full">
          <span class="label-text text-sm">Start date</span>
          <input
            type="date"
            class="input input-bordered w-full"
            value={props.draft.startDate}
            disabled={isBusy()}
            onInput={(event) => {
              props.updateDraft("startDate", event.currentTarget.value);
            }}
            required
          />
        </label>

        <label class="form-control w-full">
          <span class="label-text text-sm">End date</span>
          <input
            type="date"
            class="input input-bordered w-full"
            value={props.draft.endDate}
            disabled={isBusy()}
            onInput={(event) => {
              props.updateDraft("endDate", event.currentTarget.value);
            }}
            required
          />
        </label>

        <label class="form-control w-full sm:col-span-2">
          <span class="label-text text-sm">Description</span>
          <textarea
            class="textarea textarea-bordered min-h-[6rem] w-full"
            value={props.draft.description}
            disabled={isBusy()}
            onInput={(event) => {
              props.updateDraft("description", event.currentTarget.value);
            }}
          />
        </label>
      </div>

      <Show when={props.submissionError}>
        {(message) => <p class="text-sm text-error">{message()}</p>}
      </Show>

      <Show when={props.deletionError}>
        {(message) => <p class="text-sm text-error">{message()}</p>}
      </Show>

      <div class="flex flex-wrap items-center justify-between gap-2">
        <Show when={props.onDelete}>
          <button
            type="button"
            class="btn btn-error btn-outline btn-sm"
            disabled={isBusy()}
            onClick={() => {
              props.onDelete?.();
            }}
          >
            {isDeleting() ? "Deleting..." : (props.deleteLabel ?? "Delete")}
          </button>
        </Show>

        <div class="ml-auto flex flex-wrap items-center gap-2">
          <button
            type="button"
            class="btn btn-ghost btn-sm"
            disabled={isBusy()}
            onClick={props.onCancel}
          >
            Cancel
          </button>
          <button type="submit" class="btn btn-primary btn-sm" disabled={isBusy()}>
            {props.isSaving ? "Saving..." : props.saveLabel}
          </button>
        </div>
      </div>
    </form>
  );
};
