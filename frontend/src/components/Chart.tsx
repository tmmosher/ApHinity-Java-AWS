import { createEffect, createSignal, onCleanup, onMount } from "solid-js";
import type * as Plotly from "plotly.js";
import type { ThemePreference } from "../util/themePreference";
import { getDocumentThemePreference } from "../util/themePreference";
import { resolveThemedGraphLayout } from "../util/graphTheme";

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
    margin: { l: 40, r: 20, t: 20, b: 40 },
    paper_bgcolor: "rgba(0,0,0,0)",
    plot_bgcolor: "rgba(0,0,0,0)",
    font: { size: 12, color: themePreference === "dark" ? "#e5e7eb" : "#111827" },
    ...layout,
});

export const renderPlotlyChart = async (
    plotly: PlotlyReactTarget,
    el: HTMLDivElement,
    data: PlotlyData[],
    layout?: PlotlyLayout,
    config?: PlotlyConfig,
    themePreference: ThemePreference = getDocumentThemePreference()
) => {
    await plotly.react(
        el,
        data as any,
        buildPlotlyLayout(layout, themePreference) as any,
        buildPlotlyConfig(config) as any
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

        renderQueue = renderQueue
            .catch(() => undefined)
            .then(async () => {
                if (disposed) {
                    return;
                }

                try {
                    await renderPlotlyChart(module, el, data, layout, config, activeTheme);
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
