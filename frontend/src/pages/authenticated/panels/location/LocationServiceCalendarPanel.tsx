import {useParams} from "@solidjs/router";
import {createEffect, createMemo, createResource, createSignal} from "solid-js";
import {toast} from "solid-toast";
import {useApiHost} from "../../../../context/ApiHostContext";
import {useProfile} from "../../../../context/ProfileContext";
import {
  createLocationCorrectiveActionById,
  createLocationEventsBulkById,
  createLocationEventById,
  deleteLocationEventById,
  fetchLocationEventsById,
  getLocationEventTemplateDownloadUrl,
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
import {parseServiceCalendarSpreadsheetFile} from "../../../../util/location/serviceCalendarSpreadsheet";
import {
  buildServiceCalendarBulkRequestsFromStagedEvents,
  isStagedServiceCalendarEvent
} from "../../../../util/location/stagedServiceCalendar";
import ServiceScheduleCalendar from "./ServiceScheduleCalendar";
import {createDashboardLocationResetGuard} from "../../../../util/location/locationView";
import ServiceCalendarPanelToolbar from "../../../../components/service-editor/ServiceCalendarPanelToolbar";
import {useLocationDetail} from "../../../../context/LocationDetailContext";

type ServiceEventCalendarResource = {
  locationId: string;
  month: string;
  value: LocationServiceEvent[];
};

export const LocationServiceCalendarPanel = () => {
  const host = useApiHost();
  const profileContext = useProfile();
  const {serviceCalendarStaging} = useLocationDetail();
  const params = useParams<{ locationId: string }>();
  const [calendarMonth, setCalendarMonth] = createSignal(normalizeMonthStart(new Date()));
  const [locationSessionToken, setLocationSessionToken] = createSignal(0);
  const shouldResetCalendarState = createDashboardLocationResetGuard(params.locationId);
  const role = createMemo(() => profileContext.profile()?.role);
  let spreadsheetUploadInput: HTMLInputElement | undefined;

  const viewedMonth = createMemo(() => formatLocationEventMonth(calendarMonth()));
  const serviceCalendarTemplateHref = createMemo(() =>
    getLocationEventTemplateDownloadUrl(host, params.locationId)
  );
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
    const deletedIds = new Set(serviceCalendarStaging.stagedDeletedEvents().map((event) => event.id));
    return (serviceEvents() ?? []).filter((event) => !deletedIds.has(event.id));
  });
  const calendarEvents = createMemo<LocationServiceEvent[]>(() => [
    ...visibleServiceEvents(),
    ...serviceCalendarStaging.stagedImportedEvents()
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
    serviceCalendarStaging.reset();
    setLocationSessionToken((token) => token + 1);
    clearSpreadsheetUploadInput();
  });

  const saveServiceEvent = async (request: CreateLocationServiceEventRequest): Promise<void> => {
    await createLocationEventById(host, params.locationId, request);
    await refetchServiceEvents();
    toast.success("Service event created");
  };

  const saveEditedServiceEvent = async (
    event: LocationServiceEvent,
    request: CreateLocationServiceEventRequest
  ): Promise<void> => {
    await updateLocationEventById(host, params.locationId, event.id, request);
    await refetchServiceEvents();
    toast.success("Service event updated");
  };

  const createCorrectiveActionFromEvent = async (
    sourceEvent: LocationServiceEvent,
    request: CreateLocationServiceEventRequest
  ): Promise<void> => {
    if (isStagedServiceCalendarEvent(sourceEvent)) {
      throw new Error("Corrective actions can only be created from persisted service events.");
    }

    await createLocationCorrectiveActionById(host, params.locationId, sourceEvent.id, request);
    await refetchServiceEvents();
    toast.success("Corrective action created");
  };

  const completeServiceEvent = async (event: LocationServiceEvent): Promise<void> => {
    await updateLocationEventById(host, params.locationId, event.id, {
      ...createLocationServiceEventRequestFromEvent(event),
      status: "completed"
    });
    await refetchServiceEvents();
    toast.success("Service event marked complete");
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

    serviceCalendarStaging.setIsImportingSpreadsheet(true);
    try {
      const parsedRequests = await parseServiceCalendarSpreadsheetFile(file, role());
      if (!serviceCalendarStaging.stageImportedRequests(parsedRequests)) {
        return;
      }
      toast.success(
        `${parsedRequests.length} service event${parsedRequests.length === 1 ? "" : "s"} staged from spreadsheet`
      );
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to import service calendar spreadsheet");
    } finally {
      serviceCalendarStaging.setIsImportingSpreadsheet(false);
      input.value = "";
    }
  };

  const applyImportedEvents = async (): Promise<void> => {
    if (
      serviceCalendarStaging.isImportedEventMutationBusy()
      || (serviceCalendarStaging.stagedImportedEvents().length === 0
        && serviceCalendarStaging.stagedDeletedEvents().length === 0)
    ) {
      return;
    }

    const uploadLocationId = params.locationId;
    const uploadSessionToken = locationSessionToken();
    const importedEvents = serviceCalendarStaging.stagedImportedEvents();
    const deletedEvents = serviceCalendarStaging.stagedDeletedEvents();
    serviceCalendarStaging.setIsApplyingImportedEvents(true);

    try {
      for (const deletedEvent of deletedEvents) {
        await deleteLocationEventById(host, uploadLocationId, deletedEvent.id);
      }

      let importedCount = 0;
      if (importedEvents.length > 0) {
        const response = await createLocationEventsBulkById(
          host,
          uploadLocationId,
          buildServiceCalendarBulkRequestsFromStagedEvents(importedEvents)
        );
        importedCount = response.importedCount;
      }

      if (uploadLocationId !== params.locationId || uploadSessionToken !== locationSessionToken()) {
        return;
      }

      serviceCalendarStaging.reset();
      clearSpreadsheetUploadInput();

      if (importedCount > 0 && deletedEvents.length > 0) {
        toast.success(
          `Imported ${importedCount} service event${importedCount === 1 ? "" : "s"} and deleted ${deletedEvents.length} service event${deletedEvents.length === 1 ? "" : "s"}`
        );
      } else if (importedCount > 0) {
        toast.success(
          `Imported ${importedCount} service event${importedCount === 1 ? "" : "s"}`
        );
      } else {
        toast.success(
          `Deleted ${deletedEvents.length} service event${deletedEvents.length === 1 ? "" : "s"}`
        );
      }

      try {
        await refetchServiceEvents();
      } catch {
        if (uploadLocationId === params.locationId && uploadSessionToken === locationSessionToken()) {
          toast.error("Service calendar imported, but automatic refresh failed. Please refresh the page");
        }
      }
    } catch (error) {
      if (uploadLocationId !== params.locationId || uploadSessionToken !== locationSessionToken()) {
        return;
      }

      if (error instanceof Error && error.message === "CSRF invalid") {
        toast.error("Security token refresh failed. Please retry Apply; your staged import is still local");
        return;
      }
      if (error instanceof Error && error.message === "Security token rejected") {
        toast.error("Security validation failed. Retrying Apply usually succeeds without losing staged imports");
        return;
      }
      if (error instanceof Error && error.message === "Authentication required") {
        toast.error("Session refresh failed. Please sign in again; your staged import is still on this page");
        return;
      }
      if (error instanceof Error && error.message === "Insufficient permissions") {
        toast.error("You no longer have permission to import service calendar events");
        return;
      }

      toast.error(error instanceof Error ? error.message : "Unable to upload service calendar spreadsheet");
    } finally {
      if (uploadLocationId === params.locationId && uploadSessionToken === locationSessionToken()) {
        serviceCalendarStaging.setIsApplyingImportedEvents(false);
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
    if (serviceCalendarStaging.isImportedEventMutationBusy()) {
      throw new Error("Imported service events are currently busy.");
    }

    serviceCalendarStaging.editStagedEvent(event.id, request);
    toast.success("Staged service event updated");
  };

  const completeCalendarEvent = async (event: LocationServiceEvent): Promise<void> => {
    if (!isStagedServiceCalendarEvent(event)) {
      await completeServiceEvent(event);
      return;
    }
    if (serviceCalendarStaging.isImportedEventMutationBusy()) {
      throw new Error("Imported service events are currently busy.");
    }

    serviceCalendarStaging.completeStagedEvent(event.id);
    toast.success("Staged service event marked complete");
  };

  const deleteCalendarEvent = async (event: LocationServiceEvent): Promise<void> => {
    if (serviceCalendarStaging.isImportedEventMutationBusy()) {
      throw new Error("Imported service events are currently busy.");
    }

    if (isStagedServiceCalendarEvent(event)) {
      if (!serviceCalendarStaging.deleteStagedEvent(event.id)) {
        return;
      }
      toast.success("Staged service event deleted");
      return;
    }

    if (!canDeleteLocationServiceEvent(role())) {
      throw new Error("Only partners and admins can delete persisted service events.");
    }
    if (serviceCalendarStaging.stagedDeletedEvents().some((deletedEvent) => deletedEvent.id === event.id)) {
      return;
    }

    serviceCalendarStaging.queuePersistedEventDelete(event);
    toast.success("Service event queued for deletion");
  };

  const canDeleteCalendarEvent = (event: LocationServiceEvent): boolean => (
    !serviceCalendarStaging.isImportedEventMutationBusy()
    && (isStagedServiceCalendarEvent(event) || (
      canDeleteLocationServiceEvent(role())
      && !serviceCalendarStaging.stagedDeletedEvents().some((deletedEvent) => deletedEvent.id === event.id)
    ))
  );
  const canEditCalendarEvent = (event: LocationServiceEvent): boolean => (
    canEditLocationServiceEvent(role(), event.responsibility)
    && (!isStagedServiceCalendarEvent(event) || !serviceCalendarStaging.isImportedEventMutationBusy())
  );
  const canCompleteCalendarEvent = (event: LocationServiceEvent): boolean => (
    canCompleteLocationServiceEvent(role(), event.responsibility, event.status)
    && (!isStagedServiceCalendarEvent(event) || !serviceCalendarStaging.isImportedEventMutationBusy())
  );

  return (
    <div class="flex min-h-[calc(100vh-16rem)] flex-col gap-4">
      <section class="rounded-2xl border border-base-300 bg-base-100/70 p-6 shadow-sm">
        <ServiceCalendarPanelToolbar
          templateHref={serviceCalendarTemplateHref()}
          spreadsheetUploadInputRef={(element: HTMLInputElement | undefined) => {
            spreadsheetUploadInput = element;
          }}
          isMutationBusy={serviceCalendarStaging.isImportedEventMutationBusy()}
          isApplyingImportedEvents={serviceCalendarStaging.isApplyingImportedEvents()}
          hasStagedImportedEvents={
            serviceCalendarStaging.hasStagedImportedEvents()
            || serviceCalendarStaging.stagedDeletedEvents().length > 0
          }
          hasPendingImportedEventChanges={serviceCalendarStaging.hasPendingImportedEventChanges()}
          stagedEventCount={
            serviceCalendarStaging.stagedImportedEvents().length
            + serviceCalendarStaging.stagedDeletedEvents().length
          }
          pendingMutationCount={serviceCalendarStaging.stagedCalendarUndoStack().length}
          onSpreadsheetInputChange={(event: Event) => {
            void stageSpreadsheetImport(event);
          }}
          onApply={() => {
            void applyImportedEvents();
          }}
          onUndo={serviceCalendarStaging.undoLastCalendarMutation}
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
          onCreateCorrectiveAction={createCorrectiveActionFromEvent}
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
