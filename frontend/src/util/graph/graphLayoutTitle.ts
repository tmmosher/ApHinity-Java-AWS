const isRecord = (value: unknown): value is Record<string, unknown> =>
  value !== null && typeof value === "object" && !Array.isArray(value);

export const PLOTLY_LAYOUT_TITLE_X = 0.02;
export const PLOTLY_LAYOUT_TITLE_XANCHOR = "left";

export const normalizePlotlyLayoutTitle = (title: unknown): unknown => {
  if (typeof title === "string") {
    return {
      text: title,
      x: PLOTLY_LAYOUT_TITLE_X,
      xanchor: PLOTLY_LAYOUT_TITLE_XANCHOR
    };
  }

  if (!isRecord(title)) {
    return title;
  }

  return {
    ...title,
    x: PLOTLY_LAYOUT_TITLE_X,
    xanchor: PLOTLY_LAYOUT_TITLE_XANCHOR
  };
};
