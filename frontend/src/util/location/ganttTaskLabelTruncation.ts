const SVG_LABEL_PADDING = 12;
const SVG_ELLIPSIS = "...";

type SvgBarElement = SVGRectElement & {
  getWidth: () => number;
};

const getMeasurementContext = (): CanvasRenderingContext2D | undefined => {
  if (typeof document === "undefined") {
    return undefined;
  }

  const canvas = document.createElement("canvas");
  return canvas.getContext("2d") ?? undefined;
};

const getSvgTextFont = (element: SVGTextElement): string => {
  if (typeof window === "undefined") {
    return "13px Helvetica";
  }

  const computedStyle = window.getComputedStyle(element);
  return computedStyle.font || [
    computedStyle.fontStyle,
    computedStyle.fontVariant,
    computedStyle.fontWeight,
    computedStyle.fontSize,
    computedStyle.fontFamily
  ].filter(Boolean).join(" ");
};

const measureSvgTextWidth = (font: string, value: string): number => {
  const context = getMeasurementContext();
  if (!context) {
    return value.length * 8;
  }

  context.font = font;
  return context.measureText(value).width;
};

export const truncateTextToWidth = (
  value: string,
  maxWidth: number,
  measureWidth: (candidate: string) => number,
  ellipsis = SVG_ELLIPSIS
): string => {
  if (!value) {
    return value;
  }
  if (maxWidth <= 0) {
    return "";
  }

  if (measureWidth(value) <= maxWidth) {
    return value;
  }

  const ellipsisWidth = measureWidth(ellipsis);
  if (ellipsisWidth > maxWidth) {
    return "";
  }

  const chars = Array.from(value);
  let low = 0;
  let high = chars.length;
  let bestLength = 0;

  while (low <= high) {
    const mid = Math.floor((low + high) / 2);
    const candidate = chars.slice(0, mid).join("").trimEnd();
    const candidateWidth = measureWidth(candidate) + ellipsisWidth;

    if (candidateWidth <= maxWidth) {
      bestLength = mid;
      low = mid + 1;
    } else {
      high = mid - 1;
    }
  }

  if (bestLength > 0) {
    const candidate = chars.slice(0, bestLength).join("").trimEnd();
    return candidate ? `${candidate}${ellipsis}` : ellipsis;
  }

  return ellipsisWidth <= maxWidth ? ellipsis : "";
};

export const fitGanttTaskLabels = (root: ParentNode): void => {
  root.querySelectorAll<SVGGElement>(".bar-wrapper").forEach((barWrapper) => {
    const bar = barWrapper.querySelector(".bar") as SvgBarElement | null;
    const label = barWrapper.querySelector<SVGTextElement>(".bar-label");

    if (!bar || !label) {
      return;
    }

    const fullLabel = label.dataset.fullLabel ?? label.textContent ?? "";
    if (!fullLabel) {
      return;
    }

    label.dataset.fullLabel = fullLabel;

    const availableWidth = Math.max(0, bar.getWidth() - SVG_LABEL_PADDING);
    const font = getSvgTextFont(label);
    const visibleLabel = truncateTextToWidth(
      fullLabel,
      availableWidth,
      (candidate) => measureSvgTextWidth(font, candidate)
    );

    label.textContent = visibleLabel;
    label.setAttribute("title", fullLabel);
    label.setAttribute("aria-label", fullLabel);
  });
};
