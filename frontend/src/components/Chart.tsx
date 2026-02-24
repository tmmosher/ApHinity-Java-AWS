import { onCleanup, onMount } from "solid-js";
import Plotly from "plotly.js-dist-min";

export type PlotlyData = Partial<Plotly.PlotData>;
export type PlotlyLayout = Partial<Plotly.Layout>;
export type PlotlyConfig = Partial<Plotly.Config>;
type PlotlyReactTarget = Pick<typeof Plotly, "react">;
type PlotlyResizeTarget = Pick<typeof Plotly, "Plots">;
type ResizeEventTarget = Pick<Window, "addEventListener" | "removeEventListener">;

export type PlotlyChartProps = {
    name: string;
    data: PlotlyData[];
    layout?: PlotlyLayout;
    config?: PlotlyConfig;
    class?: string;
};

export const buildPlotlyConfig = (config?: PlotlyConfig): PlotlyConfig => ({
    displayModeBar: false,
    responsive: true,
    ...config,
});

export const buildPlotlyLayout = (layout?: PlotlyLayout): PlotlyLayout => ({
    margin: { l: 40, r: 20, t: 20, b: 40 },
    paper_bgcolor: "rgba(0,0,0,0)",
    plot_bgcolor: "rgba(0,0,0,0)",
    font: { size: 12, color: "#111827" }, // slate-900
    ...layout,
});

export const renderPlotlyChart = async (
    plotly: PlotlyReactTarget,
    el: HTMLDivElement,
    data: PlotlyData[],
    layout?: PlotlyLayout,
    config?: PlotlyConfig
) => {
    await plotly.react(
        el,
        data as any,
        buildPlotlyLayout(layout) as any,
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

const PlotlyChart = (props: PlotlyChartProps)=> {
    let el!: HTMLDivElement;

    const render = async () => {
        await renderPlotlyChart(Plotly, el, props.data, props.layout, props.config);
    };

    onMount(() => {
        void render();
        const cleanupResize = attachPlotlyResizeListener(window, Plotly, el);
        onCleanup(cleanupResize);
    });

    // When props change frequently, wrap caller with createMemo + pass stable references.
    // For most dashboards, Plotly.react in onMount + occasional re-renders is fine.

    return <div ref={el} class={props.class} />;
}

export default PlotlyChart;
