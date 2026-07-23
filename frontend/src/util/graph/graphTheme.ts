import type { PlotlyData, PlotlyLayout } from "../../components/common/Chart";
import type { ThemePreference } from "../common/themePreference";
import {normalizePlotlyLayoutTitle} from "./graphLayoutTitle";

export type GraphThemeStyle = {
  textColor: string;
  gridColor: string;
  paperBackgroundColor?: string;
  plotBackgroundColor?: string;
};

const DEFAULT_GRAPH_THEME_STYLE: Record<ThemePreference, GraphThemeStyle> = {
  light: {
    textColor: "#111827",
    gridColor: "rgba(15, 23, 42, 0.15)"
  },
  dark: {
    textColor: "#e5e7eb",
    gridColor: "rgba(148, 163, 184, 0.3)"
  }
};

const FALLBACK_GRAPH_HEIGHT = "18rem";
const FALLBACK_GRAPH_HEIGHT_PX = 288;
const GRAPH_SIZE_HEIGHT_PX = {
  half: 160,
  full: 320,
  duplex: 640,
  double: 640
} as const;
const CARTESIAN_LEGEND_HEIGHT_RATIO = 1 / 4;
const CARTESIAN_LEGEND_MAX_HEIGHT_RATIO = 1 / 4;
const CARTESIAN_LEGEND_FONT_SIZE_PX = 10;
const CARTESIAN_LEGEND_ITEM_WIDTH_PX = 30;
const CARTESIAN_LEGEND_TRACE_GROUP_GAP_PX = 2;

export type GraphDisplaySize = keyof typeof GRAPH_SIZE_HEIGHT_PX;

const isRecord = (value: unknown): value is Record<string, unknown> =>
  value !== null && typeof value === "object" && !Array.isArray(value);

const toFiniteNumber = (value: unknown): number | null =>
  typeof value === "number" && Number.isFinite(value) ? value : null;

const omitUndefinedEntries = <T extends Record<string, unknown>>(value: T): T => {
  const normalized = {...value};
  for (const [key, entry] of Object.entries(normalized)) {
    if (entry === undefined) {
      delete normalized[key];
    }
  }
  return normalized as T;
};

const applyTitleTheme = (title: unknown, textColor: string): unknown => {
  if (typeof title === "string") {
    return {
      text: title,
      font: {color: textColor}
    };
  }

  if (!isRecord(title)) {
    return title;
  }

  return {
    ...title,
    font: {
      ...(isRecord(title.font) ? title.font : {}),
      color: textColor
    }
  };
};

const applyLayoutTitleTheme = (title: unknown, textColor: string): unknown => {
  const normalizedTitle = normalizePlotlyLayoutTitle(title);
  if (!isRecord(normalizedTitle)) {
    return normalizedTitle;
  }

  return {
    ...normalizedTitle,
    font: {
      ...(isRecord(normalizedTitle.font) ? normalizedTitle.font : {}),
      color: textColor
    }
  };
};

const applyLegendTheme = (legend: unknown, textColor: string): unknown => {
  if (!isRecord(legend)) {
    return legend;
  }

  return {
    ...legend,
    font: {
      ...(isRecord(legend.font) ? legend.font : {}),
      color: textColor
    }
  };
};

const applyAxisTheme = (axis: unknown, textColor: string, gridColor: string, axisName: "x" | "y"): unknown => {
  if (!isRecord(axis)) {
    return axis;
  }

  return {
    ...axis,
    ...(axisName === "x" && axis.type === "date"
      ? {
          tickangle: axis.tickangle ?? -35,
          automargin: axis.automargin ?? true
        }
      : {}),
    color: textColor,
    title: applyTitleTheme(axis.title, textColor),
    tickfont: {
      ...(isRecord(axis.tickfont) ? axis.tickfont : {}),
      color: textColor
    },
    gridcolor: axis.gridcolor ?? gridColor,
    zerolinecolor: axis.zerolinecolor ?? gridColor,
    linecolor: axis.linecolor ?? gridColor
  };
};

const applyAnnotationTheme = (annotations: unknown, textColor: string): unknown => {
  if (!Array.isArray(annotations)) {
    return annotations;
  }

  return annotations.map((annotation) => {
    if (!isRecord(annotation)) {
      return annotation;
    }

    return {
      ...annotation,
      font: {
        ...(isRecord(annotation.font) ? annotation.font : {}),
        color: textColor
      }
    };
  });
};

export const resolveGraphThemeStyle = (
  graphStyle: unknown,
  theme: ThemePreference
): GraphThemeStyle => {
  const defaults = DEFAULT_GRAPH_THEME_STYLE[theme];
  if (!isRecord(graphStyle)) {
    return defaults;
  }

  const themeMap = isRecord(graphStyle.theme) ? graphStyle.theme : null;
  const themeStyle = themeMap && isRecord(themeMap[theme]) ? themeMap[theme] : null;

  return {
    textColor: typeof themeStyle?.textColor === "string" ? themeStyle.textColor : defaults.textColor,
    gridColor: typeof themeStyle?.gridColor === "string" ? themeStyle.gridColor : defaults.gridColor,
    paperBackgroundColor: typeof themeStyle?.paperBackgroundColor === "string"
      ? themeStyle.paperBackgroundColor
      : defaults.paperBackgroundColor,
    plotBackgroundColor: typeof themeStyle?.plotBackgroundColor === "string"
      ? themeStyle.plotBackgroundColor
      : defaults.plotBackgroundColor
  };
};

export const resolveGraphSize = (graphLayout: unknown): GraphDisplaySize | null => {
  if (!isRecord(graphLayout) || !isRecord(graphLayout.meta)) {
    return null;
  }

  const rawSize = graphLayout.meta.aphinitySize;
  return rawSize === "half" || rawSize === "full" || rawSize === "duplex" || rawSize === "double"
    ? rawSize
    : null;
};

export const resolveGraphHeight = (graphStyle: unknown, graphLayout?: unknown): string => {
  const graphSize = resolveGraphSize(graphLayout);
  if (graphSize) {
    return `${GRAPH_SIZE_HEIGHT_PX[graphSize]}px`;
  }

  if (!isRecord(graphStyle) || typeof graphStyle.height !== "number" || graphStyle.height <= 0) {
    return FALLBACK_GRAPH_HEIGHT;
  }
  return `${graphStyle.height}px`;
};

const resolveGraphHeightPixels = (graphStyle: unknown, graphLayout?: unknown): number => {
  const graphSize = resolveGraphSize(graphLayout);
  if (graphSize) {
    return GRAPH_SIZE_HEIGHT_PX[graphSize];
  }

  if (!isRecord(graphStyle) || typeof graphStyle.height !== "number" || graphStyle.height <= 0) {
    return FALLBACK_GRAPH_HEIGHT_PX;
  }
  return graphStyle.height;
};

export const resolveGraphGridClass = (graphLayout: unknown): string =>
  resolveGraphSize(graphLayout) === "duplex" || resolveGraphSize(graphLayout) === "double"
    ? "lg:col-span-2"
    : "";

const hasCartesianAxes = (layout: Record<string, unknown>): boolean =>
  Object.keys(layout).some((key) => /^xaxis\d*$/.test(key) || /^yaxis\d*$/.test(key));

const hasBarTrace = (data: readonly PlotlyData[] | undefined): boolean =>
  Array.isArray(data) && data.some((trace) => trace?.type === "bar");

const applyCartesianLegendGeometry = (
  layout: Record<string, unknown>,
  graphStyle: unknown,
  data: readonly PlotlyData[] | undefined
): Record<string, unknown> => {
  if (layout.showlegend === false || !hasCartesianAxes(layout) || hasBarTrace(data)) {
    return layout;
  }

  const legend = isRecord(layout.legend) ? layout.legend : {};
  const margin = isRecord(layout.margin) ? layout.margin : {};
  const graphHeight = resolveGraphHeightPixels(graphStyle, layout);
  const reservedLegendBand = Math.round(graphHeight * CARTESIAN_LEGEND_HEIGHT_RATIO);
  const nextBottomMargin = Math.max(toFiniteNumber(margin.b) ?? 0, reservedLegendBand);

  return {
    ...layout,
    legend: {
      ...legend,
      orientation: "h",
      x: 0,
      xanchor: "left",
      y: -0.16,
      yanchor: "top",
      maxheight: CARTESIAN_LEGEND_MAX_HEIGHT_RATIO,
      itemwidth: CARTESIAN_LEGEND_ITEM_WIDTH_PX,
      itemsizing: "trace",
      tracegroupgap: CARTESIAN_LEGEND_TRACE_GROUP_GAP_PX,
      font: {
        ...(isRecord(legend.font) ? legend.font : {}),
        size: CARTESIAN_LEGEND_FONT_SIZE_PX
      }
    },
    margin: {
      ...margin,
      b: nextBottomMargin
    }
  };
};

export const resolveThemedGraphLayout = (
  layoutValue: unknown,
  graphStyle: unknown,
  theme: ThemePreference,
  data?: readonly PlotlyData[]
): PlotlyLayout => {
  const layout = isRecord(layoutValue) ? applyCartesianLegendGeometry(layoutValue, graphStyle, data) : {};
  const themeStyle = resolveGraphThemeStyle(graphStyle, theme);

  return omitUndefinedEntries({
    ...layout,
    font: {
      ...(isRecord(layout.font) ? layout.font : {}),
      color: themeStyle.textColor
    },
    title: applyLayoutTitleTheme(layout.title, themeStyle.textColor),
    legend: applyLegendTheme(layout.legend, themeStyle.textColor),
    xaxis: applyAxisTheme(layout.xaxis, themeStyle.textColor, themeStyle.gridColor, "x"),
    yaxis: applyAxisTheme(layout.yaxis, themeStyle.textColor, themeStyle.gridColor, "y"),
    annotations: applyAnnotationTheme(layout.annotations, themeStyle.textColor),
    paper_bgcolor: themeStyle.paperBackgroundColor ?? layout.paper_bgcolor,
    plot_bgcolor: themeStyle.plotBackgroundColor ?? layout.plot_bgcolor
  }) as PlotlyLayout;
};
