import Dialog from "corvu/dialog";
import {Match, Show, Switch, createEffect, createMemo, createSignal, on} from "solid-js";
import type {AccountRole, CreateLocationServiceEventRequest} from "../../../../types/Types";
import {
  canChooseServiceEventResponsibility,
  createDefaultServiceEventDraft,
  createLocationServiceEventRequestFromDraft,
  type ServiceEventDraft
} from "../../../../util/location/serviceEventForm";

type ServiceEventCreateModalProps = {
  isOpen: boolean;
  role: AccountRole | undefined;
  onSave: (request: CreateLocationServiceEventRequest) => Promise<void>;
  onClose: () => void;
};

export const ServiceEventCreateModal = (props: ServiceEventCreateModalProps) => {
  const [draft, setDraft] = createSignal<ServiceEventDraft>(createDefaultServiceEventDraft(props.role));
  const [submissionError, setSubmissionError] = createSignal("");
  const [isSaving, setIsSaving] = createSignal(false);

  const canChooseResponsibility = createMemo(() =>
    canChooseServiceEventResponsibility(props.role)
  );

  createEffect(on(() => props.isOpen, (isOpen) => {
    if (!isOpen) {
      return;
    }
    setDraft(createDefaultServiceEventDraft(props.role));
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
    setDraft(createDefaultServiceEventDraft(props.role));
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
      setDraft(createDefaultServiceEventDraft(props.role));
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
            <Dialog.Label class="text-lg font-semibold">New Service Event</Dialog.Label>
            <Dialog.Description class="text-sm text-base-content/70">
              Create a service event for this location.
            </Dialog.Description>
          </div>

          <div class="space-y-4 overflow-y-auto">
            <label class="form-control w-full">
              <span class="label-text text-sm font-medium">Title</span>
              <input
                type="text"
                class="input input-bordered w-full"
                value={draft().title}
                disabled={isSaving()}
                onInput={(event) => updateDraft("title", event.currentTarget.value)}
              />
            </label>

            <label class="form-control w-full">
              <span class="label-text text-sm font-medium">Description</span>
              <textarea
                class="textarea textarea-bordered min-h-28 w-full"
                value={draft().description}
                disabled={isSaving()}
                onInput={(event) => updateDraft("description", event.currentTarget.value)}
              />
            </label>

            <fieldset class="space-y-2">
              <legend class="text-sm font-medium">Schedule Type</legend>
              <div class="flex flex-wrap gap-4">
                <label class="flex items-center gap-2 text-sm">
                  <input
                    type="radio"
                    class="radio radio-primary radio-sm"
                    name="service-event-schedule-mode"
                    checked={draft().scheduleMode === "timed"}
                    disabled={isSaving()}
                    onChange={() => updateDraft("scheduleMode", "timed")}
                  />
                  <span>Start / End</span>
                </label>
                <label class="flex items-center gap-2 text-sm">
                  <input
                    type="radio"
                    class="radio radio-primary radio-sm"
                    name="service-event-schedule-mode"
                    checked={draft().scheduleMode === "all-day"}
                    disabled={isSaving()}
                    onChange={() => updateDraft("scheduleMode", "all-day")}
                  />
                  <span>All Day</span>
                </label>
              </div>
            </fieldset>

            <Switch>
              <Match when={draft().scheduleMode === "timed"}>
                <div class="grid gap-3 md:grid-cols-2">
                  <label class="form-control w-full">
                    <span class="label-text text-sm font-medium">Start Date</span>
                    <input
                      type="date"
                      class="input input-bordered w-full"
                      value={draft().startDate}
                      disabled={isSaving()}
                      onInput={(event) => updateDraft("startDate", event.currentTarget.value)}
                    />
                  </label>
                  <label class="form-control w-full">
                    <span class="label-text text-sm font-medium">Start Time</span>
                    <input
                      type="time"
                      class="input input-bordered w-full"
                      value={draft().startTime}
                      disabled={isSaving()}
                      onInput={(event) => updateDraft("startTime", event.currentTarget.value)}
                    />
                  </label>
                  <label class="form-control w-full">
                    <span class="label-text text-sm font-medium">End Date</span>
                    <input
                      type="date"
                      class="input input-bordered w-full"
                      value={draft().endDate}
                      disabled={isSaving()}
                      onInput={(event) => updateDraft("endDate", event.currentTarget.value)}
                    />
                  </label>
                  <label class="form-control w-full">
                    <span class="label-text text-sm font-medium">End Time</span>
                    <input
                      type="time"
                      class="input input-bordered w-full"
                      value={draft().endTime}
                      disabled={isSaving()}
                      onInput={(event) => updateDraft("endTime", event.currentTarget.value)}
                    />
                  </label>
                </div>
              </Match>
              <Match when={draft().scheduleMode === "all-day"}>
                <div class="grid gap-3 md:grid-cols-2">
                  <label class="form-control w-full">
                    <span class="label-text text-sm font-medium">Start Date</span>
                    <input
                      type="date"
                      class="input input-bordered w-full"
                      value={draft().allDayStartDate}
                      disabled={isSaving()}
                      onInput={(event) => updateDraft("allDayStartDate", event.currentTarget.value)}
                    />
                  </label>
                  <label class="form-control w-full">
                    <span class="label-text text-sm font-medium">End Date</span>
                    <input
                      type="date"
                      class="input input-bordered w-full"
                      value={draft().allDayEndDate}
                      disabled={isSaving()}
                      onInput={(event) => updateDraft("allDayEndDate", event.currentTarget.value)}
                    />
                  </label>
                </div>
              </Match>
            </Switch>

            <Show
              when={canChooseResponsibility()}
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
                      checked={draft().responsibility === "partner"}
                      disabled={isSaving()}
                      onChange={() => updateDraft("responsibility", "partner")}
                    />
                    <span>Partner</span>
                  </label>
                  <label class="flex items-center gap-2 text-sm">
                    <input
                      type="radio"
                      class="radio radio-primary radio-sm"
                      name="service-event-responsibility"
                      checked={draft().responsibility === "client"}
                      disabled={isSaving()}
                      onChange={() => updateDraft("responsibility", "client")}
                    />
                    <span>Client</span>
                  </label>
                </div>
              </fieldset>
            </Show>
          </div>

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
              {isSaving() ? "Saving..." : "Save"}
            </button>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog>
  );
};

export default ServiceEventCreateModal;
