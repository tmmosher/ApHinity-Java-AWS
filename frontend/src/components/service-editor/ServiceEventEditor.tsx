import {For, Match, Show, Switch, createMemo, createSignal} from "solid-js";
import type {AccountRole, CreateLocationServiceEventRequest} from "../../types/Types";
import {
  canChooseServiceEventResponsibility,
  createLocationServiceEventRequestFromDraft,
  SERVICE_EVENT_TITLE_MAX_LENGTH,
  type ServiceEventDraft
} from "../../util/location/serviceEventForm";

export type UpdateDraftFn = <Key extends keyof ServiceEventDraft>(
  key: Key,
  value: ServiceEventDraft[Key]
) => void;

type ServiceEventFormFieldsProps = {
  draft: ServiceEventDraft;
  isSaving: boolean;
  allowStatusEditing: boolean;
  canChooseResponsibility: boolean;
  updateDraft: UpdateDraftFn;
};

type ServiceEventEditorControllerProps = {
  role: () => AccountRole | undefined;
  getInitialDraft: () => ServiceEventDraft;
  onSave: (request: CreateLocationServiceEventRequest) => Promise<void>;
};

type ServiceEventEditorBodyProps = {
  draft: ServiceEventDraft;
  isSaving: boolean;
  allowStatusEditing: boolean;
  canChooseResponsibility: boolean;
  updateDraft: UpdateDraftFn;
  submissionError: string;
  saveLabel: string;
  onCancel: () => void;
  onSubmit: () => void;
};

const SERVICE_EVENT_STATUS_OPTIONS = [
  {value: "upcoming", label: "Upcoming"},
  {value: "current", label: "Current"},
  {value: "overdue", label: "Overdue"},
  {value: "completed", label: "Completed"}
] as const;

const ServiceEventTextFields = (props: Pick<ServiceEventFormFieldsProps, "draft" | "isSaving" | "updateDraft">) => (
  <>
    <label class="form-control w-full">
      <span class="label-text text-sm font-medium">Title</span>
      <input
        type="text"
        class="input input-bordered w-full"
        value={props.draft.title}
        maxLength={SERVICE_EVENT_TITLE_MAX_LENGTH}
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

const ServiceEventStatusFields = (
  props: Pick<ServiceEventFormFieldsProps, "draft" | "isSaving" | "updateDraft" | "allowStatusEditing">
) => (
  <Show when={props.allowStatusEditing}>
    <label class="form-control w-full">
      <span class="label-text text-sm font-medium">Status</span>
      <select
        class="select select-bordered w-full"
        value={props.draft.status}
        disabled={props.isSaving}
        onChange={(event) => props.updateDraft("status", event.currentTarget.value as ServiceEventDraft["status"])}
      >
        <For each={SERVICE_EVENT_STATUS_OPTIONS}>
          {(statusOption) => (
            <option value={statusOption.value}>{statusOption.label}</option>
          )}
        </For>
      </select>
    </label>
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
    <ServiceEventStatusFields
      draft={props.draft}
      isSaving={props.isSaving}
      allowStatusEditing={props.allowStatusEditing}
      updateDraft={props.updateDraft}
    />
  </div>
);

export const createServiceEventEditorController = (props: ServiceEventEditorControllerProps) => {
  const [draft, setDraft] = createSignal<ServiceEventDraft>(props.getInitialDraft());
  const [submissionError, setSubmissionError] = createSignal("");
  const [isSaving, setIsSaving] = createSignal(false);

  const canChooseResponsibility = createMemo(() =>
    canChooseServiceEventResponsibility(props.role())
  );

  const reset = () => {
    setDraft(props.getInitialDraft());
    setSubmissionError("");
    setIsSaving(false);
  };

  const updateDraft: UpdateDraftFn = (key, value) => {
    setDraft((current) => ({
      ...current,
      [key]: value
    }));
    if (submissionError()) {
      setSubmissionError("");
    }
  };

  const submit = async (): Promise<boolean> => {
    if (isSaving()) {
      return false;
    }

    setSubmissionError("");
    setIsSaving(true);

    try {
      const request = createLocationServiceEventRequestFromDraft(draft(), props.role());
      await props.onSave(request);
      return true;
    } catch (error) {
      setSubmissionError(error instanceof Error ? error.message : "Unable to save service event.");
      return false;
    } finally {
      setIsSaving(false);
    }
  };

  return {
    draft,
    isSaving,
    submissionError,
    canChooseResponsibility,
    reset,
    updateDraft,
    submit
  };
};

export const ServiceEventEditorBody = (props: ServiceEventEditorBodyProps) => (
  <>
    <ServiceEventFormFields
      draft={props.draft}
      isSaving={props.isSaving}
      allowStatusEditing={props.allowStatusEditing}
      canChooseResponsibility={props.canChooseResponsibility}
      updateDraft={props.updateDraft}
    />

    <Show when={props.submissionError}>
      <p class="text-sm text-error">{props.submissionError}</p>
    </Show>

    <div class="flex items-center justify-end gap-2">
      <button
        type="button"
        class="btn btn-sm"
        disabled={props.isSaving}
        onClick={props.onCancel}
      >
        Cancel
      </button>
      <button
        type="button"
        class={"btn btn-sm " + (props.isSaving ? "btn-disabled" : "btn-primary")}
        disabled={props.isSaving}
        onClick={props.onSubmit}
      >
        {props.isSaving ? "Saving..." : props.saveLabel}
      </button>
    </div>
  </>
);
