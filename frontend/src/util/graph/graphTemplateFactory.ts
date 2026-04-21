import {z} from "zod";

export type TraceType = "pie" | "bar" | "scatter" | "indicator";

export const TRACE_COLOR_OPTIONS: Record<string, string> = {
  "Legacy Blue": "#1f77b4",
  "Legacy Green": "#2ca02c",
  "Legacy Red": "#d62728",
  "Legacy Orange": "#ff7f0e",
  "Legacy Purple": "#9467bd",
  "Legacy Cyan": "#17becf",
  "Legacy Gray": "#7f7f7f"
};

export const INDICATOR_VALUE_MIN = 0;
export const INDICATOR_VALUE_MAX = 100;

const DEFAULT_TRACE_COLOR = Object.values(TRACE_COLOR_OPTIONS)[0];
const INDICATOR_GAUGE_STEPS = [
  {color: "#80000030", range: [0, 30] as const},
  {color: "#FF000030", range: [30, 60] as const},
  {color: "#FFFF0030", range: [60, 90] as const},
  {color: "#00800030", range: [90, 100] as const}
] as const;

const isRecord = (value: unknown): value is Record<string, unknown> =>
  value !== null && typeof value === "object" && !Array.isArray(value);

export const isIncompleteNumericInput = (rawValue: string): boolean => {
  const normalized = rawValue.trim();
  return (
    normalized.length === 0 ||
    normalized === "-" ||
    normalized === "+" ||
    normalized === "." ||
    normalized === "-." ||
    normalized === "+." ||
    /^[+-]?\d+\.$/.test(normalized)
  );
};

const indicatorValueSchema = z.preprocess((value) => {
  if (typeof value !== "string") {
    return value;
  }

  const normalized = value.trim();
  if (isIncompleteNumericInput(normalized)) {
    return value;
  }

  return Number(normalized);
}, z.number().finite().min(INDICATOR_VALUE_MIN).max(INDICATOR_VALUE_MAX));

const indicatorTraceSchema = z.object({
  type: z.literal("indicator"),
  name: z.string(),
  mode: z.literal("gauge+number"),
  value: z.number().finite().min(INDICATOR_VALUE_MIN).max(INDICATOR_VALUE_MAX),
  number: z.object({
    suffix: z.literal("%"),
    font: z.object({
      size: z.number().finite()
    })
  }),
  gauge: z.object({
    shape: z.literal("angular"),
    axis: z.object({
      range: z.tuple([z.literal(INDICATOR_VALUE_MIN), z.literal(INDICATOR_VALUE_MAX)])
    }),
    bar: z.object({
      color: z.string()
    }),
    borderwidth: z.number().finite().min(0).optional(),
    steps: z.array(z.object({
      color: z.string(),
      range: z.tuple([z.number().finite(), z.number().finite()])
    }))
  })
});

export const parseIndicatorValueInput = (rawValue: string): number | null => {
  const parsedValue = indicatorValueSchema.safeParse(rawValue);
  return parsedValue.success ? parsedValue.data : null;
};

export const syncPieMarkerColors = (
  trace: Record<string, unknown>,
  preferredColor?: string
): Record<string, unknown> => {
  const marker = isRecord(trace.marker) ? {...trace.marker} : {};
  const labels = Array.isArray(trace.labels) ? trace.labels : [];
  const values = Array.isArray(trace.values) ? trace.values : [];
  const colorCount = Math.max(labels.length, values.length, 1);
  const existingColors = Array.isArray(marker.colors) ? [...marker.colors] : [];
  const fallbackColor =
    preferredColor ??
    (typeof marker.color === "string" ? marker.color : undefined) ??
    existingColors.find((entry) => typeof entry === "string") ??
    DEFAULT_TRACE_COLOR;
  const shouldReplaceExistingColors = typeof preferredColor === "string" && preferredColor.length > 0;

  const nextColors = Array.from({length: colorCount}, (_, index) => {
    if (shouldReplaceExistingColors) {
      return fallbackColor;
    }

    const existing = existingColors[index];
    return typeof existing === "string" ? existing : fallbackColor;
  });

  marker.color = typeof nextColors[0] === "string" ? nextColors[0] : fallbackColor;
  marker.colors = nextColors;

  return {
    ...trace,
    marker
  };
};

export const createPieTraceTemplate = (traceName: string): Record<string, unknown> =>
  syncPieMarkerColors(
    {
      type: "pie",
      name: traceName,
      hole: 0.72,
      sort: false,
      labels: ["fill"],
      values: [30],
      textinfo: "none",
      direction: "clockwise",
      hovertemplate: "%{label}: %{value}<extra></extra>"
    },
    DEFAULT_TRACE_COLOR
  );

export const createIndicatorTraceTemplate = (traceName: string): Record<string, unknown> =>
  indicatorTraceSchema.parse({
    type: "indicator",
    name: traceName,
    mode: "gauge+number",
    value: 0,
    number: {
      suffix: "%",
      font: {
        size: 22
      }
    },
    gauge: {
      shape: "angular",
      axis: {
        range: [0, 100]
      },
      bar: {
        color: DEFAULT_TRACE_COLOR
      },
      borderwidth: 0,
      steps: INDICATOR_GAUGE_STEPS.map((step) => ({
        color: step.color,
        range: [...step.range]
      }))
    }
  });

export const createScatterTraceTemplate = (traceName: string): Record<string, unknown> => ({
  x: [],
  y: [],
  line: {color: DEFAULT_TRACE_COLOR, width: 2},
  mode: "lines+markers",
  name: traceName,
  type: "scatter",
  marker: {size: 6}
});

export const createBarTraceTemplate = (traceName: string): Record<string, unknown> => ({
  type: "bar",
  name: traceName,
  x: [],
  y: [],
  marker: {color: DEFAULT_TRACE_COLOR}
});

export const createTraceTemplate = (
  traceType: TraceType | null,
  traceName: string
): Record<string, unknown> => {
  const normalizedType = traceType ?? "bar";

  if (normalizedType === "pie") {
    return createPieTraceTemplate(traceName);
  }
  if (normalizedType === "indicator") {
    return createIndicatorTraceTemplate(traceName);
  }
  if (normalizedType === "scatter") {
    return createScatterTraceTemplate(traceName);
  }
  return createBarTraceTemplate(traceName);
};
