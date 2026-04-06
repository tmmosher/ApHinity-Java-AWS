import {describe, expect, it, vi} from "vitest";
import {
  applyDonutCenterValueToLayout,
  attachPlotlyResizeListener,
  buildPlotlyConfig,
  buildPlotlyLayout,
  createPlotlyAnimationBaselineData,
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
