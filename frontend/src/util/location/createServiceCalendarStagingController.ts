import {createMemo, createSignal} from "solid-js";
import type {CreateLocationServiceEventRequest, LocationServiceEvent} from "../../types/Types";
import {applyStateSnapshot, undoStateSnapshot} from "../common/stateHistory";
import {
  cloneStagedServiceCalendarEvents,
  completeStagedServiceCalendarEvent,
  deleteStagedServiceCalendarEvent,
  editStagedServiceCalendarEvent,
  stageImportedServiceCalendarEvents,
  type StagedLocationServiceEvent
} from "./stagedServiceCalendar";

export type StagedServiceCalendarState = {
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
      status: event.status,
      isCorrectiveAction: event.isCorrectiveAction ?? false,
      correctiveActionSourceEventId: event.correctiveActionSourceEventId ?? null,
      correctiveActionSourceEventTitle: event.correctiveActionSourceEventTitle ?? null
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
      status: event.status,
      isCorrectiveAction: event.isCorrectiveAction ?? false,
      correctiveActionSourceEventId: event.correctiveActionSourceEventId ?? null,
      correctiveActionSourceEventTitle: event.correctiveActionSourceEventTitle ?? null
    }))
  })
);

export const createServiceCalendarStagingController = () => {
  const [stagedImportedEvents, setStagedImportedEvents] = createSignal<StagedLocationServiceEvent[]>([]);
  const [stagedDeletedEvents, setStagedDeletedEvents] = createSignal<LocationServiceEvent[]>([]);
  const [stagedCalendarUndoStack, setStagedCalendarUndoStack] = createSignal<StagedServiceCalendarState[]>([]);
  const [isImportingSpreadsheet, setIsImportingSpreadsheet] = createSignal(false);
  const [isApplyingImportedEvents, setIsApplyingImportedEvents] = createSignal(false);

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

  const stageImportedRequests = (
    requests: readonly CreateLocationServiceEventRequest[],
    options: {isCorrectiveAction?: boolean} = {}
  ): boolean => {
    const result = stageImportedServiceCalendarEvents(
      stagedImportedEvents(),
      [],
      requests,
      options
    );
    if (!result.changed) {
      return false;
    }
    return applyStagedCalendarState({
      importedEvents: result.nextEvents,
      deletedEvents: stagedDeletedEvents()
    });
  };

  const editStagedEvent = (
    eventId: number,
    request: CreateLocationServiceEventRequest
  ): boolean => {
    const result = editStagedServiceCalendarEvent(stagedImportedEvents(), [], eventId, request);
    if (!result.changed) {
      return false;
    }
    return applyStagedCalendarState({
      importedEvents: result.nextEvents,
      deletedEvents: stagedDeletedEvents()
    });
  };

  const completeStagedEvent = (eventId: number): boolean => {
    const result = completeStagedServiceCalendarEvent(stagedImportedEvents(), [], eventId);
    if (!result.changed) {
      return false;
    }
    return applyStagedCalendarState({
      importedEvents: result.nextEvents,
      deletedEvents: stagedDeletedEvents()
    });
  };

  const deleteStagedEvent = (eventId: number): boolean => {
    const result = deleteStagedServiceCalendarEvent(stagedImportedEvents(), [], eventId);
    if (!result.changed) {
      return false;
    }
    return applyStagedCalendarState({
      importedEvents: result.nextEvents,
      deletedEvents: stagedDeletedEvents()
    });
  };

  const queuePersistedEventDelete = (event: LocationServiceEvent): boolean => {
    if (stagedDeletedEvents().some((deletedEvent) => deletedEvent.id === event.id)) {
      return false;
    }
    return applyStagedCalendarState({
      importedEvents: stagedImportedEvents(),
      deletedEvents: [...stagedDeletedEvents(), event]
    });
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

  const reset = (): void => {
    setStagedImportedEvents([]);
    setStagedDeletedEvents([]);
    setStagedCalendarUndoStack([]);
    setIsImportingSpreadsheet(false);
    setIsApplyingImportedEvents(false);
  };

  return {
    stagedImportedEvents,
    stagedDeletedEvents,
    stagedCalendarUndoStack,
    isImportingSpreadsheet,
    setIsImportingSpreadsheet,
    isApplyingImportedEvents,
    setIsApplyingImportedEvents,
    hasPendingImportedEventChanges,
    hasStagedImportedEvents,
    isImportedEventMutationBusy,
    reset,
    stageImportedRequests,
    editStagedEvent,
    completeStagedEvent,
    deleteStagedEvent,
    queuePersistedEventDelete,
    undoLastCalendarMutation,
    cloneStagedServiceCalendarEvents
  };
};

export type ServiceCalendarStagingController = ReturnType<typeof createServiceCalendarStagingController>;
