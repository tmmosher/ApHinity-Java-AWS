import type {
  AccountRole,
  CreateLocationServiceEventRequest,
  LocationServiceEvent,
  ServiceEventResponsibility,
  ServiceEventStatus
} from "../../types/Types";
import {
  compareDates,
  formatDateInputValue,
  formatTimeInputValue,
  isAllDayTimeRange,
  parseDateTimeValue,
  parseDateValue
} from "./dateUtility";

export type ServiceEventScheduleMode = "timed" | "all-day";

export type ServiceEventDraft = {
  title: string;
  description: string;
  responsibility: ServiceEventResponsibility;
  scheduleMode: ServiceEventScheduleMode;
  startDate: string;
  startTime: string;
  endDate: string;
  endTime: string;
  allDayStartDate: string;
  allDayEndDate: string;
  status: ServiceEventStatus;
};

export const SERVICE_EVENT_TITLE_MAX_LENGTH = 42;

const DEFAULT_START_TIME = "09:00";
const DEFAULT_END_TIME = "10:00";
const ALL_DAY_START_TIME = "00:00";
const ALL_DAY_END_TIME = "23:59:59";

export const canChooseServiceEventResponsibility = (role: AccountRole | undefined): boolean =>
  role === "admin" || role === "partner";

export const canEditLocationServiceEvent = (
  role: AccountRole | undefined,
  responsibility: ServiceEventResponsibility
): boolean => (
  role === "admin" || role === "partner" || responsibility === "client"
);

export const canCompleteLocationServiceEvent = (
  role: AccountRole | undefined,
  responsibility: ServiceEventResponsibility,
  status: ServiceEventStatus
): boolean => (
  status !== "completed" && canEditLocationServiceEvent(role, responsibility)
);

export const canDeleteLocationServiceEvent = (
  role: AccountRole | undefined
): boolean => (
  role === "admin" || role === "partner"
);

export const normalizeServiceEventResponsibilityForRole = (
  role: AccountRole | undefined,
  responsibility: ServiceEventResponsibility
): ServiceEventResponsibility => (
  canChooseServiceEventResponsibility(role) ? responsibility : "client"
);

export const createDefaultServiceEventDraft = (
  role: AccountRole | undefined,
  now: Date = new Date()
): ServiceEventDraft => {
  const today = formatDateInputValue(now);
  return {
    title: "",
    description: "",
    responsibility: normalizeServiceEventResponsibilityForRole(role, "partner"),
    scheduleMode: "timed",
    startDate: today,
    startTime: DEFAULT_START_TIME,
    endDate: today,
    endTime: DEFAULT_END_TIME,
    allDayStartDate: today,
    allDayEndDate: today,
    status: "upcoming"
  };
};

export const createServiceEventDraftFromEvent = (event: LocationServiceEvent): ServiceEventDraft => ({
  title: event.title,
  description: event.description ?? "",
  responsibility: event.responsibility,
  scheduleMode: isAllDayTimeRange(event.time, event.endTime) ? "all-day" : "timed",
  startDate: event.date,
  startTime: formatTimeInputValue(event.time),
  endDate: event.endDate,
  endTime: formatTimeInputValue(event.endTime),
  allDayStartDate: event.date,
  allDayEndDate: event.endDate,
  status: event.status
});

export const createLocationServiceEventRequestFromEvent = (
  event: LocationServiceEvent
): CreateLocationServiceEventRequest => ({
  title: event.title,
  responsibility: event.responsibility,
  date: event.date,
  time: event.time,
  endDate: event.endDate,
  endTime: event.endTime,
  description: event.description,
  status: event.status
});

export const createLocationServiceEventRequestFromDraft = (
  draft: ServiceEventDraft,
  role: AccountRole | undefined
): CreateLocationServiceEventRequest => {
  const normalizedTitle = draft.title.trim();
  if (!normalizedTitle) {
    throw new Error("Event title is required.");
  }
  if (normalizedTitle.length > SERVICE_EVENT_TITLE_MAX_LENGTH) {
    throw new Error("Event title must be 42 characters or fewer.");
  }

  if (draft.scheduleMode === "timed") {
    if (!draft.startDate || !draft.startTime || !draft.endDate || !draft.endTime) {
      throw new Error("Start and end date/time are required.");
    }
    if (
      compareDates(
        parseDateTimeValue(draft.endDate, draft.endTime, "Enter a valid start and end date/time."),
        parseDateTimeValue(draft.startDate, draft.startTime, "Enter a valid start and end date/time.")
      ) < 0
    ) {
      throw new Error("End date and time must be after the start date and time.");
    }

    return {
      title: normalizedTitle,
      responsibility: normalizeServiceEventResponsibilityForRole(role, draft.responsibility),
      date: draft.startDate,
      time: draft.startTime,
      endDate: draft.endDate,
      endTime: draft.endTime,
      description: draft.description.trim() ? draft.description.trim() : null,
      status: draft.status
    };
  }

  if (!draft.allDayStartDate || !draft.allDayEndDate) {
    throw new Error("Start and end dates are required for all-day events.");
  }
  if (
    compareDates(
      parseDateValue(draft.allDayEndDate, "Enter a valid start and end date."),
      parseDateValue(draft.allDayStartDate, "Enter a valid start and end date.")
    ) < 0
  ) {
    throw new Error("End date must be on or after the start date.");
  }

  return {
    title: normalizedTitle,
    responsibility: normalizeServiceEventResponsibilityForRole(role, draft.responsibility),
    date: draft.allDayStartDate,
    time: ALL_DAY_START_TIME,
    endDate: draft.allDayEndDate,
    endTime: ALL_DAY_END_TIME,
    description: draft.description.trim() ? draft.description.trim() : null,
    status: draft.status
  };
};
