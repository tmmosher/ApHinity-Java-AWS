import type {
  AccountRole,
  CreateLocationServiceEventRequest,
  ServiceEventResponsibility,
  ServiceEventStatus
} from "../../types/Types";
import {SERVICE_EVENT_TITLE_MAX_LENGTH, canChooseServiceEventResponsibility} from "./serviceEventForm";
import {
  getFirstWorksheetFromWorkbook,
  isEmptyXlsxRow as isEmptySpreadsheetRow,
  loadXlsxModule,
  loadXlsxWorkbookFromFile,
  normalizeXlsxCellString as normalizeCellString,
  parseXlsxDateCell as parseDateCell,
  parseXlsxSheetRows as parseSheetRows,
  validateXlsxHeaders as validateHeaders
} from "./xlsxSpreadsheet";

const SERVICE_CALENDAR_HEADERS = [
  "Title",
  "Description",
  "Start Date",
  "End Date",
  "Start Time",
  "End Time",
  "All Day",
  "Responsibility"
] as const;
const OPTIONAL_SERVICE_CALENDAR_HEADERS = ["Status"] as const;

const ALL_DAY_START_TIME = "00:00:00";
const ALL_DAY_END_TIME = "23:59:59";
const DEFAULT_STATUS = "upcoming" as const;

type SpreadsheetHeader = (typeof SERVICE_CALENDAR_HEADERS)[number];
type OptionalSpreadsheetHeader = (typeof OPTIONAL_SERVICE_CALENDAR_HEADERS)[number];

type SpreadsheetRowRecord = Partial<Record<SpreadsheetHeader | OptionalSpreadsheetHeader, unknown>>;

const padNumber = (value: number): string => value.toString().padStart(2, "0");

const formatTimeValue = (hours: number, minutes: number, seconds: number): string => (
  `${padNumber(hours)}:${padNumber(minutes)}:${padNumber(seconds)}`
);

const parseBooleanCell = (value: unknown, rowNumber: number): boolean => {
  if (value === null || value === undefined || value === "") {
    return false;
  }
  if (typeof value === "boolean") {
    return value;
  }
  if (typeof value === "number") {
    if (value === 1) {
      return true;
    }
    if (value === 0) {
      return false;
    }
  }

  const normalized = normalizeCellString(value).toLowerCase();
  if (["true", "yes", "y", "1"].includes(normalized)) {
    return true;
  }
  if (["false", "no", "n", "0", ""].includes(normalized)) {
    return false;
  }

  throw new Error(`Row ${rowNumber}: All Day must be True or False.`);
};

const parseResponsibilityCell = (
  value: unknown,
  role: AccountRole | undefined,
  rowNumber: number
): ServiceEventResponsibility => {
  const normalized = normalizeCellString(value).toLowerCase();
  if (normalized !== "client" && normalized !== "partner") {
    throw new Error(`Row ${rowNumber}: Responsibility must be Client or Partner.`);
  }
  if (normalized === "partner" && !canChooseServiceEventResponsibility(role)) {
    throw new Error(`Row ${rowNumber}: You do not have permission to import Partner events.`);
  }
  return normalized;
};

const parseStatusCell = (value: unknown, rowNumber: number): ServiceEventStatus => {
  const normalized = normalizeCellString(value).toLowerCase();
  if (!normalized) {
    return DEFAULT_STATUS;
  }
  if (
    normalized === "upcoming"
    || normalized === "current"
    || normalized === "overdue"
    || normalized === "completed"
  ) {
    return normalized;
  }
  throw new Error(`Row ${rowNumber}: Status must be Upcoming, Current, Overdue, or Completed.`);
};

const parseTimeString = (value: string): string | null => {
  const twentyFourHourMatch = /^(\d{1,2}):(\d{2})(?::(\d{2}))?$/.exec(value);
  if (twentyFourHourMatch) {
    const hours = Number(twentyFourHourMatch[1]);
    const minutes = Number(twentyFourHourMatch[2]);
    const seconds = Number(twentyFourHourMatch[3] ?? "0");
    if (hours < 24 && minutes < 60 && seconds < 60) {
      return formatTimeValue(hours, minutes, seconds);
    }
    return null;
  }

  const twelveHourMatch = /^(\d{1,2}):(\d{2})(?::(\d{2}))?\s*([AP]M)$/i.exec(value);
  if (!twelveHourMatch) {
    return null;
  }

  const hoursValue = Number(twelveHourMatch[1]);
  const minutes = Number(twelveHourMatch[2]);
  const seconds = Number(twelveHourMatch[3] ?? "0");
  if (hoursValue < 1 || hoursValue > 12 || minutes >= 60 || seconds >= 60) {
    return null;
  }

  const meridiem = twelveHourMatch[4].toUpperCase();
  const normalizedHours = meridiem === "PM"
    ? (hoursValue % 12) + 12
    : hoursValue % 12;

  return formatTimeValue(normalizedHours, minutes, seconds);
};

const parseTimeCell = (value: unknown, rowNumber: number, label: "Start Time" | "End Time"): string | null => {
  if (value === null || value === undefined || value === "") {
    return null;
  }
  if (value instanceof Date) {
    if (Number.isNaN(value.getTime())) {
      throw new Error(`Row ${rowNumber}: ${label} is invalid.`);
    }
    return formatTimeValue(value.getHours(), value.getMinutes(), value.getSeconds());
  }
  if (typeof value === "number") {
    const totalSeconds = Math.round((value % 1) * 24 * 60 * 60);
    const normalizedSeconds = ((totalSeconds % (24 * 60 * 60)) + (24 * 60 * 60)) % (24 * 60 * 60);
    const hours = Math.floor(normalizedSeconds / 3600);
    const minutes = Math.floor((normalizedSeconds % 3600) / 60);
    const seconds = normalizedSeconds % 60;
    return formatTimeValue(hours, minutes, seconds);
  }
  if (typeof value === "string") {
    const normalized = value.trim();
    if (!normalized) {
      return null;
    }
    const parsed = parseTimeString(normalized);
    if (parsed) {
      return parsed;
    }
  }

  throw new Error(`Row ${rowNumber}: ${label} is invalid.`);
};

const validateDateRange = (
  startDate: string,
  startTime: string,
  endDate: string,
  endTime: string,
  rowNumber: number
): void => {
  const startDateTime = new Date(`${startDate}T${startTime}`);
  const endDateTime = new Date(`${endDate}T${endTime}`);
  if (Number.isNaN(startDateTime.getTime()) || Number.isNaN(endDateTime.getTime())) {
    throw new Error(`Row ${rowNumber}: Start and end date/time are invalid.`);
  }
  if (endDateTime.getTime() < startDateTime.getTime()) {
    throw new Error(`Row ${rowNumber}: End date and time must be on or after the start date and time.`);
  }
};

const parseSpreadsheetRow = (
  row: SpreadsheetRowRecord,
  role: AccountRole | undefined,
  rowNumber: number
): CreateLocationServiceEventRequest => {
  const title = normalizeCellString(row.Title);
  if (!title) {
    throw new Error(`Row ${rowNumber}: Title is required.`);
  }
  if (title.length > SERVICE_EVENT_TITLE_MAX_LENGTH) {
    throw new Error(`Row ${rowNumber}: Title must be 42 characters or fewer.`);
  }

  const description = normalizeCellString(row.Description);
  const allDay = parseBooleanCell(row["All Day"], rowNumber);
  const responsibility = parseResponsibilityCell(row.Responsibility, role, rowNumber);
  const status = parseStatusCell(row.Status, rowNumber);
  const startDate = parseDateCell(row["Start Date"], rowNumber, "Start Date");
  const endDate = parseDateCell(row["End Date"], rowNumber, "End Date");

  if (!startDate) {
    throw new Error(`Row ${rowNumber}: Start Date is required.`);
  }

  if (allDay) {
    const resolvedEndDate = endDate ?? startDate;
    validateDateRange(
      startDate,
      ALL_DAY_START_TIME,
      resolvedEndDate,
      ALL_DAY_END_TIME,
      rowNumber
    );

    return {
      title,
      description: description || null,
      responsibility,
      date: startDate,
      time: ALL_DAY_START_TIME,
      endDate: resolvedEndDate,
      endTime: ALL_DAY_END_TIME,
      status
    };
  }

  const startTime = parseTimeCell(row["Start Time"], rowNumber, "Start Time");
  const endTime = parseTimeCell(row["End Time"], rowNumber, "End Time");
  if (!startTime) {
    throw new Error(`Row ${rowNumber}: Start Time is required.`);
  }

  const resolvedEndDate = endDate ?? startDate;
  const resolvedEndTime = endTime ?? startTime;
  validateDateRange(startDate, startTime, resolvedEndDate, resolvedEndTime, rowNumber);

  return {
    title,
    description: description || null,
    responsibility,
    date: startDate,
    time: startTime,
    endDate: resolvedEndDate,
    endTime: resolvedEndTime,
    status
  };
};

export const parseServiceCalendarSpreadsheetFile = async (
  file: File,
  role: AccountRole | undefined
): Promise<CreateLocationServiceEventRequest[]> => {
  const {xlsx, workbook} = await loadXlsxWorkbookFromFile(file, {
    readOptions: {
      cellDates: true
    }
  });
  const sheet = getFirstWorksheetFromWorkbook(
    workbook,
    "Spreadsheet does not contain any worksheets.",
    "Spreadsheet worksheet could not be read."
  );

  validateHeaders(sheet, xlsx, SERVICE_CALENDAR_HEADERS);

  const parsedEvents = parseSheetRows(sheet, xlsx)
    .map((row, index) => ({row, rowNumber: index + 2}))
    .filter(({row}) => !isEmptySpreadsheetRow(row, SERVICE_CALENDAR_HEADERS))
    .map(({row, rowNumber}) => parseSpreadsheetRow(row, role, rowNumber));

  if (parsedEvents.length === 0) {
    throw new Error("Spreadsheet does not contain any service calendar events.");
  }

  return parsedEvents;
};

const formatSpreadsheetTime = (value: string): string => value.slice(0, 8);

const formatSpreadsheetResponsibility = (value: ServiceEventResponsibility): string => (
  value === "client" ? "Client" : "Partner"
);

const isAllDayRequest = (request: CreateLocationServiceEventRequest): boolean => (
  request.time === ALL_DAY_START_TIME && request.endTime.startsWith("23:59")
);

export const buildServiceCalendarSpreadsheetBlob = async (
  requests: readonly CreateLocationServiceEventRequest[]
): Promise<Blob> => {
  const xlsx = await loadXlsxModule();
  const rows = [
    [...SERVICE_CALENDAR_HEADERS, ...OPTIONAL_SERVICE_CALENDAR_HEADERS],
    ...requests.map((request) => {
      const allDay = isAllDayRequest(request);
      return [
        request.title,
        request.description ?? "",
        request.date,
        request.endDate,
        allDay ? "" : formatSpreadsheetTime(request.time),
        allDay ? "" : formatSpreadsheetTime(request.endTime),
        allDay ? "True" : "False",
        formatSpreadsheetResponsibility(request.responsibility),
        request.status.charAt(0).toUpperCase() + request.status.slice(1)
      ];
    })
  ];

  const workbook = xlsx.utils.book_new();
  const sheet = xlsx.utils.aoa_to_sheet(rows);
  xlsx.utils.book_append_sheet(workbook, sheet, "Service Calendar");

  const workbookArray = xlsx.write(workbook, {
    type: "array",
    bookType: "xlsx"
  });

  return new Blob([workbookArray], {
    type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
  });
};
