import type {CreateLocationServiceEventRequest, LocationServiceEvent} from "../../types/Types";
import {applyStateSnapshot, undoStateSnapshot} from "../common/stateHistory";

export type StagedLocationServiceEvent = LocationServiceEvent & {
  isStaged: true;
};

type StagedEventMutationResult = {
  nextEvents: StagedLocationServiceEvent[];
  nextUndoStack: StagedLocationServiceEvent[][];
  changed: boolean;
};

type StagedEventUndoResult = {
  nextEvents: StagedLocationServiceEvent[];
  nextUndoStack: StagedLocationServiceEvent[][];
  undone: boolean;
};

const cloneJson = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T;

export const cloneStagedServiceCalendarEvents = (
  events: StagedLocationServiceEvent[]
): StagedLocationServiceEvent[] => cloneJson(events);

const stagedEventSignature = (event: StagedLocationServiceEvent): string => JSON.stringify({
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
});

const stagedEventListSignature = (events: readonly StagedLocationServiceEvent[]): string => (
  JSON.stringify(events.map(stagedEventSignature))
);

const nextStagedEventId = (events: readonly StagedLocationServiceEvent[]): number => (
  events.reduce((lowestId, event) => Math.min(lowestId, event.id), 0) - 1
);

const createStagedEventFromRequest = (
  request: CreateLocationServiceEventRequest,
  id: number
): StagedLocationServiceEvent => {
  const timestamp = new Date().toISOString();
  return {
    id,
    title: request.title,
    responsibility: request.responsibility,
    date: request.date,
    time: request.time,
    endDate: request.endDate,
    endTime: request.endTime,
    description: request.description,
    status: request.status,
    isCorrectiveAction: false,
    correctiveActionSourceEventId: null,
    correctiveActionSourceEventTitle: null,
    createdAt: timestamp,
    updatedAt: timestamp,
    isStaged: true
  };
};

const updateStagedEventSnapshot = (
  currentEvents: StagedLocationServiceEvent[],
  undoStack: StagedLocationServiceEvent[][],
  nextEvents: StagedLocationServiceEvent[]
): StagedEventMutationResult => {
  const result = applyStateSnapshot(
    currentEvents,
    undoStack,
    nextEvents,
    cloneStagedServiceCalendarEvents,
    (left, right) => stagedEventListSignature(left) === stagedEventListSignature(right)
  );

  return {
    nextEvents: result.nextState,
    nextUndoStack: result.nextUndoStack,
    changed: result.changed
  };
};

export const isStagedServiceCalendarEvent = (
  event: LocationServiceEvent
): event is StagedLocationServiceEvent => (
  "isStaged" in event && event.isStaged === true
);

export const stageImportedServiceCalendarEvents = (
  currentEvents: StagedLocationServiceEvent[],
  undoStack: StagedLocationServiceEvent[][],
  requests: readonly CreateLocationServiceEventRequest[]
): StagedEventMutationResult => {
  let nextId = nextStagedEventId(currentEvents);
  const nextEvents = [
    ...currentEvents,
    ...requests.map((request) => {
      const stagedEvent = createStagedEventFromRequest(request, nextId);
      nextId -= 1;
      return stagedEvent;
    })
  ];

  return updateStagedEventSnapshot(currentEvents, undoStack, nextEvents);
};

export const editStagedServiceCalendarEvent = (
  currentEvents: StagedLocationServiceEvent[],
  undoStack: StagedLocationServiceEvent[][],
  eventId: number,
  request: CreateLocationServiceEventRequest
): StagedEventMutationResult => {
  const nextEvents = currentEvents.map((event) =>
    event.id === eventId
      ? {
          ...event,
          title: request.title,
          responsibility: request.responsibility,
          date: request.date,
          time: request.time,
          endDate: request.endDate,
          endTime: request.endTime,
          description: request.description,
          status: request.status,
          isCorrectiveAction: event.isCorrectiveAction ?? false,
          correctiveActionSourceEventId: event.correctiveActionSourceEventId ?? null,
          correctiveActionSourceEventTitle: event.correctiveActionSourceEventTitle ?? null,
          updatedAt: new Date().toISOString()
        }
      : event
  );

  return updateStagedEventSnapshot(currentEvents, undoStack, nextEvents);
};

export const completeStagedServiceCalendarEvent = (
  currentEvents: StagedLocationServiceEvent[],
  undoStack: StagedLocationServiceEvent[][],
  eventId: number
): StagedEventMutationResult => {
  const nextEvents = currentEvents.map((event) =>
    event.id === eventId
      ? {
          ...event,
          status: "completed" as const,
          updatedAt: new Date().toISOString()
        }
      : event
  );

  return updateStagedEventSnapshot(currentEvents, undoStack, nextEvents);
};

export const deleteStagedServiceCalendarEvent = (
  currentEvents: StagedLocationServiceEvent[],
  undoStack: StagedLocationServiceEvent[][],
  eventId: number
): StagedEventMutationResult => {
  const nextEvents = currentEvents.filter((event) => event.id !== eventId);
  return updateStagedEventSnapshot(currentEvents, undoStack, nextEvents);
};

export const undoStagedServiceCalendarMutation = (
  currentEvents: StagedLocationServiceEvent[],
  undoStack: StagedLocationServiceEvent[][]
): StagedEventUndoResult => {
  const result = undoStateSnapshot(currentEvents, undoStack, cloneStagedServiceCalendarEvents);
  return {
    nextEvents: result.nextState,
    nextUndoStack: result.nextUndoStack,
    undone: result.undone
  };
};

export const buildServiceCalendarRequestsFromStagedEvents = (
  events: readonly StagedLocationServiceEvent[]
): CreateLocationServiceEventRequest[] => (
  events.map((event) => ({
    title: event.title,
    responsibility: event.responsibility,
    date: event.date,
    time: event.time,
    endDate: event.endDate,
    endTime: event.endTime,
    description: event.description,
    status: event.status
  }))
);
