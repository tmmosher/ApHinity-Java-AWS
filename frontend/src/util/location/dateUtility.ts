const localizedDateOptions: Intl.DateTimeFormatOptions = {
  year: "numeric",
  month: "numeric",
  day: "numeric"
};

const monthFormatter = new Intl.DateTimeFormat("en-US", {
  month: "long"
});

const weekdayLongFormatter = new Intl.DateTimeFormat("en-US", {
  weekday: "long"
});

const weekdayShortFormatter = new Intl.DateTimeFormat("en-US", {
  weekday: "short"
});

const yearMonthPattern = /^\d{4}-(0[1-9]|1[0-2])$/;

const padNumber = (value: number): string => value.toString().padStart(2, "0");

const normalizeTimeValue = (value: string): string => (
  /^\d{2}:\d{2}$/.test(value) ? `${value}:00` : value
);

export const formatDate = (date: Date): string => {
  const locale = Intl.NumberFormat().resolvedOptions().locale;
  return date.toLocaleDateString(locale, localizedDateOptions);
};

export const formatCurrentDate = (): string => formatDate(new Date());

export const compareDates = (left: Date, right: Date): number => {
  if (!left || !right) {
    throw new Error("Both dates must be provided for comparison");
  }

  const difference = left.getTime() - right.getTime();
  if (difference < 0) {
    return -1;
  }
  if (difference > 0) {
    return 1;
  }
  return 0;
};

export const dateComparison = compareDates;

export const formatDateInputValue = (date: Date): string => (
  `${date.getFullYear()}-${padNumber(date.getMonth() + 1)}-${padNumber(date.getDate())}`
);

export const formatLocationEventMonth = (date: Date): string => (
  `${date.getFullYear()}-${padNumber(date.getMonth() + 1)}`
);

export const normalizeMonthStart = (date: Date): Date => new Date(date.getFullYear(), date.getMonth(), 1);

export const isSameCalendarMonth = (left: Date, right: Date): boolean => (
  left.getFullYear() === right.getFullYear() && left.getMonth() === right.getMonth()
);

export const parseDateInputValue = (value: string): Date => new Date(`${value}T00:00:00`);

export const addDays = (date: Date, days: number): Date => {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next;
};

export const listDateRangeInclusive = (start: Date, end: Date): Date[] => {
  const days: Date[] = [];

  for (let current = new Date(start); compareDates(current, end) <= 0; current = addDays(current, 1)) {
    days.push(current);
  }

  return days;
};

export const parseDateTimeValue = (date: string, time: string, errorMessage: string): Date => {
  const parsed = new Date(`${date}T${normalizeTimeValue(time)}`);
  if (Number.isNaN(parsed.getTime())) {
    throw new Error(errorMessage);
  }
  return parsed;
};

export const parseDateValue = (date: string, errorMessage: string): Date => {
  const parsed = parseDateInputValue(date);
  if (Number.isNaN(parsed.getTime())) {
    throw new Error(errorMessage);
  }
  return parsed;
};

export const normalizeYearMonth = (
  value: string,
  errorMessage = "Invalid service event month"
): string => {
  const normalized = value.trim();
  if (!yearMonthPattern.test(normalized)) {
    throw new Error(errorMessage);
  }
  return normalized;
};

export const formatMonthLabel = (date: Date): string => monthFormatter.format(date);

export const formatWeekdayLong = (date: Date): string => weekdayLongFormatter.format(date);

export const formatWeekdayShort = (date: Date): string => weekdayShortFormatter.format(date);
