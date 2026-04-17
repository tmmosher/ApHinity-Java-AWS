import type {AccountRole, LocationServiceEvent} from "../../types/Types";
import {
  createServiceEventDraftFromEvent,
  normalizeServiceEventResponsibilityForRole,
  SERVICE_EVENT_TITLE_MAX_LENGTH,
  type ServiceEventDraft
} from "./serviceEventForm";

const CORRECTIVE_ACTION_TITLE_PREFIX = "Corrective Action:";

const trimTitleToMaxLength = (value: string): string => (
  value.length <= SERVICE_EVENT_TITLE_MAX_LENGTH
    ? value
    : value.slice(0, SERVICE_EVENT_TITLE_MAX_LENGTH)
);

export const isCorrectiveActionServiceEvent = (event: LocationServiceEvent): boolean => (
  event.isCorrectiveAction === true
);

export const createCorrectiveActionTitle = (sourceTitle: string): string => {
  const normalizedSourceTitle = sourceTitle.trim();
  const prefixedTitle = normalizedSourceTitle.toLowerCase().startsWith("corrective action:")
    ? normalizedSourceTitle
    : `${CORRECTIVE_ACTION_TITLE_PREFIX} ${normalizedSourceTitle}`;
  return trimTitleToMaxLength(prefixedTitle);
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
