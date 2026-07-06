import type {LocationGraph} from "../../types/Types";
import type {TabulatorColumnDefinition} from "tabulator-tables";

export type TabulatorGraphRow = Record<string, unknown> & {
  rowIdentifier: string;
  caStatus: string;
  followUps: TabulatorGraphFollowUp[];
};

export type TabulatorGraphFollowUp = {
  date: string;
  value: string;
};

export type TabulatorGraphModel = {
  columns: TabulatorColumnDefinition[];
  rows: TabulatorGraphRow[];
};

const isRecord = (value: unknown): value is Record<string, unknown> =>
  value !== null && typeof value === "object" && !Array.isArray(value);

const slugField = (header: unknown, index: number): string => {
  const normalized = String(header ?? "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "");
  return normalized.length > 0 ? normalized : `column_${index + 1}`;
};

const toStringValue = (value: unknown): string =>
  value === null || value === undefined ? "" : String(value);

const listValue = (value: unknown): unknown[] =>
  Array.isArray(value) ? value : [];

const readFirstTableTrace = (graph: LocationGraph): Record<string, unknown> | null => {
  const trace = graph.data.find((entry) =>
    isRecord(entry) && String(entry.type ?? "").toLowerCase() === "table"
  );
  return isRecord(trace) ? trace : null;
};

export const isTabulatorGraph = (graph: LocationGraph): boolean => {
  const trace = readFirstTableTrace(graph);
  if (!trace) {
    return false;
  }
  const traceMeta = isRecord(trace.meta) ? trace.meta : {};
  const layoutMeta = isRecord(graph.layout?.meta) ? graph.layout.meta : {};
  const importMeta = isRecord(layoutMeta.aphinityImport) ? layoutMeta.aphinityImport : {};
  return traceMeta.renderer === "tabulator" || importMeta.renderer === "tabulator";
};

const parseFollowUps = (value: unknown): TabulatorGraphFollowUp[] => {
  if (!Array.isArray(value)) {
    return [];
  }
  return value
    .filter(isRecord)
    .map((entry) => ({
      date: toStringValue(entry.date),
      value: toStringValue(entry.value)
    }))
    .filter((entry) => entry.date.length > 0 || entry.value.length > 0);
};

export const createTabulatorGraphModel = (graph: LocationGraph): TabulatorGraphModel => {
  const trace = readFirstTableTrace(graph);
  if (!trace) {
    return {columns: [], rows: []};
  }

  const header = isRecord(trace.header) ? trace.header : {};
  const cells = isRecord(trace.cells) ? trace.cells : {};
  const headers = listValue(header.values);
  const columnValues = listValue(cells.values).map(listValue);
  const customData = listValue(trace.customdata);
  const fields = headers.map(slugField);
  const rowCount = columnValues.reduce((count, column) => Math.max(count, column.length), 0);

  const columns: TabulatorColumnDefinition[] = headers.map((headerValue, index) => ({
    title: toStringValue(headerValue),
    field: fields[index],
    minWidth: index < fields.length - 1 ? 140 : 110,
    widthGrow: index < fields.length - 1 ? 2 : 1,
    headerSort: true
  }));

  const rows: TabulatorGraphRow[] = Array.from({length: rowCount}, (_, rowIndex) => {
    const metadata = isRecord(customData[rowIndex]) ? customData[rowIndex] : {};
    const row: TabulatorGraphRow = {
      rowIdentifier: toStringValue(metadata.rowIdentifier) || `row-${rowIndex + 1}`,
      caStatus: toStringValue(metadata.caStatus),
      followUps: parseFollowUps(metadata.followUps)
    };
    fields.forEach((field, columnIndex) => {
      row[field] = toStringValue(columnValues[columnIndex]?.[rowIndex]);
    });
    return row;
  });

  return {columns, rows};
};
