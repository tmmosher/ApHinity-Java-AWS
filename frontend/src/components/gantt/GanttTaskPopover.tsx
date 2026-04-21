import Popover from "corvu/popover";
import {Show, createEffect, createSignal, type JSX, type ParentProps} from "solid-js";
import type {CreateLocationGanttTaskRequest} from "../../types/Types";
import {formatDisplayDate} from "../../util/location/dateUtility";
import {
  createDefaultGanttTaskDraft,
  createGanttTaskDraftFromTask,
  createLocationGanttTaskRequestFromDraft,
  type GanttTaskDraft
} from "../../util/location/ganttTaskForm";
import {SERVICE_EVENT_POPOVER_POSITION_PROPS} from "../../util/location/serviceEventPopoverPosition";
import {
  isStagedGanttTask,
  type TimelineTaskLike
} from "../../util/location/frappeGanttChart";
import {GanttTaskEditorBody, type UpdateGanttTaskDraftFn} from "./GanttTaskEditor";

type GanttTaskPopoverProps = {
  apiHost: string;
  locationId: string;
  task: TimelineTaskLike;
  canEdit: boolean;
  anchorStyle?: JSX.CSSProperties;
  onSave: (task: TimelineTaskLike, request: CreateLocationGanttTaskRequest) => Promise<void>;
  onDelete: (task: TimelineTaskLike) => Promise<void>;
  onClose: () => void;
};

type GanttTaskDetailItemProps = {
  label: string;
  value: string;
};

type GanttTaskPopoverContentProps = {
  task: TimelineTaskLike;
  canEdit: boolean;
  isDeleting: boolean;
  deletionError?: string;
  onEdit: () => void;
  onDelete: () => void;
  onClose: () => void;
};

const POPOVER_PROPS = {
  ...SERVICE_EVENT_POPOVER_POSITION_PROPS,
  trapFocus: false,
  restoreFocus: false,
  closeOnOutsideFocus: false
};

const GanttTaskDetailItem = (props: GanttTaskDetailItemProps) => (
  <div class="space-y-1">
    <dt class="text-xs font-semibold uppercase tracking-[0.16em] text-base-content/55">
      {props.label}
    </dt>
    <dd class="text-base-content/80">{props.value}</dd>
  </div>
);

const formatTaskDateRange = (task: TimelineTaskLike): string => (
  `${formatDisplayDate(task.startDate)} - ${formatDisplayDate(task.endDate)}`
);

const GanttTaskPopoverContent = (props: GanttTaskPopoverContentProps) => (
  <div class="space-y-4 p-4 md:p-5">
    <div class="flex items-start justify-between gap-4">
      <div class="min-w-0 space-y-1">
        <Popover.Label class="text-base font-semibold leading-tight">
          {props.task.title}
        </Popover.Label>
        <Popover.Description class="text-sm text-base-content/70">
          {formatTaskDateRange(props.task)}
        </Popover.Description>
      </div>

      <div class="flex items-start gap-2">
        <Show when={isStagedGanttTask(props.task)}>
          <span class="rounded-full border border-primary/30 bg-primary/10 px-2 py-1 text-[10px] font-semibold uppercase tracking-[0.12em] text-primary">
            Staged
          </span>
        </Show>

        <Show when={props.canEdit}>
          <button
            type="button"
            class="btn btn-primary btn-xs"
            data-gantt-task-edit=""
            disabled={props.isDeleting}
            onClick={(event) => {
              event.stopPropagation();
              props.onEdit();
            }}
          >
            Edit
          </button>
        </Show>
      </div>
    </div>

    <dl class="grid gap-3 text-sm md:grid-cols-2">
      <GanttTaskDetailItem label="Start" value={formatDisplayDate(props.task.startDate)} />
      <GanttTaskDetailItem label="End" value={formatDisplayDate(props.task.endDate)} />
    </dl>

    <div class="space-y-1">
      <p class="text-xs font-semibold uppercase tracking-[0.16em] text-base-content/55">
        Description
      </p>
      <p class="text-sm leading-6 text-base-content/80">
        {props.task.description ?? "No description provided."}
      </p>
    </div>

    <Show when={props.deletionError}>
      {(message) => (
        <p
          role="alert"
          class="rounded-xl border border-error/25 bg-error/10 px-3 py-2 text-sm text-error"
        >
          {message()}
        </p>
      )}
    </Show>

    <div class="flex items-center justify-between gap-2">
      <Show when={props.canEdit}>
        <button
          type="button"
          class="btn btn-error btn-outline btn-sm"
          disabled={props.isDeleting}
          onClick={(event) => {
            event.stopPropagation();
            props.onDelete();
          }}
        >
          {props.isDeleting ? "Deleting..." : "Delete"}
        </button>
      </Show>

      <div class="ml-auto flex items-center gap-2">
        <button
          type="button"
          class="btn btn-ghost btn-sm"
          disabled={props.isDeleting}
          onClick={props.onClose}
        >
          Close
        </button>
      </div>
    </div>
  </div>
);

export const requestGanttTaskEdit = (setIsEditing: (isEditing: boolean) => void): void => {
  setIsEditing(true);
};

export const GanttTaskPopover = (props: ParentProps<GanttTaskPopoverProps>) => {
  const [isEditing, setIsEditing] = createSignal(false);
  const [draft, setDraft] = createSignal<GanttTaskDraft>(createDefaultGanttTaskDraft());
  const [isSaving, setIsSaving] = createSignal(false);
  const [isDeleting, setIsDeleting] = createSignal(false);
  const [submissionError, setSubmissionError] = createSignal<string>();
  const [deletionError, setDeletionError] = createSignal<string>();

  createEffect(() => {
    setDraft(createGanttTaskDraftFromTask(props.task));
    setIsSaving(false);
    setIsDeleting(false);
    setSubmissionError(undefined);
    setDeletionError(undefined);
    setIsEditing(false);
  });

  const close = () => {
    if (isSaving() || isDeleting()) {
      return;
    }

    setSubmissionError(undefined);
    setDeletionError(undefined);
    setIsEditing(false);
    props.onClose();
  };

  const save = async () => {
    if (!props.canEdit || isSaving()) {
      return;
    }

    setIsSaving(true);
    setSubmissionError(undefined);
    setDeletionError(undefined);
    try {
      await props.onSave(props.task, createLocationGanttTaskRequestFromDraft(draft()));
      props.onClose();
    } catch (error) {
      setSubmissionError(error instanceof Error ? error.message : "Unable to save gantt task.");
    } finally {
      setIsSaving(false);
    }
  };

  const remove = async () => {
    if (!props.canEdit || isDeleting()) {
      return;
    }

    setIsDeleting(true);
    setSubmissionError(undefined);
    setDeletionError(undefined);
    try {
      await props.onDelete(props.task);
      props.onClose();
    } catch (error) {
      setDeletionError(error instanceof Error ? error.message : "Unable to delete gantt task.");
    } finally {
      setIsDeleting(false);
    }
  };

  const updateDraft: UpdateGanttTaskDraftFn = (key, value) => {
    setDraft((current) => ({...current, [key]: value}));
    if (submissionError()) {
      setSubmissionError(undefined);
    }
  };

  return (
    <Popover
      {...POPOVER_PROPS}
      open={props.task !== undefined}
      onOpenChange={(open) => {
        if (!open) {
          close();
        }
      }}
    >
      <Popover.Trigger
        as="button"
        type="button"
        aria-hidden="true"
        tabIndex={-1}
        class="pointer-events-none fixed z-[59] opacity-0"
        style={props.anchorStyle ?? {left: "0px", top: "0px", width: "1px", height: "1px"}}
      />

      <Popover.Portal>
        <Popover.Content
          class="z-50 w-[min(96vw,28rem)] rounded-2xl border border-base-300 bg-base-100 shadow-2xl"
          data-gantt-task-popover=""
        >
          <Show
            when={isEditing()}
            fallback={
              <GanttTaskPopoverContent
                task={props.task}
                canEdit={props.canEdit}
                isDeleting={isDeleting()}
                deletionError={deletionError()}
                onEdit={() => {
                  setSubmissionError(undefined);
                  setDeletionError(undefined);
                  requestGanttTaskEdit(setIsEditing);
                }}
                onDelete={() => {
                  void remove();
                }}
                onClose={close}
              />
            }
          >
            <div class="flex max-h-[min(80vh,38rem)] min-h-0 flex-col gap-4 p-4">
              <div class="space-y-1">
                <Popover.Label class="text-lg font-semibold">Edit Gantt Task</Popover.Label>
                <Popover.Description class="text-sm text-base-content/70">
                  Update the gantt task details for this location.
                </Popover.Description>
              </div>

              <GanttTaskEditorBody
                draft={draft()}
                isSaving={isSaving()}
                isDeleting={isDeleting()}
                submissionError={submissionError()}
                deletionError={deletionError()}
                saveLabel="Save"
                deleteLabel="Delete"
                onCancel={close}
                onSubmit={() => {
                  void save();
                }}
                onDelete={() => {
                  void remove();
                }}
                dependencyDialogApiHost={props.apiHost}
                dependencyDialogLocationId={props.locationId}
                dependencyDialogCurrentTaskId={props.task.id}
                dependencyDialogCurrentTaskTitle={draft().title || props.task.title}
                updateDraft={updateDraft}
              />
            </div>
          </Show>
        </Popover.Content>
      </Popover.Portal>
    </Popover>
  );
};

export default GanttTaskPopover;
