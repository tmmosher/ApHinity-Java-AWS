import Popover from "corvu/popover";
import {Show, createSignal, type ParentProps} from "solid-js";
import type {AccountRole, CreateLocationServiceEventRequest, LocationServiceEvent} from "../../../../types/Types";
import {
  formatDisplayDate,
  formatDisplayTime
} from "../../../../util/location/dateUtility";
import {createServiceEventDraftFromEvent} from "../../../../util/location/serviceEventForm";
import {
  createServiceEventEditorController,
  ServiceEventEditorBody
} from "./ServiceEventEditor";

type ServiceEventEditPopoverProps = {
  event: LocationServiceEvent;
  canEdit: boolean;
  role: AccountRole | undefined;
  onSave?: (event: LocationServiceEvent, request: CreateLocationServiceEventRequest) => Promise<void>;
};

type ServiceEventPopoverContentProps = {
  event: LocationServiceEvent;
  canEdit: boolean;
  onEdit: () => void;
};

const EVENT_POPOVER_PROPS = {
  placement: "bottom-start" as const,
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
    <div class="flex items-start justify-between gap-4">
      <div class="min-w-0">
        <Popover.Label class="text-base font-semibold leading-tight">
          {props.event.title}
        </Popover.Label>
      </div>

      <Show when={props.canEdit}>
        <button
          type="button"
          class="btn btn-primary btn-xs"
          data-service-event-edit=""
          onClick={(event) => {
            event.stopPropagation();
            props.onEdit();
          }}
        >
          Edit
        </button>
      </Show>
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
    </dl>

    <div class="space-y-1">
      <p class="text-xs font-semibold uppercase tracking-[0.16em] text-base-content/55">
        Description
      </p>
      <Popover.Description class="text-sm leading-6 text-base-content/80">
        {props.event.description ?? "No description provided."}
      </Popover.Description>
    </div>
  </div>
);

export const requestServiceEventEdit = (setIsEditing: (isEditing: boolean) => void): void => {
  setIsEditing(true);
};

export const ServiceEventEditPopover = (props: ParentProps<ServiceEventEditPopoverProps>) => {
  const [isEditing, setIsEditing] = createSignal(false);
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

  const resetToDetailView = () => {
    controller.reset();
    setIsEditing(false);
  };

  const canEdit = () => props.canEdit && props.onSave !== undefined;

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
              <Show
                when={isEditing()}
                fallback={
                  <ServiceEventPopoverContent
                    event={props.event}
                    canEdit={canEdit()}
                    onEdit={() => requestServiceEventEdit(setIsEditing)}
                  />
                }
              >
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
              </Show>
            </Popover.Content>
          </Popover.Portal>
        </>
      )}
    </Popover>
  );
};

export default ServiceEventEditPopover;
