import type { PlotlyLayout } from "../components/Chart";
import type { ThemePreference } from "./themePreference";

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

const isRecord = (value: unknown): value is Record<string, unknown> =>
  value !== null && typeof value === "object" && !Array.isArray(value);

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

const applyAxisTheme = (axis: unknown, textColor: string, gridColor: string): unknown => {
  if (!isRecord(axis)) {
    return axis;
  }

  return {
    ...axis,
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

export const resolveGraphHeight = (graphStyle: unknown): string => {
  if (!isRecord(graphStyle) || typeof graphStyle.height !== "number" || graphStyle.height <= 0) {
    return FALLBACK_GRAPH_HEIGHT;
  }
  return `${graphStyle.height}px`;
};

export const resolveThemedGraphLayout = (
  layoutValue: unknown,
  graphStyle: unknown,
  theme: ThemePreference
): PlotlyLayout => {
  const layout = isRecord(layoutValue) ? layoutValue : {};
  const themeStyle = resolveGraphThemeStyle(graphStyle, theme);

  return omitUndefinedEntries({
    ...layout,
    font: {
      ...(isRecord(layout.font) ? layout.font : {}),
      color: themeStyle.textColor
    },
    title: applyTitleTheme(layout.title, themeStyle.textColor),
    legend: applyLegendTheme(layout.legend, themeStyle.textColor),
    xaxis: applyAxisTheme(layout.xaxis, themeStyle.textColor, themeStyle.gridColor),
    yaxis: applyAxisTheme(layout.yaxis, themeStyle.textColor, themeStyle.gridColor),
    annotations: applyAnnotationTheme(layout.annotations, themeStyle.textColor),
    paper_bgcolor: themeStyle.paperBackgroundColor ?? layout.paper_bgcolor,
    plot_bgcolor: themeStyle.plotBackgroundColor ?? layout.plot_bgcolor
  }) as PlotlyLayout;
};
