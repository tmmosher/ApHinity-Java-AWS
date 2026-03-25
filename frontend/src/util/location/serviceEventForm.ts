import type {
  AccountRole,
  CreateLocationServiceEventRequest,
  ServiceEventResponsibility
} from "../../types/Types";

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
};

const DEFAULT_START_TIME = "09:00";
const DEFAULT_END_TIME = "10:00";
const ALL_DAY_START_TIME = "00:00";
const ALL_DAY_END_TIME = "23:59:59";

const padNumber = (value: number): string => value.toString().padStart(2, "0");

export const formatDateInputValue = (date: Date): string => (
  `${date.getFullYear()}-${padNumber(date.getMonth() + 1)}-${padNumber(date.getDate())}`
);

export const canChooseServiceEventResponsibility = (role: AccountRole | undefined): boolean =>
  role === "admin" || role === "partner";

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
    allDayEndDate: today
  };
};

const parseTimedDate = (date: string, time: string): number => {
  const parsed = Date.parse(`${date}T${time}:00`);
  if (Number.isNaN(parsed)) {
    throw new Error("Enter a valid start and end date/time.");
  }
  return parsed;
};

const parseDateOnly = (date: string): number => {
  const parsed = Date.parse(`${date}T00:00:00`);
  if (Number.isNaN(parsed)) {
    throw new Error("Enter a valid start and end date.");
  }
  return parsed;
};

export const createLocationServiceEventRequestFromDraft = (
  draft: ServiceEventDraft,
  role: AccountRole | undefined
): CreateLocationServiceEventRequest => {
  const normalizedTitle = draft.title.trim();
  if (!normalizedTitle) {
    throw new Error("Event title is required.");
  }

  if (draft.scheduleMode === "timed") {
    if (!draft.startDate || !draft.startTime || !draft.endDate || !draft.endTime) {
      throw new Error("Start and end date/time are required.");
    }
    if (parseTimedDate(draft.endDate, draft.endTime) < parseTimedDate(draft.startDate, draft.startTime)) {
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
      status: "upcoming"
    };
  }

  if (!draft.allDayStartDate || !draft.allDayEndDate) {
    throw new Error("Start and end dates are required for all-day events.");
  }
  if (parseDateOnly(draft.allDayEndDate) < parseDateOnly(draft.allDayStartDate)) {
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
    status: "upcoming"
  };
};
