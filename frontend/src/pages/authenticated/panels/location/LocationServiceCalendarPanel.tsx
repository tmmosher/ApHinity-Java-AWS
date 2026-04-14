import {useParams} from "@solidjs/router";
import {createEffect, createMemo, createResource, createSignal} from "solid-js";
import {toast} from "solid-toast";
import {useApiHost} from "../../../../context/ApiHostContext";
import {useProfile} from "../../../../context/ProfileContext";
import {
  createLocationEventById,
  deleteLocationEventById,
  fetchLocationEventsById,
  getLocationEventTemplateDownloadUrl,
  uploadLocationEventCalendarById,
  updateLocationEventById
} from "../../../../util/location/locationEventApi";
import {formatLocationEventMonth, normalizeMonthStart} from "../../../../util/location/dateUtility";
import type {CreateLocationServiceEventRequest, LocationServiceEvent} from "../../../../types/Types";
import {
  canCompleteLocationServiceEvent,
  canDeleteLocationServiceEvent,
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
  type StagedLocationServiceEvent
} from "../../../../util/location/stagedServiceCalendar";
import {applyStateSnapshot, undoStateSnapshot} from "../../../../util/common/stateHistory";
import ServiceScheduleCalendar from "./ServiceScheduleCalendar";
import {createDashboardLocationResetGuard} from "../../../../util/location/locationView";
import ServiceCalendarPanelToolbar from "../../../../components/service-editor/ServiceCalendarPanelToolbar";

type ServiceEventCalendarResource = {
  locationId: string;
  month: string;
  value: LocationServiceEvent[];
};

type StagedServiceCalendarState = {
  importedEvents: StagedLocationServiceEvent[];
  deletedEvents: LocationServiceEvent[];
};

const cloneStagedServiceCalendarState = (state: StagedServiceCalendarState): StagedServiceCalendarState => (
  JSON.parse(JSON.stringify(state)) as StagedServiceCalendarState
);

const stagedServiceCalendarStateSignature = (state: StagedServiceCalendarState): string => (
  JSON.stringify({
    importedEvents: state.importedEvents.map((event) => ({
      id: event.id,
      title: event.title,
      responsibility: event.responsibility,
      date: event.date,
      time: event.time,
      endDate: event.endDate,
      endTime: event.endTime,
      description: event.description,
      status: event.status
    })),
    deletedEvents: state.deletedEvents.map((event) => ({
      id: event.id,
      title: event.title,
      responsibility: event.responsibility,
      date: event.date,
      time: event.time,
      endDate: event.endDate,
      endTime: event.endTime,
      description: event.description,
      status: event.status
    }))
  })
);

export const LocationServiceCalendarPanel = () => {
  const host = useApiHost();
  const profileContext = useProfile();
  const params = useParams<{ locationId: string }>();
  const [calendarMonth, setCalendarMonth] = createSignal(normalizeMonthStart(new Date()));
  const [stagedImportedEvents, setStagedImportedEvents] = createSignal<StagedLocationServiceEvent[]>([]);
  const [stagedDeletedEvents, setStagedDeletedEvents] = createSignal<LocationServiceEvent[]>([]);
  const [stagedCalendarUndoStack, setStagedCalendarUndoStack] = createSignal<StagedServiceCalendarState[]>([]);
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
  const hasPendingImportedEventChanges = createMemo(() => stagedCalendarUndoStack().length > 0);
  const hasStagedImportedEvents = createMemo(() => stagedImportedEvents().length > 0);
  const isImportedEventMutationBusy = createMemo(() => (
    isImportingSpreadsheet() || isApplyingImportedEvents()
  ));
  const currentStagedCalendarState = (): StagedServiceCalendarState => ({
    importedEvents: stagedImportedEvents(),
    deletedEvents: stagedDeletedEvents()
  });
  const applyStagedCalendarState = (nextState: StagedServiceCalendarState): boolean => {
    const result = applyStateSnapshot(
      currentStagedCalendarState(),
      stagedCalendarUndoStack(),
      nextState,
      cloneStagedServiceCalendarState,
      (left, right) => stagedServiceCalendarStateSignature(left) === stagedServiceCalendarStateSignature(right)
    );
    if (!result.changed) {
      return false;
    }

    setStagedImportedEvents(result.nextState.importedEvents);
    setStagedDeletedEvents(result.nextState.deletedEvents);
    setStagedCalendarUndoStack(result.nextUndoStack);
    return true;
  };
  const undoLastCalendarMutation = (): void => {
    if (isImportedEventMutationBusy()) {
      return;
    }

    const result = undoStateSnapshot(
      currentStagedCalendarState(),
      stagedCalendarUndoStack(),
      cloneStagedServiceCalendarState
    );
    if (!result.undone) {
      return;
    }

    setStagedImportedEvents(result.nextState.importedEvents);
    setStagedDeletedEvents(result.nextState.deletedEvents);
    setStagedCalendarUndoStack(result.nextUndoStack);
  };
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
  const visibleServiceEvents = createMemo<LocationServiceEvent[]>(() => {
    const deletedIds = new Set(stagedDeletedEvents().map((event) => event.id));
    return (serviceEvents() ?? []).filter((event) => !deletedIds.has(event.id));
  });
  const calendarEvents = createMemo<LocationServiceEvent[]>(() => [
    ...visibleServiceEvents(),
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
    setStagedDeletedEvents([]);
    setStagedCalendarUndoStack([]);
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
        [],
        parsedRequests
      );
      if (!result.changed) {
        return;
      }

      applyStagedCalendarState({
        importedEvents: result.nextEvents,
        deletedEvents: stagedDeletedEvents()
      });
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

  const applyImportedEvents = async (): Promise<void> => {
    if (
      isImportedEventMutationBusy()
      || (stagedImportedEvents().length === 0 && stagedDeletedEvents().length === 0)
    ) {
      return;
    }

    const uploadLocationId = params.locationId;
    const uploadSessionToken = locationSessionToken();
    const importedEvents = stagedImportedEvents();
    const deletedEvents = stagedDeletedEvents();
    setIsApplyingImportedEvents(true);

    try {
      for (const deletedEvent of deletedEvents) {
        await deleteLocationEventById(host, uploadLocationId, deletedEvent.id);
      }

      let importedCount = 0;
      if (importedEvents.length > 0) {
        const blob = await buildServiceCalendarSpreadsheetBlob(
          buildServiceCalendarRequestsFromStagedEvents(importedEvents)
        );
        const file = new File([blob], "service_calendar_upload.xlsx", {type: blob.type});
        const response = await uploadLocationEventCalendarById(host, uploadLocationId, file);
        importedCount = response.importedCount;
      }

      if (uploadLocationId !== params.locationId || uploadSessionToken !== locationSessionToken()) {
        return;
      }

      setStagedImportedEvents([]);
      setStagedDeletedEvents([]);
      setStagedCalendarUndoStack([]);
      clearSpreadsheetUploadInput();

      if (importedCount > 0 && deletedEvents.length > 0) {
        toast.success(
          `Imported ${importedCount} service event${importedCount === 1 ? "" : "s"} and deleted ${deletedEvents.length} service event${deletedEvents.length === 1 ? "" : "s"}.`
        );
      } else if (importedCount > 0) {
        toast.success(
          `Imported ${importedCount} service event${importedCount === 1 ? "" : "s"}.`
        );
      } else {
        toast.success(
          `Deleted ${deletedEvents.length} service event${deletedEvents.length === 1 ? "" : "s"}.`
        );
      }

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
      [],
      event.id,
      request
    );
    if (!result.changed) {
      return;
    }

    applyStagedCalendarState({
      importedEvents: result.nextEvents,
      deletedEvents: stagedDeletedEvents()
    });
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
      [],
      event.id
    );
    if (!result.changed) {
      return;
    }

    applyStagedCalendarState({
      importedEvents: result.nextEvents,
      deletedEvents: stagedDeletedEvents()
    });
    toast.success("Staged service event marked complete.");
  };

  const deleteCalendarEvent = async (event: LocationServiceEvent): Promise<void> => {
    if (isImportedEventMutationBusy()) {
      throw new Error("Imported service events are currently busy.");
    }

    if (isStagedServiceCalendarEvent(event)) {
      const result = deleteStagedServiceCalendarEvent(
        stagedImportedEvents(),
        [],
        event.id
      );
      if (!result.changed) {
        return;
      }

      applyStagedCalendarState({
        importedEvents: result.nextEvents,
        deletedEvents: stagedDeletedEvents()
      });
      toast.success("Staged service event deleted.");
      return;
    }

    if (!canDeleteLocationServiceEvent(role())) {
      throw new Error("Only partners and admins can delete persisted service events.");
    }
    if (stagedDeletedEvents().some((deletedEvent) => deletedEvent.id === event.id)) {
      return;
    }

    applyStagedCalendarState({
      importedEvents: stagedImportedEvents(),
      deletedEvents: [...stagedDeletedEvents(), event]
    });
    toast.success("Service event queued for deletion.");
  };

  const canDeleteCalendarEvent = (event: LocationServiceEvent): boolean => (
    !isImportedEventMutationBusy()
    && (isStagedServiceCalendarEvent(event) || (
      canDeleteLocationServiceEvent(role())
      && !stagedDeletedEvents().some((deletedEvent) => deletedEvent.id === event.id)
    ))
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
        <ServiceCalendarPanelToolbar
          templateHref={serviceCalendarTemplateHref()}
          spreadsheetUploadInputRef={(element: HTMLInputElement | undefined) => {
            spreadsheetUploadInput = element;
          }}
          isMutationBusy={isImportedEventMutationBusy()}
          isApplyingImportedEvents={isApplyingImportedEvents()}
          hasStagedImportedEvents={hasStagedImportedEvents() || stagedDeletedEvents().length > 0}
          hasPendingImportedEventChanges={hasPendingImportedEventChanges()}
          stagedEventCount={stagedImportedEvents().length + stagedDeletedEvents().length}
          pendingMutationCount={stagedCalendarUndoStack().length}
          onSpreadsheetInputChange={(event: Event) => {
            void stageSpreadsheetImport(event);
          }}
          onApply={() => {
            void applyImportedEvents();
          }}
          onUndo={undoLastCalendarMutation}
        />
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
