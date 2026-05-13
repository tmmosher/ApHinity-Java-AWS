import type {AccountRole, LocationServiceEvent} from "../../types/Types";
import {
  createServiceEventDraftFromEvent,
  normalizeServiceEventResponsibilityForRole,
  type ServiceEventDraft
} from "./serviceEventForm";

const CORRECTIVE_ACTION_TITLE_PREFIX = "Corrective Action:";

export const isCorrectiveActionServiceEvent = (event: LocationServiceEvent): boolean => (
  event.isCorrectiveAction === true
);

export const canCreateCorrectiveActionForSourceEvent = (
  role: AccountRole | undefined,
  sourceEvent: LocationServiceEvent
): boolean => (
  !isCorrectiveActionServiceEvent(sourceEvent)
  && (
    role === "admin"
    || role === "partner"
    || (role === "client" && sourceEvent.responsibility === "client")
  )
);

export const createCorrectiveActionTitle = (sourceTitle: string): string => {
  const normalizedSourceTitle = sourceTitle.trim();
  return normalizedSourceTitle.toLowerCase().startsWith("corrective action:")
    ? normalizedSourceTitle
    : `${CORRECTIVE_ACTION_TITLE_PREFIX} ${normalizedSourceTitle}`;
};

export const createCorrectiveActionDraftFromSourceEvent = (
  sourceEvent: LocationServiceEvent,
  role: AccountRole | undefined
): ServiceEventDraft => {
  const draft = createServiceEventDraftFromEvent(sourceEvent);
  return {
    ...draft,
    title: createCorrectiveActionTitle(sourceEvent.title),
    responsibility: normalizeServiceEventResponsibilityForRole(role, sourceEvent.responsibility),
    status: "upcoming"
  };
};

export const getCorrectiveActionSourceLabel = (event: LocationServiceEvent): string | undefined => {
  if (!isCorrectiveActionServiceEvent(event)) {
    return undefined;
  }
  if (event.correctiveActionSourceEventTitle && event.correctiveActionSourceEventTitle.trim()) {
    return event.correctiveActionSourceEventTitle;
  }
  if (event.correctiveActionSourceEventId) {
    return `Event #${event.correctiveActionSourceEventId}`;
  }
  return "Unknown event";
};
