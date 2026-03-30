import Dialog from "corvu/dialog";
import {Match, Show, Switch, createEffect, createMemo, createSignal, on} from "solid-js";
import type {AccountRole, CreateLocationServiceEventRequest} from "../../../../types/Types";
import {
  canChooseServiceEventResponsibility,
  createLocationServiceEventRequestFromDraft,
  type ServiceEventDraft
} from "../../../../util/location/serviceEventForm";

type ServiceEventModalProps = {
  isOpen: boolean;
  heading: string;
  description: string;
  role: AccountRole | undefined;
  saveLabel: string;
  getInitialDraft: () => ServiceEventDraft;
  onSave: (request: CreateLocationServiceEventRequest) => Promise<void>;
  onClose: () => void;
};

type UpdateDraftFn = <Key extends keyof ServiceEventDraft>(
  key: Key,
  value: ServiceEventDraft[Key]
) => void;

type ServiceEventFormFieldsProps = {
  draft: ServiceEventDraft;
  isSaving: boolean;
  canChooseResponsibility: boolean;
  updateDraft: UpdateDraftFn;
};

const ServiceEventTextFields = (props: Pick<ServiceEventFormFieldsProps, "draft" | "isSaving" | "updateDraft">) => (
  <>
    <label class="form-control w-full">
      <span class="label-text text-sm font-medium">Title</span>
      <input
        type="text"
        class="input input-bordered w-full"
        value={props.draft.title}
        disabled={props.isSaving}
        onInput={(event) => props.updateDraft("title", event.currentTarget.value)}
      />
    </label>

    <label class="form-control w-full">
      <span class="label-text text-sm font-medium">Description</span>
      <textarea
        class="textarea textarea-bordered min-h-28 w-full"
        value={props.draft.description}
        disabled={props.isSaving}
        onInput={(event) => props.updateDraft("description", event.currentTarget.value)}
      />
    </label>
  </>
);

const ServiceEventScheduleFields = (props: Pick<ServiceEventFormFieldsProps, "draft" | "isSaving" | "updateDraft">) => (
  <>
    <fieldset class="space-y-2">
      <legend class="text-sm font-medium">Schedule Type</legend>
      <div class="flex flex-wrap gap-4">
        <label class="flex items-center gap-2 text-sm">
          <input
            type="radio"
            class="radio radio-primary radio-sm"
            name="service-event-schedule-mode"
            checked={props.draft.scheduleMode === "timed"}
            disabled={props.isSaving}
            onChange={() => props.updateDraft("scheduleMode", "timed")}
          />
          <span>Start / End</span>
        </label>
        <label class="flex items-center gap-2 text-sm">
          <input
            type="radio"
            class="radio radio-primary radio-sm"
            name="service-event-schedule-mode"
            checked={props.draft.scheduleMode === "all-day"}
            disabled={props.isSaving}
            onChange={() => props.updateDraft("scheduleMode", "all-day")}
          />
          <span>All Day</span>
        </label>
      </div>
    </fieldset>

    <Switch>
      <Match when={props.draft.scheduleMode === "timed"}>
        <div class="grid gap-3 md:grid-cols-2">
          <label class="form-control w-full">
            <span class="label-text text-sm font-medium">Start Date</span>
            <input
              type="date"
              class="input input-bordered w-full"
              value={props.draft.startDate}
              disabled={props.isSaving}
              onInput={(event) => props.updateDraft("startDate", event.currentTarget.value)}
            />
          </label>
          <label class="form-control w-full">
            <span class="label-text text-sm font-medium">Start Time</span>
            <input
              type="time"
              class="input input-bordered w-full"
              value={props.draft.startTime}
              disabled={props.isSaving}
              onInput={(event) => props.updateDraft("startTime", event.currentTarget.value)}
            />
          </label>
          <label class="form-control w-full">
            <span class="label-text text-sm font-medium">End Date</span>
            <input
              type="date"
              class="input input-bordered w-full"
              value={props.draft.endDate}
              disabled={props.isSaving}
              onInput={(event) => props.updateDraft("endDate", event.currentTarget.value)}
            />
          </label>
          <label class="form-control w-full">
            <span class="label-text text-sm font-medium">End Time</span>
            <input
              type="time"
              class="input input-bordered w-full"
              value={props.draft.endTime}
              disabled={props.isSaving}
              onInput={(event) => props.updateDraft("endTime", event.currentTarget.value)}
            />
          </label>
        </div>
      </Match>
      <Match when={props.draft.scheduleMode === "all-day"}>
        <div class="grid gap-3 md:grid-cols-2">
          <label class="form-control w-full">
            <span class="label-text text-sm font-medium">Start Date</span>
            <input
              type="date"
              class="input input-bordered w-full"
              value={props.draft.allDayStartDate}
              disabled={props.isSaving}
              onInput={(event) => props.updateDraft("allDayStartDate", event.currentTarget.value)}
            />
          </label>
          <label class="form-control w-full">
            <span class="label-text text-sm font-medium">End Date</span>
            <input
              type="date"
              class="input input-bordered w-full"
              value={props.draft.allDayEndDate}
              disabled={props.isSaving}
              onInput={(event) => props.updateDraft("allDayEndDate", event.currentTarget.value)}
            />
          </label>
        </div>
      </Match>
    </Switch>
  </>
);

const ServiceEventResponsibilityFields = (
  props: Pick<ServiceEventFormFieldsProps, "draft" | "isSaving" | "updateDraft" | "canChooseResponsibility">
) => (
  <Show
    when={props.canChooseResponsibility}
    fallback={
      <div class="space-y-1">
        <p class="text-sm font-medium">Responsibility</p>
        <p class="text-sm text-base-content/70">Client</p>
      </div>
    }
  >
    <fieldset class="space-y-2">
      <legend class="text-sm font-medium">Responsibility</legend>
      <div class="flex flex-wrap gap-4">
        <label class="flex items-center gap-2 text-sm">
          <input
            type="radio"
            class="radio radio-primary radio-sm"
            name="service-event-responsibility"
            checked={props.draft.responsibility === "partner"}
            disabled={props.isSaving}
            onChange={() => props.updateDraft("responsibility", "partner")}
          />
          <span>Partner</span>
        </label>
        <label class="flex items-center gap-2 text-sm">
          <input
            type="radio"
            class="radio radio-primary radio-sm"
            name="service-event-responsibility"
            checked={props.draft.responsibility === "client"}
            disabled={props.isSaving}
            onChange={() => props.updateDraft("responsibility", "client")}
          />
          <span>Client</span>
        </label>
      </div>
    </fieldset>
  </Show>
);

const ServiceEventFormFields = (props: ServiceEventFormFieldsProps) => (
  <div class="space-y-4 overflow-y-auto">
    <ServiceEventTextFields
      draft={props.draft}
      isSaving={props.isSaving}
      updateDraft={props.updateDraft}
    />
    <ServiceEventScheduleFields
      draft={props.draft}
      isSaving={props.isSaving}
      updateDraft={props.updateDraft}
    />
    <ServiceEventResponsibilityFields
      draft={props.draft}
      isSaving={props.isSaving}
      canChooseResponsibility={props.canChooseResponsibility}
      updateDraft={props.updateDraft}
    />
  </div>
);

export const ServiceEventModal = (props: ServiceEventModalProps) => {
  const [draft, setDraft] = createSignal<ServiceEventDraft>(props.getInitialDraft());
  const [submissionError, setSubmissionError] = createSignal("");
  const [isSaving, setIsSaving] = createSignal(false);

  const canChooseResponsibility = createMemo(() =>
    canChooseServiceEventResponsibility(props.role)
  );

  const resetDraft = () => setDraft(props.getInitialDraft());

  createEffect(on(() => props.isOpen, (isOpen) => {
    if (!isOpen) {
      return;
    }
    resetDraft();
    setSubmissionError("");
    setIsSaving(false);
  }));

  const updateDraft = <Key extends keyof ServiceEventDraft>(key: Key, value: ServiceEventDraft[Key]) => {
    setDraft((current) => ({
      ...current,
      [key]: value
    }));
    if (submissionError()) {
      setSubmissionError("");
    }
  };

  const closeAndReset = () => {
    if (isSaving()) {
      return;
    }
    resetDraft();
    setSubmissionError("");
    props.onClose();
  };

  const submit = async () => {
    if (isSaving()) {
      return;
    }

    setSubmissionError("");
    setIsSaving(true);

    try {
      const request = createLocationServiceEventRequestFromDraft(draft(), props.role);
      await props.onSave(request);
      resetDraft();
      props.onClose();
    } catch (error) {
      setSubmissionError(error instanceof Error ? error.message : "Unable to save service event.");
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <Dialog
      open={props.isOpen}
      onOpenChange={(open) => {
        if (!open) {
          closeAndReset();
        }
      }}
    >
      <Dialog.Portal>
        <Dialog.Overlay class="fixed inset-0 z-50 bg-black/45 data-closed:pointer-events-none" />
        <Dialog.Content class="fixed inset-0 z-[60] m-auto flex h-[min(92vh,44rem)] w-[min(96vw,42rem)] flex-col gap-4 rounded-xl border border-base-300 bg-base-100 p-4 shadow-2xl data-closed:pointer-events-none md:p-5">
          <div class="space-y-1">
            <Dialog.Label class="text-lg font-semibold">{props.heading}</Dialog.Label>
            <Dialog.Description class="text-sm text-base-content/70">
              {props.description}
            </Dialog.Description>
          </div>

          <ServiceEventFormFields
            draft={draft()}
            isSaving={isSaving()}
            canChooseResponsibility={canChooseResponsibility()}
            updateDraft={updateDraft}
          />

          <Show when={submissionError()}>
            <p class="text-sm text-error">{submissionError()}</p>
          </Show>

          <div class="flex items-center justify-end gap-2">
            <button
              type="button"
              class="btn btn-sm"
              disabled={isSaving()}
              onClick={closeAndReset}
            >
              Cancel
            </button>
            <button
              type="button"
              class={"btn btn-sm " + (isSaving() ? "btn-disabled" : "btn-primary")}
              disabled={isSaving()}
              onClick={() => void submit()}
            >
              {isSaving() ? "Saving..." : props.saveLabel}
            </button>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog>
  );
};

export default ServiceEventModal;
