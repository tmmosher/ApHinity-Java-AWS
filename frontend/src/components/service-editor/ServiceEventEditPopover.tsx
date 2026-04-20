import Popover from "corvu/popover";
import {Match, Show, Switch, createMemo, createSignal, type ParentProps} from "solid-js";
import type {AccountRole, CreateLocationServiceEventRequest, LocationServiceEvent} from "../../types/Types";
import {
  formatDisplayDate,
  formatDisplayTime
} from "../../util/location/dateUtility";
import {createServiceEventDraftFromEvent} from "../../util/location/serviceEventForm";
import {
  createCorrectiveActionDraftFromSourceEvent,
  getCorrectiveActionSourceLabel,
  isCorrectiveActionServiceEvent
} from "../../util/location/serviceEventCorrectiveAction";
import {
  createServiceEventEditorController,
  ServiceEventEditorBody
} from "./ServiceEventEditor";
import {SERVICE_EVENT_POPOVER_POSITION_PROPS} from "../../util/location/serviceEventPopoverPosition";
import CorrectiveActionIcon from "./CorrectiveActionIcon";

type ServiceEventEditPopoverProps = {
  event: LocationServiceEvent;
  canEdit: boolean;
  canComplete: boolean;
  canDelete?: boolean;
  role: AccountRole | undefined;
  onSave?: (event: LocationServiceEvent, request: CreateLocationServiceEventRequest) => Promise<void>;
  onCreateCorrectiveAction?: (
    sourceEvent: LocationServiceEvent,
    request: CreateLocationServiceEventRequest
  ) => Promise<void>;
  onComplete?: (event: LocationServiceEvent) => Promise<void>;
  onDelete?: (event: LocationServiceEvent) => Promise<void>;
  deleteLabel?: string;
};

type ServiceEventPopoverContentProps = {
  event: LocationServiceEvent;
  canEdit: boolean;
  canComplete: boolean;
  canDelete: boolean;
  canCreateCorrectiveAction: boolean;
  isCompleting: boolean;
  isDeleting: boolean;
  completionError?: string;
  deletionError?: string;
  sourceEventLabel?: string;
  onEdit: () => void;
  onCreateCorrectiveAction: () => void;
  onComplete: () => void;
  onDelete: () => void;
  deleteLabel: string;
};

const EVENT_POPOVER_PROPS = {
  ...SERVICE_EVENT_POPOVER_POSITION_PROPS,
  trapFocus: false,
  restoreFocus: false,
  closeOnOutsideFocus: false
};

const formatResponsibilityLabel = (responsibility: LocationServiceEvent["responsibility"]): string => (
  responsibility === "client" ? "Client" : "Partner"
);

const formatStatusLabel = (status: LocationServiceEvent["status"]): string => (
  status.charAt(0).toUpperCase() + status.slice(1)
);

const formatEventDateTime = (date: string, time: string): string => (
  `${formatDisplayDate(date)} at ${formatDisplayTime(time)}`
);

const isStagedCalendarEvent = (event: LocationServiceEvent): boolean => (
  "isStaged" in event && event.isStaged === true
);

const ServiceEventDetailItem = (props: {label: string; value: string}) => (
  <div class="space-y-1">
    <dt class="text-xs font-semibold uppercase tracking-[0.16em] text-base-content/55">
      {props.label}
    </dt>
    <dd class="text-base-content/80">{props.value}</dd>
  </div>
);

const ServiceEventPopoverContent = (props: ServiceEventPopoverContentProps) => (
  <div class="space-y-4 p-4 md:p-5">
    <div class="flex items-start gap-4">
      <div class="min-w-0 flex-1">
        <Popover.Label class="flex min-w-0 items-center gap-2 text-base font-semibold leading-tight">
          <Show when={isCorrectiveActionServiceEvent(props.event)}>
            <CorrectiveActionIcon class="size-4 shrink-0 text-warning" />
          </Show>
          <span class="min-w-0 flex-1 truncate">{props.event.title}</span>
        </Popover.Label>
      </div>

      <div class="flex shrink-0 items-center gap-2">
        <Show when={props.canCreateCorrectiveAction}>
          <button
            type="button"
            class="btn btn-warning btn-outline btn-xs"
            data-service-event-create-corrective-action=""
            disabled={props.isCompleting || props.isDeleting}
            onClick={(event) => {
              event.stopPropagation();
              props.onCreateCorrectiveAction();
            }}
          >
            Create Corrective Action
          </button>
        </Show>
        <Show when={props.canEdit}>
          <button
            type="button"
            class="btn btn-primary btn-xs"
            data-service-event-edit=""
            disabled={props.isCompleting || props.isDeleting}
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
      <ServiceEventDetailItem
        label="Start"
        value={formatEventDateTime(props.event.date, props.event.time)}
      />
      <ServiceEventDetailItem
        label="End"
        value={formatEventDateTime(props.event.endDate, props.event.endTime)}
      />
      <ServiceEventDetailItem
        label="Responsibility"
        value={formatResponsibilityLabel(props.event.responsibility)}
      />
      <ServiceEventDetailItem
        label="Status"
        value={formatStatusLabel(props.event.status)}
      />
      <Show when={props.sourceEventLabel}>
        {(sourceEventLabel) => (
          <ServiceEventDetailItem
            label="Source Event"
            value={sourceEventLabel()}
          />
        )}
      </Show>
    </dl>

    <div class="space-y-1">
      <p class="text-xs font-semibold uppercase tracking-[0.16em] text-base-content/55">
        Description
      </p>
      <Popover.Description class="text-sm leading-6 text-base-content/80">
        {props.event.description ?? "No description provided."}
      </Popover.Description>
    </div>

    <Show when={props.completionError}>
      {(message) => (
        <p
          role="alert"
          class="rounded-xl border border-error/25 bg-error/10 px-3 py-2 text-sm text-error"
        >
          {message()}
        </p>
      )}
    </Show>

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

    <Show when={props.canComplete || props.canDelete}>
      <div class="flex items-center justify-between gap-2">
        <Show when={props.canDelete}>
          <button
            type="button"
            class="btn btn-error btn-outline btn-sm"
            data-service-event-delete=""
            disabled={props.isCompleting || props.isDeleting}
            onClick={(event) => {
              event.stopPropagation();
              props.onDelete();
            }}
          >
            {props.isDeleting ? "Deleting..." : props.deleteLabel}
          </button>
        </Show>

        <Show when={props.canComplete}>
          <button
            type="button"
            class="btn btn-success btn-sm"
            data-service-event-complete=""
            disabled={props.isCompleting || props.isDeleting}
            onClick={(event) => {
              event.stopPropagation();
              props.onComplete();
            }}
          >
            {props.isCompleting ? "Marking Complete..." : "Mark Complete"}
          </button>
        </Show>
      </div>
    </Show>
  </div>
);

export const requestServiceEventEdit = (setIsEditing: (isEditing: boolean) => void): void => {
  setIsEditing(true);
};

export const ServiceEventEditPopover = (props: ParentProps<ServiceEventEditPopoverProps>) => {
  const [isEditing, setIsEditing] = createSignal(false);
  const [isCreatingCorrectiveAction, setIsCreatingCorrectiveAction] = createSignal(false);
  const [isCompleting, setIsCompleting] = createSignal(false);
  const [isDeleting, setIsDeleting] = createSignal(false);
  const [completionError, setCompletionError] = createSignal<string>();
  const [deletionError, setDeletionError] = createSignal<string>();
  const controller = createServiceEventEditorController({
    role: () => props.role,
    getInitialDraft: () => createServiceEventDraftFromEvent(props.event),
    onSave: (request) => {
      if (!props.onSave) {
        throw new Error("Editing is unavailable.");
      }
      return props.onSave(props.event, request);
    }
  });
  const correctiveActionController = createServiceEventEditorController({
    role: () => props.role,
    getInitialDraft: () => createCorrectiveActionDraftFromSourceEvent(props.event, props.role),
    onSave: (request) => {
      if (!props.onCreateCorrectiveAction) {
        throw new Error("Corrective action creation is unavailable.");
      }
      return props.onCreateCorrectiveAction(props.event, request);
    }
  });
  const sourceEventLabel = createMemo(() => getCorrectiveActionSourceLabel(props.event));

  const resetToDetailView = () => {
    controller.reset();
    correctiveActionController.reset();
    setIsCompleting(false);
    setIsDeleting(false);
    setCompletionError(undefined);
    setDeletionError(undefined);
    setIsEditing(false);
    setIsCreatingCorrectiveAction(false);
  };

  const canEdit = () => props.canEdit && props.onSave !== undefined;
  const canComplete = () => props.canComplete && props.onComplete !== undefined && props.event.status !== "completed";
  const canDelete = () => (props.canDelete ?? false) && props.onDelete !== undefined;
  const canCreateCorrectiveAction = () => (
    props.onCreateCorrectiveAction !== undefined
    && !isStagedCalendarEvent(props.event)
    && !isCorrectiveActionServiceEvent(props.event)
  );
  const openEditMode = () => {
    setCompletionError(undefined);
    setDeletionError(undefined);
    setIsCreatingCorrectiveAction(false);
    requestServiceEventEdit(setIsEditing);
  };
  const openCorrectiveActionMode = () => {
    if (!canCreateCorrectiveAction()) {
      return;
    }
    setCompletionError(undefined);
    setDeletionError(undefined);
    setIsEditing(false);
    correctiveActionController.reset();
    setIsCreatingCorrectiveAction(true);
  };
  const handleComplete = async (closePopover: () => void): Promise<void> => {
    if (!canComplete() || isCompleting()) {
      return;
    }

    setCompletionError(undefined);
    setDeletionError(undefined);
    setIsCompleting(true);

    try {
      await props.onComplete?.(props.event);
      resetToDetailView();
      closePopover();
    } catch (error) {
      setCompletionError(
        error instanceof Error ? error.message : "Unable to mark service event complete."
      );
    } finally {
      setIsCompleting(false);
    }
  };
  const handleDelete = async (closePopover: () => void): Promise<void> => {
    if (!canDelete() || isDeleting()) {
      return;
    }

    setCompletionError(undefined);
    setDeletionError(undefined);
    setIsDeleting(true);

    try {
      await props.onDelete?.(props.event);
      resetToDetailView();
      closePopover();
    } catch (error) {
      setDeletionError(
        error instanceof Error ? error.message : "Unable to delete service event."
      );
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <Popover
      {...EVENT_POPOVER_PROPS}
      onOpenChange={(open) => {
        if (!open) {
          resetToDetailView();
        }
      }}
    >
      {(popover) => (
        <>
          {props.children}

          <Popover.Portal>
            <Popover.Content
              class="z-50 w-[min(96vw,28rem)] rounded-2xl border border-base-300 bg-base-100 shadow-2xl"
              data-service-event-popover=""
            >
              <Switch
                fallback={
                  <ServiceEventPopoverContent
                    event={props.event}
                    canEdit={canEdit()}
                    canCreateCorrectiveAction={canCreateCorrectiveAction()}
                    canComplete={canComplete()}
                    canDelete={canDelete()}
                    isCompleting={isCompleting()}
                    isDeleting={isDeleting()}
                    completionError={completionError()}
                    deletionError={deletionError()}
                    sourceEventLabel={sourceEventLabel()}
                    onEdit={openEditMode}
                    onCreateCorrectiveAction={openCorrectiveActionMode}
                    onComplete={() => {
                      void handleComplete(() => popover.setOpen(false));
                    }}
                    onDelete={() => {
                      void handleDelete(() => popover.setOpen(false));
                    }}
                    deleteLabel={props.deleteLabel ?? "Delete"}
                  />
                }
              >
                <Match when={isEditing()}>
                  <div class="flex max-h-[min(80vh,38rem)] min-h-0 flex-col gap-4 p-4">
                    <div class="space-y-1">
                      <Popover.Label class="text-lg font-semibold">Edit Service Event</Popover.Label>
                      <Popover.Description class="text-sm text-base-content/70">
                        Update the service event details for this location.
                      </Popover.Description>
                    </div>

                    <ServiceEventEditorBody
                      draft={controller.draft()}
                      isSaving={controller.isSaving()}
                      allowStatusEditing={true}
                      canChooseResponsibility={controller.canChooseResponsibility()}
                      updateDraft={controller.updateDraft}
                      submissionError={controller.submissionError()}
                      saveLabel="Save Changes"
                      onCancel={resetToDetailView}
                      onSubmit={() => {
                        void controller.submit().then((didSave) => {
                          if (didSave) {
                            resetToDetailView();
                            popover.setOpen(false);
                          }
                        });
                      }}
                    />
                  </div>
                </Match>
                <Match when={isCreatingCorrectiveAction()}>
                  <div class="flex max-h-[min(80vh,38rem)] min-h-0 flex-col gap-4 p-4">
                    <div class="space-y-1">
                      <Popover.Label class="text-lg font-semibold">Create Corrective Action</Popover.Label>
                      <Popover.Description class="text-sm text-base-content/70">
                        Draft the corrective action work order for this service event.
                      </Popover.Description>
                    </div>

                    <ServiceEventEditorBody
                      draft={correctiveActionController.draft()}
                      isSaving={correctiveActionController.isSaving()}
                      allowStatusEditing={false}
                      canChooseResponsibility={correctiveActionController.canChooseResponsibility()}
                      updateDraft={correctiveActionController.updateDraft}
                      submissionError={correctiveActionController.submissionError()}
                      saveLabel="Create Corrective Action"
                      onCancel={resetToDetailView}
                      onSubmit={() => {
                        void correctiveActionController.submit().then((didSave) => {
                          if (didSave) {
                            resetToDetailView();
                            popover.setOpen(false);
                          }
                        });
                      }}
                    />
                  </div>
                </Match>
              </Switch>
            </Popover.Content>
          </Popover.Portal>
        </>
      )}
    </Popover>
  );
};

export default ServiceEventEditPopover;
