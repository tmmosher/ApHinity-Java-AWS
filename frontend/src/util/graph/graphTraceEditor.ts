import type * as Plotly from "plotly.js";
import {
  createTraceTemplate,
  parseIndicatorValueInput,
  isIncompleteNumericInput,
  syncPieMarkerColors,
  INDICATOR_THRESHOLD_COLOR,
  TRACE_COLOR_OPTIONS,
  type TraceType
} from "./graphTemplateFactory";
import type { EditableGraphPayload } from "./graphEditor";

export type {TraceType};
export const AUTO_SIZE_TRACE_FLAG = "autoSize";
export type BarOrientation = "h" | "v";
export type CartesianAxisValueMode = "auto" | "numeric" | "categorical";

export const TRACE_EDITOR_BY_TYPE: Record<string, TraceType> = {
  pie: "pie",
  bar: "bar",
  scatter: "scatter",
  indicator: "indicator",
  scattergl: "scatter"
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

  if (typeof previousValue === "number") {
    if (normalized.length === 0) {
      return "";
    }
    if (isIncompleteNumericInput(rawValue)) {
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
    if (isIncompleteNumericInput(rawValue)) {
      return rawValue;
    }
    const numeric = Number(rawValue);
    if (Number.isFinite(numeric)) {
      return numeric;
    }
  }
  return rawValue;
};

export const parseNumericInput = (rawValue: string): number | null => {
  const parsedValue = coerceInputValue(rawValue, 0, true);
  return typeof parsedValue === "number" ? parsedValue : null;
};

export const getTraceArray = (trace: Record<string, unknown>, field: string): unknown[] => {
  const value = trace[field];
  return Array.isArray(value) ? [...value] : [];
};

export const getTraceType = (trace: Record<string, unknown>): TraceType | null => {
  const typeValue = typeof trace.type === "string" ? trace.type.toLowerCase() : "";
  return TRACE_EDITOR_BY_TYPE[typeValue] ?? null;
};

const isNumericList = (values: unknown[]): boolean =>
  values.every((value) => typeof value === "number" && Number.isFinite(value));

const isScalarList = (values: unknown[]): boolean =>
  values.every((value) =>
    value === null ||
    typeof value === "string" ||
    typeof value === "number" ||
    typeof value === "boolean"
  );

export const getBarOrientation = (
  trace: Record<string, unknown>
): BarOrientation => {
  const rawOrientation = typeof trace.orientation === "string"
    ? trace.orientation.trim().toLowerCase()
    : "";
  if (rawOrientation === "h" || rawOrientation === "v") {
    return rawOrientation;
  }

  const xValues = getTraceArray(trace, "x");
  const yValues = getTraceArray(trace, "y");
  if (xValues.length > 0 && yValues.length > 0) {
    if (isNumericList(xValues) && isScalarList(yValues)) {
      return "h";
    }
    if (isScalarList(xValues) && isNumericList(yValues)) {
      return "v";
    }
  }

  return "h";
};

export const getCartesianAxisValueMode = (
  trace: Record<string, unknown>,
  axis: "x" | "y"
): CartesianAxisValueMode => {
  if (getTraceType(trace) !== "bar") {
    return axis === "y" ? "numeric" : "auto";
  }

  const orientation = getBarOrientation(trace);
  if (orientation === "h") {
    return axis === "x" ? "numeric" : "categorical";
  }
  return axis === "x" ? "categorical" : "numeric";
};

export const isAutoSizingPieTrace = (trace: Record<string, unknown>): boolean =>
  getTraceType(trace) === "pie" && trace[AUTO_SIZE_TRACE_FLAG] === true;

export const getTraceColor = (trace: Record<string, unknown>): string | null => {
  if (getTraceType(trace) === "indicator") {
    const gauge = isRecord(trace.gauge) ? trace.gauge : null;
    if (gauge && isRecord(gauge.bar) && typeof gauge.bar.color === "string") {
      return gauge.bar.color;
    }
  }

  const marker = isRecord(trace.marker) ? trace.marker : null;
  if (marker) {
    if (typeof marker.color === "string") {
      return marker.color;
    }
    if (Array.isArray(marker.color)) {
      const firstColor = marker.color.find((entry) => typeof entry === "string");
      if (typeof firstColor === "string") {
        return firstColor;
      }
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

const getDefaultTraceColor = (): string =>
  Object.values(TRACE_COLOR_OPTIONS)[0];

export const getPieRowColor = (
  trace: Record<string, unknown>,
  rowIndex: number
): string => {
  const marker = isRecord(trace.marker) ? trace.marker : null;
  if (marker && Array.isArray(marker.colors)) {
    const rowColor = marker.colors[rowIndex];
    if (typeof rowColor === "string") {
      return rowColor;
    }
    const firstColor = marker.colors.find((entry) => typeof entry === "string");
    if (typeof firstColor === "string") {
      return firstColor;
    }
  }

  if (marker && typeof marker.color === "string") {
    return marker.color;
  }

  return getDefaultTraceColor();
};

export const getBarRowColor = (
  trace: Record<string, unknown>,
  rowIndex: number
): string => {
  const marker = isRecord(trace.marker) ? trace.marker : null;
  if (marker && Array.isArray(marker.color)) {
    const rowColor = marker.color[rowIndex];
    if (typeof rowColor === "string") {
      return rowColor;
    }
    const firstColor = marker.color.find((entry) => typeof entry === "string");
    if (typeof firstColor === "string") {
      return firstColor;
    }
  }
  if (marker && Array.isArray(marker.colors)) {
    const rowColor = marker.colors[rowIndex];
    if (typeof rowColor === "string") {
      return rowColor;
    }
    const firstColor = marker.colors.find((entry) => typeof entry === "string");
    if (typeof firstColor === "string") {
      return firstColor;
    }
  }

  if (marker && typeof marker.color === "string") {
    return marker.color;
  }

  return getDefaultTraceColor();
};

export const buildTraceLabel = (trace: Record<string, unknown>, index: number): string => {
  const traceName = typeof trace.name === "string" ? trace.name.trim() : "";
  const traceType = typeof trace.type === "string" ? trace.type : "trace";
  if (traceName.length > 0) {
    return `${index + 1}. ${traceName} (${traceType})`;
  }
  return `${index + 1}. ${traceType}`;
};

const buildDefaultTraceName = (traceIndex: number): string => `Trace ${traceIndex + 1}`;

export const createTrace = (
  preferredType: TraceType | null,
  existingTraceCount: number
): Record<string, unknown> => {
  const traceName = buildDefaultTraceName(existingTraceCount);
  return createTraceTemplate(preferredType, traceName);
};

export const renameTrace = (
  trace: Record<string, unknown>,
  rawName: string
): Record<string, unknown> => {
  const nextName = rawName.trim();
  if (nextName.length === 0) {
    const {name: _, ...traceWithoutName} = trace;
    return traceWithoutName;
  }

  return {
    ...trace,
    name: nextName
  };
};

const getTraceAxisLayoutKey = (
  trace: Record<string, unknown>,
  axisPrefix: "x" | "y"
): string => {
  const axisReferenceKey = axisPrefix === "x" ? "xaxis" : "yaxis";
  const axisLayoutKey = axisPrefix === "x" ? "xaxis" : "yaxis";
  const axisReference = typeof trace[axisReferenceKey] === "string"
    ? String(trace[axisReferenceKey]).trim().toLowerCase()
    : axisPrefix;
  const match = new RegExp(`^${axisPrefix}(\\d+)?$`).exec(axisReference);
  if (!match) {
    return axisLayoutKey;
  }
  const suffix = match[1];
  if (!suffix || suffix === "1") {
    return axisLayoutKey;
  }
  return `${axisLayoutKey}${suffix}`;
};

const getTraceValueAxisLayoutKey = (trace: Record<string, unknown>): string =>
  getTraceType(trace) === "bar" && getBarOrientation(trace) === "h"
    ? getTraceAxisLayoutKey(trace, "x")
    : getTraceAxisLayoutKey(trace, "y");

const swapTraceAxisReference = (
  value: unknown,
  axisPrefix: "x" | "y"
): string | undefined => {
  if (typeof value !== "string") {
    return undefined;
  }
  const normalized = value.trim().toLowerCase();
  const match = /^[xy](\d+)?$/.exec(normalized);
  if (!match) {
    return normalized;
  }
  const suffix = match[1] ?? "";
  return `${axisPrefix}${suffix}`;
};

const hasRangeBoundValue = (value: unknown): boolean =>
  value !== null &&
  value !== undefined &&
  !(typeof value === "string" && value.length === 0);

export const getTraceYAxisRange = (
  layout: Record<string, unknown> | null | undefined,
  trace: Record<string, unknown>
): [unknown, unknown] => {
  if (!isRecord(layout)) {
    return ["", ""];
  }

  const axis = layout[getTraceValueAxisLayoutKey(trace)];
  if (!isRecord(axis) || !Array.isArray(axis.range)) {
    return ["", ""];
  }

  return [axis.range[0] ?? "", axis.range[1] ?? ""];
};

export const getTraceYAxisTitle = (
  layout: Record<string, unknown> | null | undefined,
  trace: Record<string, unknown>
): string => {
  if (!isRecord(layout)) {
    return "";
  }

  const axis = layout[getTraceValueAxisLayoutKey(trace)];
  if (!isRecord(axis)) {
    return "";
  }

  const title = axis.title;
  if (typeof title === "string") {
    return title;
  }
  if (isRecord(title) && typeof title.text === "string") {
    return title.text;
  }
  return "";
};

export const updateTraceYAxisRange = (
  layout: Record<string, unknown> | null | undefined,
  trace: Record<string, unknown>,
  boundIndex: 0 | 1,
  rawValue: string
): Record<string, unknown> | null => {
  const axisKey = getTraceValueAxisLayoutKey(trace);
  const nextLayout = isRecord(layout) ? {...layout} : {};
  const nextAxis = isRecord(nextLayout[axisKey]) ? {...nextLayout[axisKey]} : {};
  const existingRange = Array.isArray(nextAxis.range)
    ? [nextAxis.range[0], nextAxis.range[1]]
    : [null, null];
  const previousBound = existingRange[boundIndex];

  if (rawValue.length === 0) {
    existingRange[boundIndex] = null;
  } else {
    existingRange[boundIndex] = coerceInputValue(rawValue, previousBound, true);
  }

  const hasAnyRange = hasRangeBoundValue(existingRange[0]) || hasRangeBoundValue(existingRange[1]);
  if (hasAnyRange) {
    nextAxis.range = existingRange;
    nextLayout[axisKey] = nextAxis;
    return nextLayout;
  }

  if (isRecord(nextLayout[axisKey])) {
    delete nextAxis.range;
    if (Object.keys(nextAxis).length === 0) {
      delete nextLayout[axisKey];
    } else {
      nextLayout[axisKey] = nextAxis;
    }
  }

  return Object.keys(nextLayout).length > 0 ? nextLayout : null;
};

export const updateTraceYAxisTitle = (
  layout: Record<string, unknown> | null | undefined,
  trace: Record<string, unknown>,
  rawTitle: string
): Record<string, unknown> | null => {
  const axisKey = getTraceValueAxisLayoutKey(trace);
  const nextLayout = isRecord(layout) ? {...layout} : {};
  const nextAxis = isRecord(nextLayout[axisKey]) ? {...nextLayout[axisKey]} : {};
  const normalizedTitle = rawTitle.trim();

  if (normalizedTitle.length > 0) {
    nextAxis.title = isRecord(nextAxis.title)
      ? {
          ...nextAxis.title,
          text: normalizedTitle
        }
      : normalizedTitle;
    nextLayout[axisKey] = nextAxis;
    return nextLayout;
  }

  if ("title" in nextAxis) {
    delete nextAxis.title;
  }
  if (Object.keys(nextAxis).length === 0) {
    delete nextLayout[axisKey];
  } else {
    nextLayout[axisKey] = nextAxis;
  }

  return Object.keys(nextLayout).length > 0 ? nextLayout : null;
};

export const setBarOrientation = (
  trace: Record<string, unknown>,
  nextOrientation: BarOrientation
): Record<string, unknown> => {
  if (getTraceType(trace) !== "bar") {
    return trace;
  }

  const currentOrientation = getBarOrientation(trace);
  const xValues = getTraceArray(trace, "x");
  const yValues = getTraceArray(trace, "y");
  const nextXAxis = swapTraceAxisReference(trace.yaxis, "x");
  const nextYAxis = swapTraceAxisReference(trace.xaxis, "y");

  if (currentOrientation === nextOrientation) {
    return {
      ...trace,
      orientation: nextOrientation
    };
  }

  const nextTrace: Record<string, unknown> = {
    ...trace,
    orientation: nextOrientation,
    x: yValues,
    y: xValues
  };
  if (nextXAxis !== undefined) {
    nextTrace.xaxis = nextXAxis;
  } else {
    delete nextTrace.xaxis;
  }
  if (nextYAxis !== undefined) {
    nextTrace.yaxis = nextYAxis;
  } else {
    delete nextTrace.yaxis;
  }
  return nextTrace;
};

export const swapCartesianLayoutAxes = (
  layout: Record<string, unknown> | null | undefined
): Record<string, unknown> | null => {
  if (!isRecord(layout)) {
    return null;
  }

  const nextLayout = {...layout};
  const suffixes = new Set<string>();
  for (const key of Object.keys(nextLayout)) {
    const match = /^(xaxis|yaxis)(\d*)$/.exec(key);
    if (match) {
      suffixes.add(match[2] || "");
    }
  }

  if (suffixes.size === 0) {
    return nextLayout;
  }

  for (const suffix of suffixes) {
    const xKey = `xaxis${suffix}`;
    const yKey = `yaxis${suffix}`;
    const xAxis = nextLayout[xKey];
    const yAxis = nextLayout[yKey];

    if (yAxis === undefined) {
      delete nextLayout[xKey];
    } else {
      nextLayout[xKey] = yAxis;
    }

    if (xAxis === undefined) {
      delete nextLayout[yKey];
    } else {
      nextLayout[yKey] = xAxis;
    }
  }

  return nextLayout;
};

export const setPieRowColor = (
  trace: Record<string, unknown>,
  rowIndex: number,
  colorHex: string
): Record<string, unknown> => {
  if (!Number.isInteger(rowIndex) || rowIndex < 0) {
    return trace;
  }

  const syncedTrace = syncPieMarkerColors(trace);
  const marker = isRecord(syncedTrace.marker) ? {...syncedTrace.marker} : {};
  const colors = Array.isArray(marker.colors)
    ? marker.colors.map((entry, index) =>
        typeof entry === "string" ? entry : getPieRowColor(syncedTrace, index)
      )
    : [];

  if (rowIndex >= colors.length) {
    return syncedTrace;
  }

  colors[rowIndex] = colorHex;
  marker.color = colors[0] ?? colorHex;
  marker.colors = colors;

  return {
    ...syncedTrace,
    marker
  };
};

export const setBarRowColor = (
  trace: Record<string, unknown>,
  rowIndex: number,
  colorHex: string
): Record<string, unknown> => {
  if (getTraceType(trace) !== "bar" || !Number.isInteger(rowIndex) || rowIndex < 0) {
    return trace;
  }

  const rowCount = Math.max(getTraceArray(trace, "x").length, getTraceArray(trace, "y").length);
  if (rowIndex >= rowCount) {
    return trace;
  }

  const marker = isRecord(trace.marker) ? {...trace.marker} : {};
  const colors = Array.from({length: rowCount}, (_, index) => getBarRowColor(trace, index));
  colors[rowIndex] = colorHex;
  marker.color = colors;
  marker.colors = colors;

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

  if (traceType === "bar") {
    const marker = isRecord(trace.marker) ? {...trace.marker} : {};
    const rowCount = Math.max(getTraceArray(trace, "x").length, getTraceArray(trace, "y").length);
    if (rowCount > 0) {
      const colors = Array.from({length: rowCount}, () => colorHex);
      marker.color = colors;
      marker.colors = colors;
    } else {
      marker.color = colorHex;
    }
    return {
      ...trace,
      marker
    };
  }

  if (traceType === "indicator") {
    const gauge = isRecord(trace.gauge) ? {...trace.gauge} : {};
    const bar = isRecord(gauge.bar) ? {...gauge.bar} : {};
    bar.color = colorHex;
    gauge.bar = bar;

    return {
      ...trace,
      gauge
    };
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

export const updateIndicatorValue = (
  trace: Record<string, unknown>,
  rawValue: string
): Record<string, unknown> => {
  const parsedValue = parseIndicatorValueInput(rawValue);
  if (parsedValue === null) {
    return trace;
  }

  const gauge = isRecord(trace.gauge) ? {...trace.gauge} : {};
  const threshold = isRecord(gauge.threshold) ? {...gauge.threshold} : {};
  const line = isRecord(threshold.line) ? {...threshold.line} : {};
  line.color = typeof line.color === "string" ? line.color : INDICATOR_THRESHOLD_COLOR;
  line.width = typeof line.width === "number" && Number.isFinite(line.width) ? line.width : 2;
  threshold.line = line;
  threshold.thickness =
    typeof threshold.thickness === "number" && Number.isFinite(threshold.thickness) ? threshold.thickness : 0.75;
  threshold.value = parsedValue;
  gauge.threshold = threshold;

  return {
    ...trace,
    value: parsedValue,
    gauge
  };
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
  const nextTrace = syncPieMarkerColors({
    ...trace,
    values
  });

  return applyPieAutoSizingPlaceholder(nextTrace, rowIndex);
};

const applyPieAutoSizingPlaceholder = (
  trace: Record<string, unknown>,
  rowIndex: number
): Record<string, unknown> => {
  if (!isAutoSizingPieTrace(trace) || rowIndex !== 0) {
    return trace;
  }

  // Planned behavior:
  // - Treat values[0] as the numerator/fill.
  // - Derive values[1] as max(0, 100 - fill) when an autosize target of 100 is used.
  // - Keep additional slices untouched (or reject multi-slice autosize traces).
  return trace;
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
  const previousRowCount = Math.max(xValues.length, yValues.length);
  const firstXValue = xValues.find((value) => value !== undefined && value !== null);
  const nextX = typeof firstXValue === "number" ? xValues.length + 1 : "";
  const firstYValue = yValues.find((value) => value !== undefined && value !== null);
  const nextY = typeof firstYValue === "number" ? 0 : "";
  xValues.push(nextX);
  yValues.push(nextY);
  const nextTrace: Record<string, unknown> = {
    ...trace,
    x: xValues,
    y: yValues
  };
  if (getTraceType(trace) !== "bar") {
    return nextTrace;
  }

  const marker = isRecord(trace.marker) ? {...trace.marker} : {};
  const inheritedColor = previousRowCount > 0
    ? getBarRowColor(trace, 0)
    : getDefaultTraceColor();
  const colors = Array.from({length: Math.max(xValues.length, yValues.length)}, (_, index) =>
    index < previousRowCount ? getBarRowColor(trace, index) : inheritedColor
  );
  marker.color = colors;
  marker.colors = colors;
  return {
    ...nextTrace,
    marker
  };
};

export const updateCartesianX = (
  trace: Record<string, unknown>,
  rowIndex: number,
  rawValue: string,
  mode: CartesianAxisValueMode = "auto"
): Record<string, unknown> => {
  const xValues = getTraceArray(trace, "x");
  xValues[rowIndex] = coerceCartesianAxisValue(rawValue, xValues[rowIndex], mode);
  return {
    ...trace,
    x: xValues
  };
};

export const updateCartesianY = (
  trace: Record<string, unknown>,
  rowIndex: number,
  rawValue: string,
  mode: CartesianAxisValueMode = "numeric"
): Record<string, unknown> => {
  const yValues = getTraceArray(trace, "y");
  yValues[rowIndex] = coerceCartesianAxisValue(rawValue, yValues[rowIndex], mode);
  return {
    ...trace,
    y: yValues
  };
};

const coerceCartesianAxisValue = (
  rawValue: string,
  previousValue: unknown,
  mode: CartesianAxisValueMode
): unknown => {
  if (mode === "categorical") {
    return rawValue;
  }
  if (mode === "numeric") {
    return coerceInputValue(rawValue, 0, true);
  }
  return coerceInputValue(rawValue, previousValue, false);
};

export const removeCartesianRow = (
  trace: Record<string, unknown>,
  rowIndex: number
): Record<string, unknown> => {
  const previousRowCount = Math.max(getTraceArray(trace, "x").length, getTraceArray(trace, "y").length);
  const xValues = getTraceArray(trace, "x");
  const yValues = getTraceArray(trace, "y");
  if (rowIndex >= 0 && rowIndex < xValues.length) {
    xValues.splice(rowIndex, 1);
  }
  if (rowIndex >= 0 && rowIndex < yValues.length) {
    yValues.splice(rowIndex, 1);
  }
  const nextTrace: Record<string, unknown> = {
    ...trace,
    x: xValues,
    y: yValues
  };
  if (getTraceType(trace) !== "bar") {
    return nextTrace;
  }

  const marker = isRecord(trace.marker) ? {...trace.marker} : {};
  const originalColors = Array.from({length: previousRowCount}, (_, index) =>
    getBarRowColor(trace, index)
  );
  if (rowIndex >= 0 && rowIndex < originalColors.length) {
    originalColors.splice(rowIndex, 1);
  }
  if (originalColors.length > 0) {
    marker.color = originalColors;
    marker.colors = originalColors;
  } else {
    delete marker.colors;
    marker.color = getDefaultTraceColor();
  }
  return {
    ...nextTrace,
    marker
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
