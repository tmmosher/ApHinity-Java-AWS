import type * as Plotly from "plotly.js";
import type { EditableGraphPayload } from "./graphEditor";

export type TraceType = "pie" | "bar" | "scatter";

export const TRACE_EDITOR_BY_TYPE: Record<string, TraceType> = {
  pie: "pie",
  bar: "bar",
  scatter: "scatter",
  scattergl: "scatter"
};

export const TRACE_COLOR_OPTIONS: Record<string, string> = {
  "Ocean Blue": "#2563eb",
  "Forest Green": "#16a34a",
  "Sunset Orange": "#ea580c",
  "Crimson Red": "#dc2626",
  "Deep Violet": "#7c3aed",
  "Slate Gray": "#475569"
};

export const isRecord = (value: unknown): value is Record<string, unknown> =>
  value !== null && typeof value === "object" && !Array.isArray(value);

export const toInputValue = (value: unknown): string =>
  typeof value === "string" || typeof value === "number" || typeof value === "boolean"
    ? String(value)
    : "";

export const coerceInputValue = (
  rawValue: string,
  previousValue: unknown,
  preferNumeric: boolean
): unknown => {
  const normalized = rawValue.trim();
  const isIncompleteNumericInput =
    normalized === "-" ||
    normalized === "+" ||
    normalized === "." ||
    normalized === "-." ||
    normalized === "+." ||
    /^[+-]?\d+\.$/.test(normalized);

  if (typeof previousValue === "number") {
    if (normalized.length === 0) {
      return "";
    }
    if (isIncompleteNumericInput) {
      return rawValue;
    }
    const numeric = Number(rawValue);
    return Number.isFinite(numeric) ? numeric : rawValue;
  }
  if (typeof previousValue === "boolean") {
    return rawValue.toLowerCase() === "true";
  }
  if (preferNumeric) {
    if (normalized.length === 0) {
      return rawValue;
    }
    if (isIncompleteNumericInput) {
      return rawValue;
    }
    const numeric = Number(rawValue);
    if (Number.isFinite(numeric)) {
      return numeric;
    }
  }
  return rawValue;
};

export const getTraceArray = (trace: Record<string, unknown>, field: string): unknown[] => {
  const value = trace[field];
  return Array.isArray(value) ? [...value] : [];
};

export const getTraceType = (trace: Record<string, unknown>): TraceType | null => {
  const typeValue = typeof trace.type === "string" ? trace.type.toLowerCase() : "";
  return TRACE_EDITOR_BY_TYPE[typeValue] ?? null;
};

export const getTraceColor = (trace: Record<string, unknown>): string | null => {
  const marker = isRecord(trace.marker) ? trace.marker : null;
  if (marker) {
    if (typeof marker.color === "string") {
      return marker.color;
    }
    if (Array.isArray(marker.colors)) {
      const firstColor = marker.colors.find((entry) => typeof entry === "string");
      if (typeof firstColor === "string") {
        return firstColor;
      }
    }
  }

  const line = isRecord(trace.line) ? trace.line : null;
  if (line && typeof line.color === "string") {
    return line.color;
  }

  return null;
};

export const buildTraceLabel = (trace: Record<string, unknown>, index: number): string => {
  const traceName = typeof trace.name === "string" ? trace.name.trim() : "";
  const traceType = typeof trace.type === "string" ? trace.type : "trace";
  if (traceName.length > 0) {
    return `${index + 1}. ${traceName} (${traceType})`;
  }
  return `${index + 1}. ${traceType}`;
};

const syncPieMarkerColors = (
  trace: Record<string, unknown>,
  preferredColor?: string
): Record<string, unknown> => {
  const marker = isRecord(trace.marker) ? {...trace.marker} : {};
  const labelCount = getTraceArray(trace, "labels").length;
  const valueCount = getTraceArray(trace, "values").length;
  const colorCount = Math.max(labelCount, valueCount, 1);
  const existingColors = Array.isArray(marker.colors) ? [...marker.colors] : [];
  const fallbackColor =
    preferredColor ??
    (typeof marker.color === "string" ? marker.color : undefined) ??
    existingColors.find((entry) => typeof entry === "string") ??
    Object.values(TRACE_COLOR_OPTIONS)[0];

  marker.color = fallbackColor;
  marker.colors = Array.from({length: colorCount}, (_, index) => {
    const existing = existingColors[index];
    return typeof existing === "string" ? existing : fallbackColor;
  });

  return {
    ...trace,
    marker
  };
};

export const setTraceColor = (
  trace: Record<string, unknown>,
  traceType: TraceType,
  colorHex: string
): Record<string, unknown> => {
  if (traceType === "pie") {
    return syncPieMarkerColors(trace, colorHex);
  }

  const marker = isRecord(trace.marker) ? {...trace.marker} : {};
  marker.color = colorHex;
  let nextTrace: Record<string, unknown> = {
    ...trace,
    marker
  };

  if (traceType === "scatter") {
    const line = isRecord(trace.line) ? {...trace.line} : {};
    line.color = colorHex;
    nextTrace = {
      ...nextTrace,
      line
    };
  }

  return nextTrace;
};

export const addPieRow = (trace: Record<string, unknown>): Record<string, unknown> => {
  const labels = getTraceArray(trace, "labels");
  const values = getTraceArray(trace, "values");
  labels.push(`Slice ${labels.length + 1}`);
  values.push(0);
  return syncPieMarkerColors({
    ...trace,
    labels,
    values
  });
};

export const updatePieLabel = (
  trace: Record<string, unknown>,
  rowIndex: number,
  rawValue: string
): Record<string, unknown> => {
  const labels = getTraceArray(trace, "labels");
  labels[rowIndex] = rawValue;
  return syncPieMarkerColors({
    ...trace,
    labels
  });
};

export const updatePieValue = (
  trace: Record<string, unknown>,
  rowIndex: number,
  rawValue: string
): Record<string, unknown> => {
  const values = getTraceArray(trace, "values");
  values[rowIndex] = coerceInputValue(rawValue, values[rowIndex], true);
  return syncPieMarkerColors({
    ...trace,
    values
  });
};

export const removePieRow = (
  trace: Record<string, unknown>,
  rowIndex: number
): Record<string, unknown> => {
  const labels = getTraceArray(trace, "labels");
  const values = getTraceArray(trace, "values");
  if (rowIndex >= 0 && rowIndex < labels.length) {
    labels.splice(rowIndex, 1);
  }
  if (rowIndex >= 0 && rowIndex < values.length) {
    values.splice(rowIndex, 1);
  }
  return syncPieMarkerColors({
    ...trace,
    labels,
    values
  });
};

export const addCartesianRow = (trace: Record<string, unknown>): Record<string, unknown> => {
  const xValues = getTraceArray(trace, "x");
  const yValues = getTraceArray(trace, "y");
  const firstXValue = xValues.find((value) => value !== undefined && value !== null);
  const nextX = typeof firstXValue === "number" ? xValues.length + 1 : "";
  const firstYValue = yValues.find((value) => value !== undefined && value !== null);
  const nextY = typeof firstYValue === "number" ? 0 : "";
  xValues.push(nextX);
  yValues.push(nextY);
  return {
    ...trace,
    x: xValues,
    y: yValues
  };
};

export const updateCartesianX = (
  trace: Record<string, unknown>,
  rowIndex: number,
  rawValue: string
): Record<string, unknown> => {
  const xValues = getTraceArray(trace, "x");
  xValues[rowIndex] = coerceInputValue(rawValue, xValues[rowIndex], false);
  return {
    ...trace,
    x: xValues
  };
};

export const updateCartesianY = (
  trace: Record<string, unknown>,
  rowIndex: number,
  rawValue: string
): Record<string, unknown> => {
  const yValues = getTraceArray(trace, "y");
  yValues[rowIndex] = coerceInputValue(rawValue, yValues[rowIndex], true);
  return {
    ...trace,
    y: yValues
  };
};

export const removeCartesianRow = (
  trace: Record<string, unknown>,
  rowIndex: number
): Record<string, unknown> => {
  const xValues = getTraceArray(trace, "x");
  const yValues = getTraceArray(trace, "y");
  if (rowIndex >= 0 && rowIndex < xValues.length) {
    xValues.splice(rowIndex, 1);
  }
  if (rowIndex >= 0 && rowIndex < yValues.length) {
    yValues.splice(rowIndex, 1);
  }
  return {
    ...trace,
    x: xValues,
    y: yValues
  };
};

const createHiddenPlotlyDiv = (): HTMLDivElement => {
  const graphDiv = document.createElement("div");
  graphDiv.style.position = "fixed";
  graphDiv.style.left = "-10000px";
  graphDiv.style.top = "-10000px";
  graphDiv.style.width = "1px";
  graphDiv.style.height = "1px";
  graphDiv.style.pointerEvents = "none";
  document.body.appendChild(graphDiv);
  return graphDiv;
};

const cloneForPlotly = <T>(value: T): T => {
  if (typeof structuredClone === "function") {
    try {
      return structuredClone(value);
    } catch {
      // fall through to JSON clone fallback for plain graph payloads.
    }
  }

  try {
    return JSON.parse(JSON.stringify(value)) as T;
  } catch {
    return value;
  }
};

export const removeTraceWithPlotly = async (
  plotly: Pick<typeof Plotly, "newPlot" | "deleteTraces" | "purge">,
  payload: EditableGraphPayload,
  traceIndex: number
): Promise<Record<string, unknown>[]> => {
  if (!Number.isInteger(traceIndex) || traceIndex < 0 || traceIndex >= payload.data.length) {
    throw new Error("Trace index out of range.");
  }

  const graphDiv = createHiddenPlotlyDiv();

  try {
    const plotlyInputData = payload.data.map((trace) => cloneForPlotly(trace) as Plotly.Data);
    const plotlyElement = graphDiv as unknown as Plotly.PlotlyHTMLElement;
    const plotlyLayout = cloneForPlotly(payload.layout ?? undefined) as unknown as Plotly.Layout | undefined;
    const plotlyConfig = cloneForPlotly(payload.config ?? undefined) as unknown as Plotly.Config | undefined;

    await plotly.newPlot(
      plotlyElement,
      plotlyInputData,
      plotlyLayout,
      plotlyConfig
    );

    await plotly.deleteTraces(plotlyElement, [traceIndex]);

    return payload.data
      .filter((_, index) => index !== traceIndex);
  } finally {
    try {
      plotly.purge(graphDiv as unknown as Plotly.PlotlyHTMLElement);
    } catch {
      // no-op: cleanup guard
    }
    graphDiv.remove();
  }
};
