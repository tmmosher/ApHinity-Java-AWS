import type {CreateLocationGanttTaskRequest} from "../../types/Types";
import {
  createLocationGanttTaskRequestFromDraft,
  GANTT_TASK_TITLE_MAX_LENGTH,
  GANTT_TASK_TITLE_MIN_LENGTH
} from "./ganttTaskForm";
import {
  getFirstWorksheetFromWorkbook,
  isEmptyXlsxRow as isEmptySpreadsheetRow,
  loadXlsxWorkbookFromFile,
  normalizeXlsxCellString as normalizeCellString,
  parseXlsxDateCell as parseDateCell,
  parseXlsxSheetRows as parseSheetRows,
  validateXlsxHeaders as validateHeaders
} from "./xlsxSpreadsheet";

const REQUIRED_HEADERS = ["Title", "Description", "Start Date", "End Date"] as const;
type RequiredHeader = (typeof REQUIRED_HEADERS)[number];

type SpreadsheetRowRecord = Partial<Record<RequiredHeader, unknown>>;

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

const parseSpreadsheetRow = (row: SpreadsheetRowRecord, rowNumber: number): CreateLocationGanttTaskRequest => {
  const title = normalizeCellString(row.Title);
  if (!title) {
    throw new Error(`Row ${rowNumber}: Title is required.`);
  }
  if (title.length < GANTT_TASK_TITLE_MIN_LENGTH || title.length > GANTT_TASK_TITLE_MAX_LENGTH) {
    throw new Error(`Row ${rowNumber}: Title must be between 3 and 60 characters.`);
  }

  const description = normalizeCellString(row.Description);
  const startDate = parseDateCell(row["Start Date"], rowNumber, "Start Date");
  const endDate = parseDateCell(row["End Date"], rowNumber, "End Date");
  if (!startDate) {
    throw new Error(`Row ${rowNumber}: Start Date is required.`);
  }
  if (!endDate) {
    throw new Error(`Row ${rowNumber}: End Date is required.`);
  }

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

export const parseGanttTaskSpreadsheetFile = async (
  file: File
): Promise<CreateLocationGanttTaskRequest[]> => {
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

  validateHeaders(sheet, xlsx, REQUIRED_HEADERS);

  const requests = parseSheetRows<SpreadsheetRowRecord>(sheet, xlsx)
    .map((row, index) => ({row, rowNumber: index + 2}))
    .filter(({row}) => !isEmptySpreadsheetRow(row, REQUIRED_HEADERS))
    .map(({row, rowNumber}) => parseSpreadsheetRow(row, rowNumber));

  if (requests.length === 0) {
    throw new Error("Spreadsheet does not contain any gantt tasks.");
  }

  return requests;
};
