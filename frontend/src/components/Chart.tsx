import { onCleanup, onMount } from "solid-js";
import Plotly from "plotly.js-dist-min";

type PlotlyData = Partial<Plotly.PlotData>;
type PlotlyLayout = Partial<Plotly.Layout>;
type PlotlyConfig = Partial<Plotly.Config>;

export type PlotlyChartProps = {
    name: string;
    data: PlotlyData[];
    layout?: PlotlyLayout;
    config?: PlotlyConfig;
    class?: string;
};

export default function PlotlyChart(props: PlotlyChartProps) {
    let el!: HTMLDivElement;

    const render = async () => {
        const config: PlotlyConfig = {
            displayModeBar: false,
            responsive: true,
            ...props.config,
        };

        const layout: PlotlyLayout = {
            margin: { l: 40, r: 20, t: 20, b: 40 },
            paper_bgcolor: "rgba(0,0,0,0)",
            plot_bgcolor: "rgba(0,0,0,0)",
            font: { size: 12, color: "#111827" }, // slate-900
            ...props.layout,
        };

        await Plotly.react(el, props.data as any, layout as any, config as any);
    };

    onMount(() => {
        render();
        const onResize = () => Plotly.Plots.resize(el);
        window.addEventListener("resize", onResize);
        onCleanup(() => window.removeEventListener("resize", onResize));
    });

    // When props change frequently, wrap caller with createMemo + pass stable references.
    // For most dashboards, Plotly.react in onMount + occasional re-renders is fine.

    return <div ref={el} class={props.class} />;
}