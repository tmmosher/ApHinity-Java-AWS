import {createMemo, createSignal, type Accessor} from "solid-js";
import {toast} from "solid-toast";
import type {AccountRole, CreateLocationServiceEventRequest} from "../../../../types/Types";
import {
  buildServiceCalendarSpreadsheetBlob,
  parseServiceCalendarSpreadsheetFile
} from "../../../../util/location/serviceCalendarSpreadsheet";
import {
  buildServiceCalendarRequestsFromStagedEvents,
  completeStagedServiceCalendarEvent,
  deleteStagedServiceCalendarEvent,
  editStagedServiceCalendarEvent,
  stageImportedServiceCalendarEvents,
  type StagedLocationServiceEvent,
  undoStagedServiceCalendarMutation
} from "../../../../util/location/stagedServiceCalendar";
import {uploadLocationEventCalendarById} from "../../../../util/location/locationEventApi";

type ServiceCalendarImportControllerProps = {
  host: string;
  locationId: Accessor<string>;
  locationSessionToken: Accessor<number>;
  role: Accessor<AccountRole | undefined>;
  clearUploadInput: () => void;
  refetchServiceEvents: () => Promise<void>;
};

type StagedEventMutation = {
  nextEvents: StagedLocationServiceEvent[];
  nextUndoStack: StagedLocationServiceEvent[][];
  changed: boolean;
};

const applyStagedMutation = (
  mutation: StagedEventMutation,
  setStagedImportedEvents: (value: StagedLocationServiceEvent[]) => void,
  setStagedImportUndoStack: (value: StagedLocationServiceEvent[][]) => void
): boolean => {
  if (!mutation.changed) {
    return false;
  }

  setStagedImportedEvents(mutation.nextEvents);
  setStagedImportUndoStack(mutation.nextUndoStack);
  return true;
};

export const createServiceCalendarImportController = (props: ServiceCalendarImportControllerProps) => {
  const [stagedImportedEvents, setStagedImportedEvents] = createSignal<StagedLocationServiceEvent[]>([]);
  const [stagedImportUndoStack, setStagedImportUndoStack] = createSignal<StagedLocationServiceEvent[][]>([]);
  const [isImportingSpreadsheet, setIsImportingSpreadsheet] = createSignal(false);
  const [isApplyingImportedEvents, setIsApplyingImportedEvents] = createSignal(false);

  const hasPendingImportedEventChanges = createMemo(() => stagedImportUndoStack().length > 0);
  const hasStagedImportedEvents = createMemo(() => stagedImportedEvents().length > 0);
  const isImportedEventMutationBusy = createMemo(() => (
    isImportingSpreadsheet() || isApplyingImportedEvents()
  ));

  const reset = (): void => {
    setStagedImportedEvents([]);
    setStagedImportUndoStack([]);
    setIsImportingSpreadsheet(false);
    setIsApplyingImportedEvents(false);
  };

  const stageSpreadsheetImportFile = async (file: File): Promise<void> => {
    setIsImportingSpreadsheet(true);
    try {
      const parsedRequests = await parseServiceCalendarSpreadsheetFile(file, props.role());
      const result = stageImportedServiceCalendarEvents(
        stagedImportedEvents(),
        stagedImportUndoStack(),
        parsedRequests
      );
      if (!applyStagedMutation(result, setStagedImportedEvents, setStagedImportUndoStack)) {
        return;
      }

      toast.success(
        `${parsedRequests.length} service event${parsedRequests.length === 1 ? "" : "s"} staged from spreadsheet.`
      );
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to import service calendar spreadsheet.");
    } finally {
      setIsImportingSpreadsheet(false);
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

    const uploadLocationId = props.locationId();
    const uploadSessionToken = props.locationSessionToken();
    setIsApplyingImportedEvents(true);

    try {
      const blob = await buildServiceCalendarSpreadsheetBlob(
        buildServiceCalendarRequestsFromStagedEvents(stagedImportedEvents())
      );
      const file = new File([blob], "service_calendar_upload.xlsx", {type: blob.type});
      const response = await uploadLocationEventCalendarById(props.host, uploadLocationId, file);
      if (uploadLocationId !== props.locationId() || uploadSessionToken !== props.locationSessionToken()) {
        return;
      }

      setStagedImportedEvents([]);
      setStagedImportUndoStack([]);
      props.clearUploadInput();
      toast.success(
        `Imported ${response.importedCount} service event${response.importedCount === 1 ? "" : "s"}.`
      );

      try {
        await props.refetchServiceEvents();
      } catch {
        if (uploadLocationId === props.locationId() && uploadSessionToken === props.locationSessionToken()) {
          toast.error("Service calendar imported, but automatic refresh failed. Please refresh the page.");
        }
      }
    } catch (error) {
      if (uploadLocationId !== props.locationId() || uploadSessionToken !== props.locationSessionToken()) {
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
      if (uploadLocationId === props.locationId() && uploadSessionToken === props.locationSessionToken()) {
        setIsApplyingImportedEvents(false);
      }
    }
  };

  const editStagedEvent = (
    eventId: number,
    request: CreateLocationServiceEventRequest
  ): boolean => applyStagedMutation(
    editStagedServiceCalendarEvent(
      stagedImportedEvents(),
      stagedImportUndoStack(),
      eventId,
      request
    ),
    setStagedImportedEvents,
    setStagedImportUndoStack
  );

  const completeStagedEvent = (eventId: number): boolean => applyStagedMutation(
    completeStagedServiceCalendarEvent(
      stagedImportedEvents(),
      stagedImportUndoStack(),
      eventId
    ),
    setStagedImportedEvents,
    setStagedImportUndoStack
  );

  const deleteStagedEvent = (eventId: number): boolean => applyStagedMutation(
    deleteStagedServiceCalendarEvent(
      stagedImportedEvents(),
      stagedImportUndoStack(),
      eventId
    ),
    setStagedImportedEvents,
    setStagedImportUndoStack
  );

  return {
    stagedImportedEvents,
    stagedImportUndoStack,
    isImportingSpreadsheet,
    isApplyingImportedEvents,
    isImportedEventMutationBusy,
    hasPendingImportedEventChanges,
    hasStagedImportedEvents,
    reset,
    stageSpreadsheetImportFile,
    undoLastImportedEventMutation,
    applyImportedEvents,
    editStagedEvent,
    completeStagedEvent,
    deleteStagedEvent
  };
};
