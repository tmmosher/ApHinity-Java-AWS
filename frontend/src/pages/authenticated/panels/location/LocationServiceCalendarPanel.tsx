import {useParams} from "@solidjs/router";
import {Show, createEffect, createMemo, createResource, createSignal} from "solid-js";
import {toast} from "solid-toast";
import {useApiHost} from "../../../../context/ApiHostContext";
import {useProfile} from "../../../../context/ProfileContext";
import {
  createLocationEventById,
  fetchLocationEventsById,
  getLocationEventTemplateDownloadUrl,
  uploadLocationEventCalendarById,
  updateLocationEventById
} from "../../../../util/location/locationEventApi";
import {formatLocationEventMonth, normalizeMonthStart} from "../../../../util/location/dateUtility";
import type {CreateLocationServiceEventRequest, LocationServiceEvent} from "../../../../types/Types";
import {
  canCompleteLocationServiceEvent,
  canEditLocationServiceEvent,
  createLocationServiceEventRequestFromEvent
} from "../../../../util/location/serviceEventForm";
import {
  buildServiceCalendarSpreadsheetBlob,
  parseServiceCalendarSpreadsheetFile
} from "../../../../util/location/serviceCalendarSpreadsheet";
import {
  buildServiceCalendarRequestsFromStagedEvents,
  completeStagedServiceCalendarEvent,
  deleteStagedServiceCalendarEvent,
  editStagedServiceCalendarEvent,
  isStagedServiceCalendarEvent,
  stageImportedServiceCalendarEvents,
  type StagedLocationServiceEvent,
  undoStagedServiceCalendarMutation
} from "../../../../util/location/stagedServiceCalendar";
import ServiceCalendarIntroPopover from "./ServiceCalendarIntroPopover";
import ServiceScheduleCalendar from "./ServiceScheduleCalendar";
import {createDashboardLocationResetGuard} from "./locationView";

type ServiceEventCalendarResource = {
  locationId: string;
  month: string;
  value: LocationServiceEvent[];
};

export const LocationServiceCalendarPanel = () => {
  const host = useApiHost();
  const profileContext = useProfile();
  const params = useParams<{ locationId: string }>();
  const [calendarMonth, setCalendarMonth] = createSignal(normalizeMonthStart(new Date()));
  const [stagedImportedEvents, setStagedImportedEvents] = createSignal<StagedLocationServiceEvent[]>([]);
  const [stagedImportUndoStack, setStagedImportUndoStack] = createSignal<StagedLocationServiceEvent[][]>([]);
  const [isImportingSpreadsheet, setIsImportingSpreadsheet] = createSignal(false);
  const [isApplyingImportedEvents, setIsApplyingImportedEvents] = createSignal(false);
  const [locationSessionToken, setLocationSessionToken] = createSignal(0);
  const shouldResetCalendarState = createDashboardLocationResetGuard(params.locationId);
  const role = createMemo(() => profileContext.profile()?.role);
  let spreadsheetUploadInput: HTMLInputElement | undefined;

  const viewedMonth = createMemo(() => formatLocationEventMonth(calendarMonth()));
  const serviceCalendarTemplateHref = createMemo(() =>
    getLocationEventTemplateDownloadUrl(host, params.locationId)
  );
  const hasPendingImportedEventChanges = createMemo(() => stagedImportUndoStack().length > 0);
  const hasStagedImportedEvents = createMemo(() => stagedImportedEvents().length > 0);
  const isImportedEventMutationBusy = createMemo(() => (
    isImportingSpreadsheet() || isApplyingImportedEvents()
  ));
  const serviceEventRequest = createMemo(() => ({
    locationId: params.locationId,
    month: viewedMonth()
  }));

  const [serviceEventResource, {refetch: refetchServiceEventResource}] = createResource(
    serviceEventRequest,
    async (request): Promise<ServiceEventCalendarResource> => ({
      ...request,
      value: await fetchLocationEventsById(host, request.locationId, request.month)
    })
  );

  const serviceEvents = createMemo(() => {
    const resource = serviceEventResource();
    const request = serviceEventRequest();
    if (!resource || resource.locationId !== request.locationId || resource.month !== request.month) {
      return undefined;
    }
    return resource.value;
  });
  const calendarEvents = createMemo<LocationServiceEvent[]>(() => [
    ...(serviceEvents() ?? []),
    ...stagedImportedEvents()
  ]);

  const serviceEventErrorMessage = createMemo(() => {
    if (serviceEventResource.loading || !serviceEventResource.error) {
      return undefined;
    }
    return serviceEventResource.error instanceof Error
      ? serviceEventResource.error.message
      : "Unable to load service events.";
  });

  const refetchServiceEvents = async (): Promise<void> => {
    await refetchServiceEventResource();
  };
  const clearSpreadsheetUploadInput = (): void => {
    if (spreadsheetUploadInput) {
      spreadsheetUploadInput.value = "";
    }
  };

  createEffect(() => {
    if (!shouldResetCalendarState(params.locationId)) {
      return;
    }

    setCalendarMonth(normalizeMonthStart(new Date()));
    setStagedImportedEvents([]);
    setStagedImportUndoStack([]);
    setIsImportingSpreadsheet(false);
    setIsApplyingImportedEvents(false);
    setLocationSessionToken((token) => token + 1);
    clearSpreadsheetUploadInput();
  });

  const saveServiceEvent = async (request: CreateLocationServiceEventRequest): Promise<void> => {
    await createLocationEventById(host, params.locationId, request);
    await refetchServiceEvents();
    toast.success("Service event created.");
  };

  const saveEditedServiceEvent = async (
    event: LocationServiceEvent,
    request: CreateLocationServiceEventRequest
  ): Promise<void> => {
    await updateLocationEventById(host, params.locationId, event.id, request);
    await refetchServiceEvents();
    toast.success("Service event updated.");
  };

  const completeServiceEvent = async (event: LocationServiceEvent): Promise<void> => {
    await updateLocationEventById(host, params.locationId, event.id, {
      ...createLocationServiceEventRequestFromEvent(event),
      status: "completed"
    });
    await refetchServiceEvents();
    toast.success("Service event marked complete.");
  };
  const stageSpreadsheetImport = async (event: Event): Promise<void> => {
    const input = event.currentTarget;
    if (!(input instanceof HTMLInputElement)) {
      return;
    }

    const file = input.files?.[0];
    if (!file) {
      return;
    }

    setIsImportingSpreadsheet(true);
    try {
      const parsedRequests = await parseServiceCalendarSpreadsheetFile(file, role());
      const result = stageImportedServiceCalendarEvents(
        stagedImportedEvents(),
        stagedImportUndoStack(),
        parsedRequests
      );
      if (!result.changed) {
        return;
      }

      setStagedImportedEvents(result.nextEvents);
      setStagedImportUndoStack(result.nextUndoStack);
      toast.success(
        `${parsedRequests.length} service event${parsedRequests.length === 1 ? "" : "s"} staged from spreadsheet.`
      );
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to import service calendar spreadsheet.");
    } finally {
      setIsImportingSpreadsheet(false);
      input.value = "";
    }
  };

  const undoLastImportedEventMutation = (): void => {
    if (isImportedEventMutationBusy()) {
      return;
    }

    const result = undoStagedServiceCalendarMutation(
      stagedImportedEvents(),
      stagedImportUndoStack()
    );
    if (!result.undone) {
      return;
    }

    setStagedImportedEvents(result.nextEvents);
    setStagedImportUndoStack(result.nextUndoStack);
  };

  const applyImportedEvents = async (): Promise<void> => {
    if (isImportedEventMutationBusy() || stagedImportedEvents().length === 0) {
      return;
    }

    const uploadLocationId = params.locationId;
    const uploadSessionToken = locationSessionToken();
    setIsApplyingImportedEvents(true);

    try {
      const blob = await buildServiceCalendarSpreadsheetBlob(
        buildServiceCalendarRequestsFromStagedEvents(stagedImportedEvents())
      );
      const file = new File([blob], "service_calendar_upload.xlsx", {type: blob.type});
      const response = await uploadLocationEventCalendarById(host, uploadLocationId, file);
      if (uploadLocationId !== params.locationId || uploadSessionToken !== locationSessionToken()) {
        return;
      }

      setStagedImportedEvents([]);
      setStagedImportUndoStack([]);
      clearSpreadsheetUploadInput();
      toast.success(
        `Imported ${response.importedCount} service event${response.importedCount === 1 ? "" : "s"}.`
      );

      try {
        await refetchServiceEvents();
      } catch {
        if (uploadLocationId === params.locationId && uploadSessionToken === locationSessionToken()) {
          toast.error("Service calendar imported, but automatic refresh failed. Please refresh the page.");
        }
      }
    } catch (error) {
      if (uploadLocationId !== params.locationId || uploadSessionToken !== locationSessionToken()) {
        return;
      }

      if (error instanceof Error && error.message === "CSRF invalid") {
        toast.error("Security token refresh failed. Please retry Apply; your staged import is still local.");
        return;
      }
      if (error instanceof Error && error.message === "Security token rejected") {
        toast.error("Security validation failed. Retrying Apply usually succeeds without losing staged imports.");
        return;
      }
      if (error instanceof Error && error.message === "Authentication required") {
        toast.error("Session refresh failed. Please sign in again; your staged import is still on this page.");
        return;
      }
      if (error instanceof Error && error.message === "Insufficient permissions") {
        toast.error("You no longer have permission to import service calendar events.");
        return;
      }

      toast.error(error instanceof Error ? error.message : "Unable to upload service calendar spreadsheet.");
    } finally {
      if (uploadLocationId === params.locationId && uploadSessionToken === locationSessionToken()) {
        setIsApplyingImportedEvents(false);
      }
    }
  };

  const saveCalendarEvent = async (
    event: LocationServiceEvent,
    request: CreateLocationServiceEventRequest
  ): Promise<void> => {
    if (!isStagedServiceCalendarEvent(event)) {
      await saveEditedServiceEvent(event, request);
      return;
    }
    if (isImportedEventMutationBusy()) {
      throw new Error("Imported service events are currently busy.");
    }

    const result = editStagedServiceCalendarEvent(
      stagedImportedEvents(),
      stagedImportUndoStack(),
      event.id,
      request
    );
    if (!result.changed) {
      return;
    }

    setStagedImportedEvents(result.nextEvents);
    setStagedImportUndoStack(result.nextUndoStack);
    toast.success("Staged service event updated.");
  };

  const completeCalendarEvent = async (event: LocationServiceEvent): Promise<void> => {
    if (!isStagedServiceCalendarEvent(event)) {
      await completeServiceEvent(event);
      return;
    }
    if (isImportedEventMutationBusy()) {
      throw new Error("Imported service events are currently busy.");
    }

    const result = completeStagedServiceCalendarEvent(
      stagedImportedEvents(),
      stagedImportUndoStack(),
      event.id
    );
    if (!result.changed) {
      return;
    }

    setStagedImportedEvents(result.nextEvents);
    setStagedImportUndoStack(result.nextUndoStack);
    toast.success("Staged service event marked complete.");
  };

  const deleteCalendarEvent = async (event: LocationServiceEvent): Promise<void> => {
    if (!isStagedServiceCalendarEvent(event)) {
      throw new Error("Only staged service events can be deleted here.");
    }
    if (isImportedEventMutationBusy()) {
      throw new Error("Imported service events are currently busy.");
    }

    const result = deleteStagedServiceCalendarEvent(
      stagedImportedEvents(),
      stagedImportUndoStack(),
      event.id
    );
    if (!result.changed) {
      return;
    }

    setStagedImportedEvents(result.nextEvents);
    setStagedImportUndoStack(result.nextUndoStack);
    toast.success("Staged service event deleted.");
  };

  const canDeleteCalendarEvent = (event: LocationServiceEvent): boolean => (
    isStagedServiceCalendarEvent(event) && !isImportedEventMutationBusy()
  );
  const canEditCalendarEvent = (event: LocationServiceEvent): boolean => (
    canEditLocationServiceEvent(role(), event.responsibility)
    && (!isStagedServiceCalendarEvent(event) || !isImportedEventMutationBusy())
  );
  const canCompleteCalendarEvent = (event: LocationServiceEvent): boolean => (
    canCompleteLocationServiceEvent(role(), event.responsibility, event.status)
    && (!isStagedServiceCalendarEvent(event) || !isImportedEventMutationBusy())
  );

  return (
    <div class="flex min-h-[calc(100vh-16rem)] flex-col gap-4">
      <section class="rounded-2xl border border-base-300 bg-base-100/70 p-6 shadow-sm">
        <div class="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
          <div class="space-y-1">
            <h2 class="text-xl font-semibold tracking-tight">Service Calendar</h2>
          </div>
          <div class="flex flex-col items-stretch gap-3 md:items-end">
            <div class="flex flex-wrap items-center gap-3">
              <ServiceCalendarIntroPopover templateHref={serviceCalendarTemplateHref()} />
              <input
                ref={(element) => {
                  spreadsheetUploadInput = element;
                }}
                type="file"
                accept=".xlsx"
                class="file-input file-input-bordered file-input-sm w-full min-w-[18rem] rounded-2xl md:w-auto"
                aria-label="Import service calendar spreadsheet"
                data-service-calendar-upload-input=""
                disabled={isImportedEventMutationBusy()}
                onChange={(event) => {
                  void stageSpreadsheetImport(event);
                }}
              />
              <button
                type="button"
                class={"btn btn-sm rounded-2xl " + (
                  hasStagedImportedEvents() && !isImportedEventMutationBusy() ? "btn-primary" : "btn-disabled"
                )}
                disabled={!hasStagedImportedEvents() || isImportedEventMutationBusy()}
                data-service-calendar-apply=""
                onClick={() => void applyImportedEvents()}
              >
                {isApplyingImportedEvents() ? "Applying..." : "Apply"}
              </button>
              <button
                type="button"
                class={"btn btn-sm rounded-2xl " + (
                  hasPendingImportedEventChanges() && !isImportedEventMutationBusy() ? "btn-outline" : "btn-disabled"
                )}
                disabled={!hasPendingImportedEventChanges() || isImportedEventMutationBusy()}
                data-service-calendar-undo=""
                onClick={undoLastImportedEventMutation}
              >
                Undo
              </button>
            </div>
            <Show when={hasPendingImportedEventChanges()}>
              <p class="text-right text-xs text-base-content/70">
                {stagedImportedEvents().length} staged service event{stagedImportedEvents().length === 1 ? "" : "s"} -{" "}
                {stagedImportUndoStack().length} pending import mutation{stagedImportUndoStack().length === 1 ? "" : "s"}
              </p>
            </Show>
          </div>
        </div>
      </section>

      <section class="flex min-h-[44rem] flex-1 flex-col rounded-2xl border border-base-300 bg-base-100/70 p-4 shadow-sm md:p-6">
        <ServiceScheduleCalendar
          month={calendarMonth()}
          onMonthChange={(month) => setCalendarMonth(normalizeMonthStart(month))}
          events={calendarEvents()}
          isLoading={serviceEventResource.loading && serviceEvents() === undefined}
          error={serviceEventErrorMessage()}
          onRetry={() => void refetchServiceEvents()}
          eventEditorRole={role()}
          onCreateEventSave={saveServiceEvent}
          canEditEvent={canEditCalendarEvent}
          onEditEventSave={saveCalendarEvent}
          canCompleteEvent={canCompleteCalendarEvent}
          onCompleteEvent={completeCalendarEvent}
          canDeleteEvent={canDeleteCalendarEvent}
          onDeleteEvent={deleteCalendarEvent}
        />
      </section>
    </div>
  );
};
