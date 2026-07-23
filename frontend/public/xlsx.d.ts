export type BookType = "xlsx" | "xlsm" | string;

export interface ParsingOptions {
  type?: "array" | "binary" | "buffer" | "base64" | "file" | "string";
  cellDates?: boolean;
  bookVBA?: boolean;
  [key: string]: unknown;
}

export interface WritingOptions {
  type?: "array" | "binary" | "buffer" | "base64" | "file" | "string";
  bookType?: BookType;
  [key: string]: unknown;
}

export interface Sheet2JSONOpts {
  header?: 1 | "A" | string[];
  raw?: boolean;
  range?: number | string;
  defval?: unknown;
  blankrows?: boolean;
  [key: string]: unknown;
}

export interface WorkSheet {
  "!ref"?: string;
  [cell: string]: unknown;
}

export interface WorkBook {
  SheetNames: string[];
  Sheets: Record<string, WorkSheet | undefined>;
  [key: string]: unknown;
}

export const version: string;

export function read(data: ArrayBuffer | Uint8Array | string, options?: ParsingOptions): WorkBook;
export function write(workbook: WorkBook, options: WritingOptions & {type: "array"}): ArrayBuffer;
export function write(workbook: WorkBook, options?: WritingOptions): unknown;

export const utils: {
  book_new(): WorkBook;
  book_append_sheet(workbook: WorkBook, worksheet: WorkSheet, name?: string): string;
  aoa_to_sheet(data: unknown[][]): WorkSheet;
  sheet_to_json<T>(worksheet: WorkSheet, options?: Sheet2JSONOpts): T[];
};
