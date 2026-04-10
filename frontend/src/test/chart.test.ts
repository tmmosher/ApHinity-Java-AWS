import {describe, expect, it, vi} from "vitest";
import {
  applyDonutCenterValueToLayout,
  attachPlotlyResizeListener,
  buildPlotlyConfig,
  buildPlotlyLayout,
  createPlotlyAnimationBaselineData,
  purgePlotlyChart,
  renderPlotlyChart
} from "../components/Chart";

vi.mock("plotly.js-dist-min", () => ({
  default: {
    react: vi.fn(),
    Plots: {
      resize: vi.fn()
    }
  }
}));

describe("Chart helpers", () => {
  it("merges default Plotly config and layout", () => {
    const config = buildPlotlyConfig({
      displayModeBar: true
    });
    const layout = buildPlotlyLayout({
      font: {
        size: 20
      }
    });

    expect(config).toMatchObject({
      displayModeBar: true,
      responsive: true
    });
    expect(layout).toMatchObject({
      margin: {l: 40, r: 20, t: 20, b: 40},
      font: {size: 20}
    });
  });

  it("renders chart data through Plotly.react", async () => {
    const react = vi.fn().mockResolvedValue(undefined);
    const plotly = {react} as unknown as {react: (...args: unknown[]) => Promise<unknown>};
    const element = {id: "chart-root"} as unknown as HTMLDivElement;
    const data = [{type: "bar"}];

    await renderPlotlyChart(
      plotly as any,
      element,
      data,
      {font: {size: 16}},
      {displayModeBar: true}
    );

    expect(react).toHaveBeenCalledTimes(1);
    const [calledElement, calledData, calledLayout, calledConfig] = react.mock.calls[0];
    expect(calledElement).toBe(element);
    expect(calledData).toBe(data);
    expect(calledLayout).toMatchObject({
      margin: {l: 40, r: 20, t: 20, b: 40},
      font: {size: 16}
    });
    expect(calledConfig).toMatchObject({
      displayModeBar: true,
      responsive: true
    });
  });

  it("purges Plotly state when a chart is torn down", () => {
    const purge = vi.fn();
    const element = {id: "chart-root"} as unknown as HTMLDivElement;

    purgePlotlyChart({purge} as unknown as {purge: (...args: unknown[]) => void}, element);

    expect(purge).toHaveBeenCalledTimes(1);
    expect(purge).toHaveBeenCalledWith(element);
  });

  it("treats missing Plotly cleanup hooks as a no-op", () => {
    const element = {id: "chart-root"} as unknown as HTMLDivElement;

    expect(() => purgePlotlyChart(undefined, element)).not.toThrow();
  });

  it("swallows Plotly purge failures during cleanup", () => {
    const purge = vi.fn(() => {
      throw new Error("cleanup failed");
    });
    const element = {id: "chart-root"} as unknown as HTMLDivElement;

    expect(() =>
      purgePlotlyChart({purge} as unknown as {purge: (...args: unknown[]) => void}, element)
    ).not.toThrow();
  });

  it("builds zero-baseline data for supported trace types", () => {
    expect(createPlotlyAnimationBaselineData([
      {type: "bar", x: ["Jan"], y: [9]},
      {type: "bar", orientation: "h", x: [7], y: ["North"]},
      {type: "scatter", x: [1, 2], y: [5, 8]},
      {type: "pie", values: [60, 40]},
      {type: "indicator", value: 12}
    ])).toEqual([
      {type: "bar", x: ["Jan"], y: [0]},
      {type: "bar", orientation: "h", x: [0], y: ["North"]},
      {type: "scatter", x: [1, 2], y: [0, 0]},
      {type: "pie", values: [0, 0]},
      {type: "indicator", value: 12}
    ]);
  });

  it("animates multi-trace scatter charts from a zero baseline without losing the final payload", async () => {
    const react = vi.fn().mockResolvedValue(undefined);
    const animate = vi.fn().mockResolvedValue(undefined);
    const plotly = {
      react,
      animate
    } as unknown as {
      react: (...args: unknown[]) => Promise<unknown>;
      animate: (...args: unknown[]) => Promise<unknown>;
    };
    const element = {id: "chart-root"} as unknown as HTMLDivElement;
    const data = [
      {
        type: "scatter",
        name: "HPC",
        x: ["2025-01-01", "2025-02-01"],
        y: [14, 13],
        line: {color: "#1f77b4", width: 2},
        mode: "lines+markers",
        marker: {size: 6}
      },
      {
        type: "scatter",
        name: "Endotoxin",
        x: ["2025-01-01", "2025-02-01"],
        y: [6, 5],
        line: {color: "#2ca02c", width: 2},
        mode: "lines+markers",
        marker: {size: 6}
      }
    ];

    await renderPlotlyChart(
      plotly as any,
      element,
      data,
      {
        margin: {b: 10, l: 10, r: 10, t: 10},
        showlegend: false,
        annotations: [
          {
            x: 0.5,
            y: 0.5,
            font: {size: 22},
            text: "<b>68%</b>",
            xref: "paper",
            yref: "paper",
            showarrow: false
          }
        ]
      },
      undefined,
      "light",
      {animateFromBaseline: true}
    );

    expect(react).toHaveBeenCalledTimes(1);
    expect(react.mock.calls[0][1]).toEqual([
      {
        type: "scatter",
        name: "HPC",
        x: ["2025-01-01", "2025-02-01"],
        y: [0, 0],
        line: {color: "#1f77b4", width: 2},
        mode: "lines+markers",
        marker: {size: 6}
      },
      {
        type: "scatter",
        name: "Endotoxin",
        x: ["2025-01-01", "2025-02-01"],
        y: [0, 0],
        line: {color: "#2ca02c", width: 2},
        mode: "lines+markers",
        marker: {size: 6}
      }
    ]);
    expect(animate).toHaveBeenCalledTimes(1);
    expect(animate.mock.calls[0][1]).toMatchObject({
      data,
      traces: [0, 1]
    });
    expect(data[0].y).toEqual([14, 13]);
    expect(data[1].y).toEqual([6, 5]);
  });

  it("animates supported traces from a zero baseline when requested", async () => {
    const react = vi.fn().mockResolvedValue(undefined);
    const animate = vi.fn().mockResolvedValue(undefined);
    const plotly = {
      react,
      animate
    } as unknown as {
      react: (...args: unknown[]) => Promise<unknown>;
      animate: (...args: unknown[]) => Promise<unknown>;
    };

    await renderPlotlyChart(
      plotly as any,
      {id: "chart-root"} as unknown as HTMLDivElement,
      [{type: "bar", x: ["Jan"], y: [9]}],
      undefined,
      undefined,
      "light",
      {animateFromBaseline: true}
    );

    expect(react).toHaveBeenCalledTimes(1);
    expect(react.mock.calls[0][1]).toEqual([{type: "bar", x: ["Jan"], y: [0]}]);
    expect(animate).toHaveBeenCalledTimes(1);
    expect(animate.mock.calls[0][1]).toMatchObject({
      data: [{type: "bar", x: ["Jan"], y: [9]}],
      traces: [0]
    });
  });

  it("uses first donut fill value for centered KPI annotation text", () => {
    const layout = applyDonutCenterValueToLayout(
      [
        {
          type: "pie",
          hole: 0.72,
          values: [60, 40]
        }
      ],
      {
        annotations: [
          {
            x: 0.5,
            y: 0.5,
            showarrow: false,
            text: "<b>68%</b>"
          }
        ]
      }
    );

    expect(layout?.annotations?.[0]).toMatchObject({
      text: "<b>60%</b>"
    });
  });

  it("preserves template number formatting for centered donut annotation text", () => {
    const layout = applyDonutCenterValueToLayout(
      [
        {
          type: "pie",
          hole: 0.72,
          values: [1234, 99]
        }
      ],
      {
        annotations: [
          {
            xref: "paper",
            yref: "paper",
            text: "<b>2,307</b>"
          }
        ]
      }
    );

    expect(layout?.annotations?.[0]).toMatchObject({
      text: "<b>1,234</b>"
    });
  });

  it("registers resize handling and removes it on cleanup", () => {
    const addEventListener = vi.fn();
    const removeEventListener = vi.fn();
    const eventTarget = {
      addEventListener,
      removeEventListener
    };

    const resize = vi.fn();
    const plotly = {
      Plots: {
        resize
      }
    };

    const element = {id: "chart-root"} as unknown as HTMLDivElement;
    const cleanup = attachPlotlyResizeListener(
      eventTarget as any,
      plotly as any,
      element
    );

    expect(addEventListener).toHaveBeenCalledWith("resize", expect.any(Function));
    const resizeHandler = addEventListener.mock.calls[0][1] as () => void;

    resizeHandler();
    expect(resize).toHaveBeenCalledWith(element);

    cleanup();
    expect(removeEventListener).toHaveBeenCalledWith("resize", resizeHandler);
  });
});
