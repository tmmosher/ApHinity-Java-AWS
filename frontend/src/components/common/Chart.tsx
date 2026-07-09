import { createEffect, createSignal, onCleanup, onMount } from "solid-js";
import type * as Plotly from "plotly.js";
import type { ThemePreference } from "../../util/common/themePreference";
import { getDocumentThemePreference } from "../../util/common/themePreference";
import { resolveThemedGraphLayout } from "../../util/graph/graphTheme";
import { INDICATOR_THRESHOLD_COLOR } from "../../util/graph/graphTemplateFactory";

export type PlotlyData = Partial<Plotly.PlotData>;
export type PlotlyLayout = Partial<Plotly.Layout>;
export type PlotlyConfig = Partial<Plotly.Config>;
type PlotlyReactTarget = Pick<typeof Plotly, "react">;
type PlotlyResizeTarget = Pick<typeof Plotly, "Plots">;
type ResizeEventTarget = Pick<Window, "addEventListener" | "removeEventListener">;
type PlotlyModule = typeof Plotly;

let plotlyModulePromise: Promise<PlotlyModule> | null = null;

export type PlotlyChartProps = {
    name: string;
    version?: string;
    data: PlotlyData[];
    layout?: PlotlyLayout;
    config?: PlotlyConfig;
    style?: Record<string, unknown> | null;
    class?: string;
};

export const buildPlotlyConfig = (config?: PlotlyConfig): PlotlyConfig => ({
    displayModeBar: false,
    responsive: true,
    ...config,
});

export const buildPlotlyLayout = (
    layout?: PlotlyLayout,
    themePreference: ThemePreference = getDocumentThemePreference()
): PlotlyLayout => ({
    autosize: true,
    margin: { l: 40, r: 20, t: 20, b: 40 },
    paper_bgcolor: "rgba(0,0,0,0)",
    plot_bgcolor: "rgba(0,0,0,0)",
    font: { size: 12, color: themePreference === "dark" ? "#e5e7eb" : "#111827" },
    ...layout,
});

const isRecord = (value: unknown): value is Record<string, unknown> =>
    value !== null && typeof value === "object" && !Array.isArray(value);

const ISO_DATE_PATTERN = /^(\d{4})-(\d{1,2})-(\d{1,2})$/;

const normalizeIsoDateValue = (value: unknown): string | null => {
    if (typeof value !== "string") {
        return null;
    }
    const normalized = value.trim();
    const match = ISO_DATE_PATTERN.exec(normalized);
    if (!match) {
        return null;
    }

    const year = Number(match[1]);
    const month = Number(match[2]);
    const day = Number(match[3]);
    if (!Number.isInteger(year) || !Number.isInteger(month) || !Number.isInteger(day)) {
        return null;
    }

    const parsed = new Date(Date.UTC(year, month - 1, day));
    if (
        Number.isNaN(parsed.getTime()) ||
        parsed.getUTCFullYear() !== year ||
        parsed.getUTCMonth() + 1 !== month ||
        parsed.getUTCDate() !== day
    ) {
        return null;
    }

    return [
        String(year).padStart(4, "0"),
        String(month).padStart(2, "0"),
        String(day).padStart(2, "0")
    ].join("-");
};

const toFiniteNumber = (value: unknown): number | null => {
    if (typeof value === "number" && Number.isFinite(value)) {
        return value;
    }
    if (typeof value === "string") {
        const normalized = value.replace(/,/g, "").trim();
        if (normalized.length === 0) {
            return null;
        }
        const parsed = Number(normalized);
        return Number.isFinite(parsed) ? parsed : null;
    }
    return null;
};

const formatLikeTemplateNumber = (value: number, templateNumber: string): string => {
    const decimalSeparatorIndex = templateNumber.indexOf(".");
    const decimalPlaces = decimalSeparatorIndex >= 0
        ? templateNumber.length - decimalSeparatorIndex - 1
        : 0;
    const formatter = new Intl.NumberFormat("en-US", {
        useGrouping: templateNumber.includes(","),
        minimumFractionDigits: decimalPlaces,
        maximumFractionDigits: decimalPlaces
    });
    return formatter.format(value);
};

const replaceFirstNumericToken = (templateText: string, value: number): string => {
    const numberTokenPattern = /-?\d[\d,]*(?:\.\d+)?/;
    const match = numberTokenPattern.exec(templateText);
    if (!match || match.index === undefined) {
        return String(value);
    }

    const formattedNumber = formatLikeTemplateNumber(value, match[0]);
    const start = match.index;
    const end = start + match[0].length;
    return templateText.slice(0, start) + formattedNumber + templateText.slice(end);
};

const isCenteredAnnotation = (annotation: Record<string, unknown>): boolean => {
    const x = toFiniteNumber(annotation.x);
    const y = toFiniteNumber(annotation.y);
    const hasPaperAnchors = annotation.xref === "paper" && annotation.yref === "paper";
    return (x === 0.5 && y === 0.5) || hasPaperAnchors;
};

const findDonutFillValue = (data: PlotlyData[]): number | null => {
    for (const trace of data) {
        if (!isRecord(trace)) {
            continue;
        }
        if (String(trace.type ?? "").toLowerCase() !== "pie") {
            continue;
        }

        const hole = toFiniteNumber(trace.hole);
        if (hole === null || hole <= 0) {
            continue;
        }

        if (!Array.isArray(trace.values) || trace.values.length === 0) {
            continue;
        }

        const fillValue = toFiniteNumber(trace.values[0]);
        if (fillValue !== null) {
            return fillValue;
        }
    }
    return null;
};

const sortDateSeriesTrace = (trace: PlotlyData): PlotlyData => {
    if (!isRecord(trace)) {
        return trace;
    }

    const traceType = String(trace.type ?? "").toLowerCase();
    if (traceType !== "scatter" && traceType !== "scattergl") {
        return trace;
    }

    const xValues = trace.x;
    if (!Array.isArray(xValues) || xValues.length < 2) {
        return trace;
    }

    let hasDateValue = false;
    const datedPoints = xValues.map((value, index) => {
        const normalizedDate = normalizeIsoDateValue(value);
        if (normalizedDate !== null) {
            hasDateValue = true;
            return {index, normalizedDate, sortable: true as const};
        }
        if (value === null || value === undefined || (typeof value === "string" && value.trim().length === 0)) {
            return {index, normalizedDate: "", sortable: false as const};
        }
        return null;
    });

    if (!hasDateValue || datedPoints.some((point) => point === null)) {
        return trace;
    }

    const sortedPoints = [...datedPoints].sort((left, right) => {
        if (left.sortable !== right.sortable) {
            return left.sortable ? -1 : 1;
        }
        if (!left.sortable) {
            return left.index - right.index;
        }
        return left.normalizedDate.localeCompare(right.normalizedDate) || left.index - right.index;
    });

    const alreadySorted = sortedPoints.every((point, index) => point.index === index);
    if (alreadySorted) {
        return trace;
    }

    const nextTrace: Record<string, unknown> = {...trace};
    for (const [key, value] of Object.entries(trace)) {
        if (!Array.isArray(value) || value.length !== xValues.length) {
            continue;
        }
        nextTrace[key] = sortedPoints.map((point) => value[point.index]);
    }
    return nextTrace as PlotlyData;
};

const normalizeDateSeriesData = (data: PlotlyData[]): PlotlyData[] => {
    let changed = false;
    const normalizedData = data.map((trace) => {
        const normalizedTrace = sortDateSeriesTrace(trace);
        if (normalizedTrace !== trace) {
            changed = true;
        }
        return normalizedTrace;
    });
    return changed ? normalizedData : data;
};

const syncIndicatorThresholdValue = (trace: PlotlyData): PlotlyData => {
    if (!isRecord(trace) || String(trace.type ?? "").toLowerCase() !== "indicator") {
        return trace;
    }

    const value = toFiniteNumber(trace.value);
    if (value === null) {
        return trace;
    }

    const gauge = isRecord(trace.gauge) ? trace.gauge : {};
    const currentThreshold = isRecord(gauge.threshold) ? gauge.threshold : {};
    const currentLine = isRecord(currentThreshold.line) ? currentThreshold.line : {};
    const currentThresholdValue = toFiniteNumber(currentThreshold.value);
    const currentThickness = toFiniteNumber(currentThreshold.thickness);
    const currentLineWidth = toFiniteNumber(currentLine.width);

    if (
        currentThresholdValue === value &&
        typeof currentLine.color === "string" &&
        currentThickness !== null &&
        currentLineWidth !== null
    ) {
        return trace;
    }

    return {
        ...trace,
        gauge: {
            ...gauge,
            threshold: {
                ...currentThreshold,
                line: {
                    ...currentLine,
                    color: typeof currentLine.color === "string" ? currentLine.color : INDICATOR_THRESHOLD_COLOR,
                    width: currentLineWidth ?? 2
                },
                thickness: currentThickness ?? 0.75,
                value
            }
        }
    };
};

const normalizeIndicatorData = (data: PlotlyData[]): PlotlyData[] => {
    let changed = false;
    const normalizedData = data.map((trace) => {
        const normalizedTrace = syncIndicatorThresholdValue(trace);
        if (normalizedTrace !== trace) {
            changed = true;
        }
        return normalizedTrace;
    });
    return changed ? normalizedData : data;
};

export const applyDonutCenterValueToLayout = (
    data: PlotlyData[],
    layout?: PlotlyLayout
): PlotlyLayout | undefined => {
    if (!layout || !Array.isArray(layout.annotations) || layout.annotations.length === 0) {
        return layout;
    }

    const fillValue = findDonutFillValue(data);
    if (fillValue === null) {
        return layout;
    }

    const annotationIndex = layout.annotations.findIndex((entry) => isRecord(entry) && isCenteredAnnotation(entry));
    if (annotationIndex < 0) {
        return layout;
    }

    const annotation = layout.annotations[annotationIndex];
    if (!isRecord(annotation)) {
        return layout;
    }

    const nextText = typeof annotation.text === "string"
        ? replaceFirstNumericToken(annotation.text, fillValue)
        : String(fillValue);

    if (annotation.text === nextText) {
        return layout;
    }

    const nextAnnotations = layout.annotations.slice();
    nextAnnotations[annotationIndex] = {
        ...annotation,
        text: nextText
    };

    return {
        ...layout,
        annotations: nextAnnotations
    };
};

export const purgePlotlyChart = (
    plotly: Partial<Pick<typeof Plotly, "purge">> | null | undefined,
    el: HTMLDivElement
) => {
    if (!plotly || typeof plotly.purge !== "function") {
        return;
    }

    try {
        plotly.purge(el);
    } catch {
        // Plotly cleanup should never block graph teardown.
    }
};

export const renderPlotlyChart = async (
    plotly: PlotlyReactTarget,
    el: HTMLDivElement,
    data: PlotlyData[],
    layout?: PlotlyLayout,
    config?: PlotlyConfig,
    themePreference: ThemePreference = getDocumentThemePreference()
) => {
    const normalizedData = normalizeDateSeriesData(normalizeIndicatorData(data));
    const finalLayout = buildPlotlyLayout(
        applyDonutCenterValueToLayout(normalizedData, layout),
        themePreference
    );
    const finalConfig = buildPlotlyConfig(config);
    await plotly.react(el, normalizedData as any, finalLayout as any, finalConfig as any);
};

export const attachPlotlyResizeListener = (
    eventTarget: ResizeEventTarget,
    plotly: PlotlyResizeTarget,
    el: HTMLDivElement
) => {
    const onResize = () => {
        if (!el.isConnected) {
            return;
        }
        try {
            plotly.Plots.resize(el);
        } catch {
            // Plotly can race with Solid teardown; resize should not surface as a panel error.
        }
    };
    eventTarget.addEventListener("resize", onResize);
    return () => eventTarget.removeEventListener("resize", onResize);
};

/**
 * Loads Plotly once and reuses the same module instance for every chart.
 *
 * The dynamic import keeps Plotly out of the initial bundle, while the cached
 * promise prevents duplicate downloads when several charts mount together.
 */
export const loadPlotlyModule = async (): Promise<PlotlyModule> => {
    if (!plotlyModulePromise) {
        plotlyModulePromise = import("plotly.js-dist-min").then((module) => {
            const moduleWithDefault = module as unknown as { default?: PlotlyModule };
            return moduleWithDefault.default ?? (module as unknown as PlotlyModule);
        });
    }

    return plotlyModulePromise;
};

/**
 * Mounts a Plotly chart and keeps it in sync with theme changes.
 *
 * Rendering is queued so stale async updates do not overwrite a newer chart
 * state after the component has already been disposed or re-rendered.
 */
const PlotlyChart = (props: PlotlyChartProps)=> {
    let el!: HTMLDivElement;
    let disposed = false;
    let cleanupResize: (() => void) | undefined;
    let disconnectThemeObserver: (() => void) | undefined;
    let renderQueue: Promise<void> = Promise.resolve();
    const [plotlyModule, setPlotlyModule] = createSignal<PlotlyModule | null>(null);
    const [themePreference, setThemePreference] = createSignal<ThemePreference>(getDocumentThemePreference());

    onMount(() => {
        setThemePreference(getDocumentThemePreference());

        void loadPlotlyModule().then((module) => {
            if (disposed) {
                return;
            }
            setPlotlyModule(module);
        });

        const observer = new MutationObserver(() => {
            setThemePreference(getDocumentThemePreference());
        });
        observer.observe(document.documentElement, {
            attributes: true,
            attributeFilter: ["data-theme"]
        });
        disconnectThemeObserver = () => observer.disconnect();
    });

    createEffect(() => {
        const module = plotlyModule();
        if (!module) {
            return;
        }

        // The upload preview flow can hand Plotly a fresh graph payload without
        // changing the trace array identity. Treat the backend timestamp as a
        // render version so regenerated graphs are always repainted locally.
        const renderVersion = props.version;
        void renderVersion;
        const activeTheme = themePreference();
        const data = props.data;
        const layout = resolveThemedGraphLayout(props.layout, props.style, activeTheme, data);
        const config = props.config;

        renderQueue = renderQueue
            .catch(() => undefined)
            .then(async () => {
                if (disposed || !el.isConnected) {
                    return;
                }

                try {
                    await renderPlotlyChart(
                        module,
                        el,
                        data,
                        layout,
                        config,
                        activeTheme
                    );
                } catch (error) {
                    if (!disposed) {
                        purgePlotlyChart(module, el);
                    }
                    console.error(`Failed to render graph "${props.name}"`, error);
                    return;
                }
                if (disposed) {
                    return;
                }
                if (!cleanupResize) {
                    cleanupResize = attachPlotlyResizeListener(window, module, el);
                }
            });
    });

    onCleanup(() => {
        disposed = true;
        cleanupResize?.();
        disconnectThemeObserver?.();
        const module = plotlyModule();
        void renderQueue
            .catch(() => undefined)
            .then(() => purgePlotlyChart(module, el));
    });

    return <div ref={el} class={props.class} />;
}

export default PlotlyChart;
