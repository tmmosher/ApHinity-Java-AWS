import Calendar from "corvu/calendar";
import Popover from "corvu/popover";
import {createEffect, createSignal, on, type JSX} from "solid-js";
import type {AccountRole, CreateLocationServiceEventRequest} from "../../../../types/Types";
import {createDefaultServiceEventDraft} from "../../../../util/location/serviceEventForm";
import {
  createServiceEventEditorController,
  ServiceEventEditorBody
} from "./ServiceEventEditor";
import {SERVICE_EVENT_POPOVER_POSITION_PROPS} from "./serviceEventPopoverPosition";

type ServiceEventCreatePopoverProps = {
  day: Date;
  style: JSX.CSSProperties;
  class: string;
  classList?: Record<string, boolean | undefined>;
  role: AccountRole | undefined;
  onSave: (request: CreateLocationServiceEventRequest) => Promise<void>;
};

const CREATE_POPOVER_PROPS = {
  ...SERVICE_EVENT_POPOVER_POSITION_PROPS,
  trapFocus: false,
  restoreFocus: false
};

export const ServiceEventCreatePopoverContent = (props: {
  draft: ReturnType<typeof createServiceEventEditorController>["draft"];
  isSaving: ReturnType<typeof createServiceEventEditorController>["isSaving"];
  canChooseResponsibility: ReturnType<typeof createServiceEventEditorController>["canChooseResponsibility"];
  updateDraft: ReturnType<typeof createServiceEventEditorController>["updateDraft"];
  submissionError: ReturnType<typeof createServiceEventEditorController>["submissionError"];
  onCancel: () => void;
  onSubmit: () => void;
}) => (
  <div class="flex max-h-[min(80vh,38rem)] min-h-0 flex-col gap-4 p-4">
    <div class="space-y-1">
      <Popover.Label class="text-lg font-semibold">New Service Event</Popover.Label>
      <Popover.Description class="text-sm text-base-content/70">
        Create a service event for this day.
      </Popover.Description>
    </div>

    <ServiceEventEditorBody
      draft={props.draft()}
      isSaving={props.isSaving()}
      allowStatusEditing={false}
      canChooseResponsibility={props.canChooseResponsibility()}
      updateDraft={props.updateDraft}
      submissionError={props.submissionError()}
      saveLabel="Save"
      onCancel={props.onCancel}
      onSubmit={props.onSubmit}
    />
  </div>
);

export const ServiceEventCreatePopover = (props: ServiceEventCreatePopoverProps) => {
  const [isOpen, setIsOpen] = createSignal(false);
  const controller = createServiceEventEditorController({
    role: () => props.role,
    getInitialDraft: () => createDefaultServiceEventDraft(props.role, props.day),
    onSave: props.onSave
  });

  createEffect(on(isOpen, (open) => {
    if (open) {
      controller.reset();
    }
  }));

  const closeAndReset = () => {
    if (controller.isSaving()) {
      return;
    }
    controller.reset();
    setIsOpen(false);
  };

  const submit = async () => {
    const didSave = await controller.submit();
    if (didSave) {
      controller.reset();
      setIsOpen(false);
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
        closeAndReset();
      }}
    >
      <Popover.Trigger
        as={Calendar.CellTrigger}
        type="button"
        day={props.day}
        style={props.style}
        class={props.class}
        classList={props.classList}
        data-service-calendar-day-trigger=""
      >
        <span class="text-xs font-semibold tabular-nums md:text-sm">
          {props.day.getDate()}
        </span>
      </Popover.Trigger>

      <Popover.Portal>
        <Popover.Content
          class="z-50 w-[min(96vw,28rem)] rounded-2xl border border-base-300 bg-base-100 shadow-2xl"
          data-service-event-create-popover=""
        >
          <ServiceEventCreatePopoverContent
            draft={controller.draft}
            isSaving={controller.isSaving}
            canChooseResponsibility={controller.canChooseResponsibility}
            updateDraft={controller.updateDraft}
            submissionError={controller.submissionError}
            onCancel={closeAndReset}
            onSubmit={() => void submit()}
          />
        </Popover.Content>
      </Popover.Portal>
    </Popover>
  );
};

export default ServiceEventCreatePopover;
