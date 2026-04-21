import type {ParsingOptions, WorkBook, WorkSheet} from "xlsx";

export type XlsxModule = typeof import("xlsx");

type LoadXlsxWorkbookOptions = {
  selectFileErrorMessage?: string;
  unsupportedFileErrorMessage?: string;
  readErrorMessage?: string;
  readOptions?: Omit<ParsingOptions, "type">;
};

const EXCEL_EPOCH_UTC = Date.UTC(1899, 11, 30);
const MILLISECONDS_PER_DAY = 24 * 60 * 60 * 1000;

const padNumber = (value: number): string => value.toString().padStart(2, "0");

const formatDateValueUtc = (date: Date): string => (
  `${date.getUTCFullYear()}-${padNumber(date.getUTCMonth() + 1)}-${padNumber(date.getUTCDate())}`
);

const createExcelSerialDate = (value: number): Date => (
  new Date(EXCEL_EPOCH_UTC + Math.round(value * MILLISECONDS_PER_DAY))
);

const parseDateString = (value: string): Date | null => {
  const isoMatch = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(value);
  if (isoMatch) {
    const [, yearText, monthText, dayText] = isoMatch;
    const year = Number(yearText);
    const month = Number(monthText);
    const day = Number(dayText);
    const parsed = new Date(Date.UTC(year, month - 1, day));
    return Number.isNaN(parsed.getTime())
      || parsed.getUTCFullYear() !== year
      || parsed.getUTCMonth() !== month - 1
      || parsed.getUTCDate() !== day
      ? null
      : parsed;
  }

  const usMatch = /^(\d{1,2})\/(\d{1,2})\/(\d{2}|\d{4})$/.exec(value);
  if (usMatch) {
    const [, monthText, dayText, yearText] = usMatch;
    const normalizedYear = yearText.length === 2 ? 2000 + Number(yearText) : Number(yearText);
    const month = Number(monthText);
    const day = Number(dayText);
    const parsed = new Date(Date.UTC(normalizedYear, month - 1, day));
    return Number.isNaN(parsed.getTime())
      || parsed.getUTCFullYear() !== normalizedYear
      || parsed.getUTCMonth() !== month - 1
      || parsed.getUTCDate() !== day
      ? null
      : parsed;
  }

  return null;
};

const isBlankCellValue = (value: unknown): boolean => (
  value === null || value === undefined || (typeof value === "string" && value.trim() === "")
);

export const loadXlsxModule = async (): Promise<XlsxModule> => import("xlsx");

export const loadXlsxWorkbookFromFile = async (
  file: File,
  options: LoadXlsxWorkbookOptions = {}
): Promise<{xlsx: XlsxModule; workbook: WorkBook}> => {
  const selectFileErrorMessage = options.selectFileErrorMessage ?? "Select an .xlsx spreadsheet to import.";
  const unsupportedFileErrorMessage = options.unsupportedFileErrorMessage ?? "Only .xlsx spreadsheets are supported.";
  const readErrorMessage = options.readErrorMessage ?? "Spreadsheet could not be read.";

  if (!(file instanceof File)) {
    throw new Error(selectFileErrorMessage);
  }
  if (!file.name.toLowerCase().endsWith(".xlsx")) {
    throw new Error(unsupportedFileErrorMessage);
  }

  const xlsx = await loadXlsxModule();
  try {
    const workbook = xlsx.read(await file.arrayBuffer(), {
      type: "array",
      ...(options.readOptions ?? {})
    });
    return {xlsx, workbook};
  } catch {
    throw new Error(readErrorMessage);
  }
};

export const getFirstWorksheetFromWorkbook = (
  workbook: WorkBook,
  missingWorksheetErrorMessage: string,
  unreadableWorksheetErrorMessage: string
): WorkSheet => {
  const firstSheetName = workbook.SheetNames[0];
  if (!firstSheetName) {
    throw new Error(missingWorksheetErrorMessage);
  }

  const sheet = workbook.Sheets[firstSheetName];
  if (!sheet) {
    throw new Error(unreadableWorksheetErrorMessage);
  }

  return sheet;
};

export const normalizeXlsxCellString = (value: unknown): string => (
  typeof value === "string" ? value.trim() : ""
);

export const parseXlsxSheetRows = <T extends object>(
  sheet: WorkSheet,
  xlsx: XlsxModule
): T[] => (
  xlsx.utils.sheet_to_json<T>(sheet, {
    defval: null,
    raw: true,
    blankrows: false
  })
);

export const validateXlsxHeaders = (
  sheet: WorkSheet,
  xlsx: XlsxModule,
  requiredHeaders: readonly string[]
): void => {
  const [headerRow = []] = xlsx.utils.sheet_to_json<unknown[]>(sheet, {
    header: 1,
    raw: false,
    blankrows: false,
    range: 0
  });

  if (headerRow.length === 0 || headerRow.every((value) => normalizeXlsxCellString(value) === "")) {
    throw new Error("Spreadsheet is missing the header row.");
  }

  const normalizedHeaders = new Set(headerRow.map((value) => normalizeXlsxCellString(value)));
  const missingHeaders = requiredHeaders.filter((header) => !normalizedHeaders.has(header));
  if (missingHeaders.length > 0) {
    throw new Error(`Spreadsheet is missing required columns: ${missingHeaders.join(", ")}.`);
  }
};

export const isEmptyXlsxRow = (
  row: Record<string, unknown>,
  headers: readonly string[]
): boolean => (
  headers.every((header) => isBlankCellValue(row[header]))
);

export const parseXlsxDateCell = (
  value: unknown,
  rowNumber: number,
  label: string
): string | null => {
  if (value === null || value === undefined || value === "") {
    return null;
  }
  if (value instanceof Date) {
    if (Number.isNaN(value.getTime())) {
      throw new Error(`Row ${rowNumber}: ${label} is invalid.`);
    }
    return formatDateValueUtc(value);
  }
  if (typeof value === "number") {
    if (value < 1) {
      throw new Error(`Row ${rowNumber}: ${label} is invalid.`);
    }
    return formatDateValueUtc(createExcelSerialDate(value));
  }
  if (typeof value === "string") {
    const normalized = value.trim();
    if (!normalized) {
      return null;
    }
    const parsed = parseDateString(normalized);
    if (parsed) {
      return formatDateValueUtc(parsed);
    }
  }

  throw new Error(`Row ${rowNumber}: ${label} is invalid.`);
};
