import { createEffect, createSignal, onCleanup, onMount } from "solid-js";
import type * as Plotly from "plotly.js";
import type { ThemePreference } from "../util/common/themePreference";
import { getDocumentThemePreference } from "../util/common/themePreference";
import { resolveThemedGraphLayout } from "../util/graph/graphTheme";

export type PlotlyData = Partial<Plotly.PlotData>;
export type PlotlyLayout = Partial<Plotly.Layout>;
export type PlotlyConfig = Partial<Plotly.Config>;
type PlotlyReactTarget = Pick<typeof Plotly, "react">;
type PlotlyAnimatedRenderTarget = Pick<typeof Plotly, "react"> & Partial<Pick<typeof Plotly, "animate">>;
type PlotlyResizeTarget = Pick<typeof Plotly, "Plots">;
type ResizeEventTarget = Pick<Window, "addEventListener" | "removeEventListener">;
type PlotlyModule = typeof Plotly;

type PlotlyRenderOptions = {
    animateFromBaseline?: boolean;
};

type PlotlyAnimationFrame = {
    data: PlotlyData[];
    layout?: PlotlyLayout;
    traces: number[];
};

const GRAPH_LOAD_ANIMATION_OPTIONS = {
    transition: {
        duration: 700,
        easing: "cubic-in-out"
    },
    frame: {
        duration: 700,
        redraw: false
    },
    mode: "afterall" as const
};

let plotlyModulePromise: Promise<PlotlyModule> | null = null;

export type PlotlyChartProps = {
    name: string;
    data: PlotlyData[];
    layout?: PlotlyLayout;
    config?: PlotlyConfig;
    style?: Record<string, unknown> | null;
    animationToken?: number | string;
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
    margin: { l: 40, r: 20, t: 20, b: 40 },
    paper_bgcolor: "rgba(0,0,0,0)",
    plot_bgcolor: "rgba(0,0,0,0)",
    font: { size: 12, color: themePreference === "dark" ? "#e5e7eb" : "#111827" },
    ...layout,
});

const isRecord = (value: unknown): value is Record<string, unknown> =>
    value !== null && typeof value === "object" && !Array.isArray(value);

const cloneTraceWithZeroedValues = (
    trace: PlotlyData,
    axisKey: "x" | "y" | "values"
): PlotlyData => {
    if (!isRecord(trace) || !Array.isArray(trace[axisKey])) {
        return trace;
    }

    let changed = false;
    const zeroedValues = trace[axisKey].map((value) => {
        if (toFiniteNumber(value) === null) {
            return value;
        }
        changed = true;
        return 0;
    });

    if (!changed) {
        return trace;
    }

    return {
        ...trace,
        [axisKey]: zeroedValues
    };
};

export const createPlotlyAnimationBaselineData = (data: PlotlyData[]): PlotlyData[] => (
    data.map((trace) => {
        if (!isRecord(trace)) {
            return trace;
        }

        const normalizedType = String(trace.type ?? "").toLowerCase();
        if (normalizedType === "pie") {
            return cloneTraceWithZeroedValues(trace, "values");
        }
        if (normalizedType === "bar" && String(trace.orientation ?? "").toLowerCase() === "h") {
            return cloneTraceWithZeroedValues(trace, "x");
        }
        if (normalizedType === "bar" || normalizedType === "scatter") {
            return cloneTraceWithZeroedValues(trace, "y");
        }
        return trace;
    })
);

const canAnimateFromBaseline = (plotly: PlotlyAnimatedRenderTarget): plotly is PlotlyAnimatedRenderTarget & {
    animate: typeof Plotly.animate;
} => typeof plotly.animate === "function";

const createPlotlyAnimationFrame = (
    data: PlotlyData[],
    layout?: PlotlyLayout
): PlotlyAnimationFrame => ({
    data,
    layout,
    traces: data.map((_, index) => index)
});

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

export const renderPlotlyChart = async (
    plotly: PlotlyAnimatedRenderTarget,
    el: HTMLDivElement,
    data: PlotlyData[],
    layout?: PlotlyLayout,
    config?: PlotlyConfig,
    themePreference: ThemePreference = getDocumentThemePreference(),
    options: PlotlyRenderOptions = {}
) => {
    const finalLayout = buildPlotlyLayout(
        applyDonutCenterValueToLayout(data, layout),
        themePreference
    );
    const finalConfig = buildPlotlyConfig(config);

    if (!options.animateFromBaseline || !canAnimateFromBaseline(plotly)) {
        await plotly.react(el, data as any, finalLayout as any, finalConfig as any);
        return;
    }

    const baselineData = createPlotlyAnimationBaselineData(data);
    const shouldAnimate = baselineData.some((trace, index) => trace !== data[index]);
    if (!shouldAnimate) {
        await plotly.react(el, data as any, finalLayout as any, finalConfig as any);
        return;
    }

    const baselineLayout = buildPlotlyLayout(
        applyDonutCenterValueToLayout(baselineData, layout),
        themePreference
    );

    await plotly.react(el, baselineData as any, baselineLayout as any, finalConfig as any);
    await plotly.animate(
        el,
        createPlotlyAnimationFrame(data, finalLayout) as any,
        GRAPH_LOAD_ANIMATION_OPTIONS as any
    );
};

export const attachPlotlyResizeListener = (
    eventTarget: ResizeEventTarget,
    plotly: PlotlyResizeTarget,
    el: HTMLDivElement
) => {
    const onResize = () => plotly.Plots.resize(el);
    eventTarget.addEventListener("resize", onResize);
    return () => eventTarget.removeEventListener("resize", onResize);
};

export const loadPlotlyModule = async (): Promise<PlotlyModule> => {
    if (!plotlyModulePromise) {
        plotlyModulePromise = import("plotly.js-dist-min").then((module) => {
            const moduleWithDefault = module as unknown as { default?: PlotlyModule };
            return moduleWithDefault.default ?? (module as unknown as PlotlyModule);
        });
    }

    return plotlyModulePromise;
};

const PlotlyChart = (props: PlotlyChartProps)=> {
    let el!: HTMLDivElement;
    let disposed = false;
    let cleanupResize: (() => void) | undefined;
    let disconnectThemeObserver: (() => void) | undefined;
    let renderQueue: Promise<void> = Promise.resolve();
    let lastScheduledAnimationToken: number | string | undefined;
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

        const activeTheme = themePreference();
        const data = props.data;
        const layout = resolveThemedGraphLayout(props.layout, props.style, activeTheme);
        const config = props.config;
        const animationToken = props.animationToken;
        const shouldAnimate = animationToken !== undefined && animationToken !== lastScheduledAnimationToken;
        if (shouldAnimate) {
            lastScheduledAnimationToken = animationToken;
        }

        renderQueue = renderQueue
            .catch(() => undefined)
            .then(async () => {
                if (disposed) {
                    return;
                }

                try {
                    await renderPlotlyChart(
                        module,
                        el,
                        data,
                        layout,
                        config,
                        activeTheme,
                        {animateFromBaseline: shouldAnimate}
                    );
                } catch (error) {
                    console.error(`Failed to render graph "${props.name}"`, error);
                    return;
                }
                if (!disposed && !cleanupResize) {
                    cleanupResize = attachPlotlyResizeListener(window, module, el);
                }
            });
    });

    onCleanup(() => {
        disposed = true;
        cleanupResize?.();
        disconnectThemeObserver?.();
    });

    return <div ref={el} class={props.class} />;
}

export default PlotlyChart;
