import type {CreateLocationGanttTaskRequest} from "../../types/Types";
import {
  createLocationGanttTaskRequestFromDraft,
  GANTT_TASK_TITLE_MAX_LENGTH,
  GANTT_TASK_TITLE_MIN_LENGTH
} from "./ganttTaskForm";
import {formatDateInputValue, parseDateValue} from "./dateUtility";

const REQUIRED_HEADERS = ["Title", "Description", "Start Date", "End Date"] as const;
type RequiredHeader = (typeof REQUIRED_HEADERS)[number];

type CsvRowRecord = Partial<Record<RequiredHeader, string>>;

const datePattern = /^\d{4}-\d{2}-\d{2}$/;

const parseCsvLine = (line: string): string[] => {
  const values: string[] = [];
  let current = "";
  let inQuotes = false;

  for (let index = 0; index < line.length; index += 1) {
    const character = line[index];
    if (character === "\"") {
      const nextCharacter = line[index + 1];
      if (inQuotes && nextCharacter === "\"") {
        current += "\"";
        index += 1;
        continue;
      }
      inQuotes = !inQuotes;
      continue;
    }
    if (character === "," && !inQuotes) {
      values.push(current);
      current = "";
      continue;
    }
    current += character;
  }

  if (inQuotes) {
    throw new Error("CSV row contains unmatched quotes.");
  }

  values.push(current);
  return values;
};

const normalizeCsvCell = (value: string | undefined): string => (value ?? "").trim();

const parseHeaderRow = (line: string): string[] => (
  parseCsvLine(line).map((value) => value.trim())
);

const validateHeaders = (headers: string[]): void => {
  const headerSet = new Set(headers);
  const missingHeaders = REQUIRED_HEADERS.filter((header) => !headerSet.has(header));
  if (missingHeaders.length > 0) {
    throw new Error(`CSV is missing required columns: ${missingHeaders.join(", ")}.`);
  }
};

const mapCsvRow = (headers: string[], values: string[]): CsvRowRecord => {
  const row: CsvRowRecord = {};
  headers.forEach((header, index) => {
    if (REQUIRED_HEADERS.includes(header as RequiredHeader)) {
      row[header as RequiredHeader] = values[index] ?? "";
    }
  });
  return row;
};

const parseDateCell = (value: string, rowNumber: number, label: "Start Date" | "End Date"): string => {
  const normalized = value.trim();
  if (!normalized) {
    throw new Error(`Row ${rowNumber}: ${label} is required.`);
  }
  if (!datePattern.test(normalized)) {
    throw new Error(`Row ${rowNumber}: ${label} must use YYYY-MM-DD format.`);
  }

  try {
    const parsed = parseDateValue(normalized, `Row ${rowNumber}: ${label} is invalid.`);
    if (formatDateInputValue(parsed) !== normalized) {
      throw new Error("invalid");
    }
  } catch {
    throw new Error(`Row ${rowNumber}: ${label} is invalid.`);
  }

  return normalized;
};

const mapDraftValidationError = (message: string): string => {
  if (message === "Task title is required.") {
    return "Title is required.";
  }
  if (message === "Task title must be between 3 and 60 characters.") {
    return "Title must be between 3 and 60 characters.";
  }
  if (message === "Task end date must be on or after the start date.") {
    return "End Date must be on or after Start Date.";
  }
  return message;
};

const parseRowRequest = (row: CsvRowRecord, rowNumber: number): CreateLocationGanttTaskRequest => {
  const title = normalizeCsvCell(row.Title);
  if (!title) {
    throw new Error(`Row ${rowNumber}: Title is required.`);
  }
  if (title.length < GANTT_TASK_TITLE_MIN_LENGTH || title.length > GANTT_TASK_TITLE_MAX_LENGTH) {
    throw new Error(`Row ${rowNumber}: Title must be between 3 and 60 characters.`);
  }

  const startDate = parseDateCell(normalizeCsvCell(row["Start Date"]), rowNumber, "Start Date");
  const endDate = parseDateCell(normalizeCsvCell(row["End Date"]), rowNumber, "End Date");
  const description = normalizeCsvCell(row.Description);
  try {
    return createLocationGanttTaskRequestFromDraft({
      title,
      startDate,
      endDate,
      description
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Invalid gantt task";
    throw new Error(`Row ${rowNumber}: ${mapDraftValidationError(message)}`);
  }
};

const isEmptyRow = (row: CsvRowRecord): boolean => (
  REQUIRED_HEADERS.every((header) => normalizeCsvCell(row[header]) === "")
);

export const parseGanttTaskCsvFile = async (file: File): Promise<CreateLocationGanttTaskRequest[]> => {
  if (!(file instanceof File)) {
    throw new Error("Select a .csv file to import.");
  }
  if (!file.name.toLowerCase().endsWith(".csv")) {
    throw new Error("Only .csv files are supported for gantt imports.");
  }

  const text = await file.text();
  const lines = text
    .split(/\r?\n/)
    .map((line) => line.trimEnd())
    .filter((line) => line.trim() !== "");

  if (lines.length === 0) {
    throw new Error("CSV file is empty.");
  }

  const headers = parseHeaderRow(lines[0]);
  validateHeaders(headers);

  const requests = lines
    .slice(1)
    .map((line, index) => ({row: mapCsvRow(headers, parseCsvLine(line)), rowNumber: index + 2}))
    .filter(({row}) => !isEmptyRow(row))
    .map(({row, rowNumber}) => parseRowRequest(row, rowNumber));

  if (requests.length === 0) {
    throw new Error("CSV file does not contain any gantt tasks.");
  }

  return requests;
};
