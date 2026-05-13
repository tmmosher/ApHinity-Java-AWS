import {describe, expect, it, vi} from "vitest";
import {
  applyDonutCenterValueToLayout,
  attachPlotlyResizeListener,
  buildPlotlyConfig,
  buildPlotlyLayout,
  purgePlotlyChart,
  renderPlotlyChart
} from "../components/common/Chart";

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
    expect(calledData).toEqual(data);
    expect(calledLayout).toMatchObject({
      margin: {l: 40, r: 20, t: 20, b: 40},
      font: {size: 16}
    });
    expect(calledConfig).toMatchObject({
      displayModeBar: true,
      responsive: true
    });
  });

  it("syncs indicator threshold values to the current indicator value before rendering", async () => {
    const react = vi.fn().mockResolvedValue(undefined);
    const plotly = {react} as unknown as {react: (...args: unknown[]) => Promise<unknown>};
    const element = {id: "chart-root"} as unknown as HTMLDivElement;
    const data = [{
      type: "indicator",
      value: 68,
      gauge: {
        shape: "angular",
        axis: {range: [0, 100]},
        bar: {color: "#1f77b4"},
        threshold: {
          line: {color: "red", width: 2},
          thickness: 0.75,
          value: 90
        }
      }
    }];

    await renderPlotlyChart(
      plotly as any,
      element,
      data
    );

    const [, calledData] = react.mock.calls[0];
    expect(calledData).toEqual([{
      type: "indicator",
      value: 68,
      gauge: {
        shape: "angular",
        axis: {range: [0, 100]},
        bar: {color: "#1f77b4"},
        threshold: {
          line: {color: "red", width: 2},
          thickness: 0.75,
          value: 68
        }
      }
    }]);
    expect(data[0]).toEqual({
      type: "indicator",
      value: 68,
      gauge: {
        shape: "angular",
        axis: {range: [0, 100]},
        bar: {color: "#1f77b4"},
        threshold: {
          line: {color: "red", width: 2},
          thickness: 0.75,
          value: 90
        }
      }
    });
  });

  it("sorts scatter date-series points chronologically before rendering", async () => {
    const react = vi.fn().mockResolvedValue(undefined);
    const plotly = {react} as unknown as {react: (...args: unknown[]) => Promise<unknown>};
    const element = {id: "chart-root"} as unknown as HTMLDivElement;
    const data = [{
      type: "scatter",
      name: "Cooling Towers",
      x: ["2025-08-01", "2025-07-01", "2025-09-01"],
      y: [90, 75, 100],
      customdata: [
        {sampleCount: 10},
        {sampleCount: 8},
        {sampleCount: 12}
      ]
    }];

    await renderPlotlyChart(
      plotly as any,
      element,
      data,
      undefined,
      undefined
    );

    const [, calledData] = react.mock.calls[0];
    expect(calledData).toEqual([{
      type: "scatter",
      name: "Cooling Towers",
      x: ["2025-07-01", "2025-08-01", "2025-09-01"],
      y: [75, 90, 100],
      customdata: [
        {sampleCount: 8},
        {sampleCount: 10},
        {sampleCount: 12}
      ]
    }]);
    expect(data[0]).toEqual({
      type: "scatter",
      name: "Cooling Towers",
      x: ["2025-08-01", "2025-07-01", "2025-09-01"],
      y: [90, 75, 100],
      customdata: [
        {sampleCount: 10},
        {sampleCount: 8},
        {sampleCount: 12}
      ]
    });
  });

  it("sorts scatter date-series points with single-digit month or day before rendering", async () => {
    const react = vi.fn().mockResolvedValue(undefined);
    const plotly = {react} as unknown as {react: (...args: unknown[]) => Promise<unknown>};
    const element = {id: "chart-root"} as unknown as HTMLDivElement;
    const data = [{
      type: "scatter",
      name: "Utility SPD",
      x: ["2025-8-1", "2025-07-01", "2025-9-1"],
      y: [90, 75, 100]
    }];

    await renderPlotlyChart(
      plotly as any,
      element,
      data,
      undefined,
      undefined
    );

    const [, calledData] = react.mock.calls[0];
    expect(calledData).toEqual([{
      type: "scatter",
      name: "Utility SPD",
      x: ["2025-07-01", "2025-8-1", "2025-9-1"],
      y: [75, 90, 100]
    }]);
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
