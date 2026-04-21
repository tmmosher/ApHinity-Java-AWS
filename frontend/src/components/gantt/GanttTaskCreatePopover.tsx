import Popover from "corvu/popover";
import {createEffect, createSignal, on} from "solid-js";
import type {CreateLocationGanttTaskRequest} from "../../types/Types";
import {
  createDefaultGanttTaskDraft,
  createLocationGanttTaskRequestFromDraft,
  type GanttTaskDraft
} from "../../util/location/ganttTaskForm";
import {SERVICE_EVENT_POPOVER_POSITION_PROPS} from "../../util/location/serviceEventPopoverPosition";
import {GanttTaskEditorBody, type UpdateGanttTaskDraftFn} from "./GanttTaskEditor";

type GanttTaskCreatePopoverProps = {
  apiHost: string;
  locationId: string;
  onCreate: (request: CreateLocationGanttTaskRequest) => Promise<void>;
};

const CREATE_POPOVER_PROPS = {
  ...SERVICE_EVENT_POPOVER_POSITION_PROPS,
  trapFocus: false,
  restoreFocus: false
};

export const GanttTaskCreatePopover = (props: GanttTaskCreatePopoverProps) => {
  const [isOpen, setIsOpen] = createSignal(false);
  const [draft, setDraft] = createSignal<GanttTaskDraft>(createDefaultGanttTaskDraft());
  const [isSaving, setIsSaving] = createSignal(false);
  const [submissionError, setSubmissionError] = createSignal<string>();

  const reset = () => {
    setDraft(createDefaultGanttTaskDraft());
    setSubmissionError(undefined);
  };

  createEffect(on(isOpen, (open) => {
    if (open) {
      reset();
    }
  }));

  const close = () => {
    if (isSaving()) {
      return;
    }

    reset();
    setIsOpen(false);
  };

  const submit = async () => {
    if (isSaving()) {
      return;
    }

    setIsSaving(true);
    setSubmissionError(undefined);
    try {
      await props.onCreate(createLocationGanttTaskRequestFromDraft(draft()));
      reset();
      setIsOpen(false);
    } catch (error) {
      setSubmissionError(error instanceof Error ? error.message : "Unable to create gantt task.");
    } finally {
      setIsSaving(false);
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
      {...CREATE_POPOVER_PROPS}
      open={isOpen()}
      onOpenChange={(open) => {
        if (open) {
          setIsOpen(true);
          return;
        }
        close();
      }}
    >
      <Popover.Trigger
        as="button"
        type="button"
        class="btn btn-primary btn-sm rounded-2xl"
        data-gantt-task-create-trigger=""
      >
        Add Task
      </Popover.Trigger>

      <Popover.Portal>
        <Popover.Content
          class="z-50 w-[min(96vw,28rem)] rounded-2xl border border-base-300 bg-base-100 shadow-2xl"
          data-gantt-task-create-popover=""
        >
          <div class="flex max-h-[min(80vh,38rem)] min-h-0 flex-col gap-4 p-4">
            <div class="space-y-1">
              <Popover.Label class="text-lg font-semibold">Add Gantt Task</Popover.Label>
              <Popover.Description class="text-sm text-base-content/70">
                Create a new gantt task for this location.
              </Popover.Description>
            </div>

            <GanttTaskEditorBody
              draft={draft()}
              isSaving={isSaving()}
              submissionError={submissionError()}
              saveLabel="Add Task"
              onCancel={close}
              onSubmit={() => {
                void submit();
              }}
              dependencyDialogApiHost={props.apiHost}
              dependencyDialogLocationId={props.locationId}
              dependencyDialogCurrentTaskTitle={draft().title || "this new gantt task"}
              updateDraft={updateDraft}
            />
          </div>
        </Popover.Content>
      </Popover.Portal>
    </Popover>
  );
};

export default GanttTaskCreatePopover;
