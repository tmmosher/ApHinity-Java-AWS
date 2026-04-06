import type {
  LocationServiceEvent,
  ServiceEventResponsibility,
  ServiceEventStatus
} from "../../types/Types";
import {compareDates, parseDateValue} from "./dateUtility";

type SelectFilterValue<T extends string> = T | "";

export type ServiceCalendarFilters = {
  responsibility: {
    enabled: boolean;
    value: SelectFilterValue<ServiceEventResponsibility>;
  };
  date: {
    enabled: boolean;
    value: string;
  };
  status: {
    enabled: boolean;
    value: SelectFilterValue<ServiceEventStatus>;
  };
};

const EVENT_DATE_PARSE_ERROR = "Invalid service event date";
const FILTER_DATE_PARSE_ERROR = "Invalid service calendar filter date";

const hasSelectedValue = (value: string): boolean => value.trim().length > 0;

const isResponsibilityFilterActive = (filters: ServiceCalendarFilters): boolean =>
  filters.responsibility.enabled && hasSelectedValue(filters.responsibility.value);

const isDateFilterActive = (filters: ServiceCalendarFilters): boolean =>
  filters.date.enabled && hasSelectedValue(filters.date.value);

const isStatusFilterActive = (filters: ServiceCalendarFilters): boolean =>
  filters.status.enabled && hasSelectedValue(filters.status.value);

const parseSelectedFilterDate = (filters: ServiceCalendarFilters): Date | undefined => {
  if (!isDateFilterActive(filters)) {
    return undefined;
  }

  try {
    return parseDateValue(filters.date.value, FILTER_DATE_PARSE_ERROR);
  } catch {
    return undefined;
  }
};

export const createDefaultServiceCalendarFilters = (): ServiceCalendarFilters => ({
  responsibility: {
    enabled: false,
    value: ""
  },
  date: {
    enabled: false,
    value: ""
  },
  status: {
    enabled: false,
    value: ""
  }
});

export const countActiveServiceCalendarFilters = (filters: ServiceCalendarFilters): number => (
  Number(isResponsibilityFilterActive(filters))
  + Number(parseSelectedFilterDate(filters) !== undefined)
  + Number(isStatusFilterActive(filters))
);

export const filterLocationServiceEvents = (
  events: readonly LocationServiceEvent[],
  filters: ServiceCalendarFilters
): LocationServiceEvent[] => {
  const responsibilityFilter = isResponsibilityFilterActive(filters) ? filters.responsibility.value : "";
  const statusFilter = isStatusFilterActive(filters) ? filters.status.value : "";
  const selectedDate = parseSelectedFilterDate(filters);

  return events.filter((event) => {
    if (responsibilityFilter && event.responsibility !== responsibilityFilter) {
      return false;
    }

    if (statusFilter && event.status !== statusFilter) {
      return false;
    }

    if (!selectedDate) {
      return true;
    }

    const eventStartDate = parseDateValue(event.date, EVENT_DATE_PARSE_ERROR);
    const eventEndDate = parseDateValue(event.endDate, EVENT_DATE_PARSE_ERROR);
    return compareDates(selectedDate, eventStartDate) >= 0 && compareDates(selectedDate, eventEndDate) <= 0;
  });
};
