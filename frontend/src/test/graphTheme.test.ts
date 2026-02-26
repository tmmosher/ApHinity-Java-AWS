import {describe, expect, it} from "vitest";
import {
  resolveGraphHeight,
  resolveGraphThemeStyle,
  resolveThemedGraphLayout
} from "../util/graphTheme";

describe("graphTheme", () => {
  it("uses default theme colors when graph style has no theme block", () => {
    expect(resolveGraphThemeStyle(undefined, "light")).toEqual({
      textColor: "#111827",
      gridColor: "rgba(15, 23, 42, 0.15)",
      paperBackgroundColor: undefined,
      plotBackgroundColor: undefined
    });
    expect(resolveGraphThemeStyle({}, "dark")).toEqual({
      textColor: "#e5e7eb",
      gridColor: "rgba(148, 163, 184, 0.3)",
      paperBackgroundColor: undefined,
      plotBackgroundColor: undefined
    });
  });

  it("reads per-theme style overrides from graph style", () => {
    const graphStyle = {
      theme: {
        dark: {
          textColor: "#f4f4f5",
          gridColor: "rgba(255,255,255,0.2)",
          paperBackgroundColor: "#101214",
          plotBackgroundColor: "#0c0f11"
        }
      }
    };

    expect(resolveGraphThemeStyle(graphStyle, "dark")).toEqual({
      textColor: "#f4f4f5",
      gridColor: "rgba(255,255,255,0.2)",
      paperBackgroundColor: "#101214",
      plotBackgroundColor: "#0c0f11"
    });
    expect(resolveGraphThemeStyle(graphStyle, "light")).toEqual({
      textColor: "#111827",
      gridColor: "rgba(15, 23, 42, 0.15)",
      paperBackgroundColor: undefined,
      plotBackgroundColor: undefined
    });
  });

  it("resolves graph heights from style and falls back when invalid", () => {
    expect(resolveGraphHeight({height: 320})).toBe("320px");
    expect(resolveGraphHeight({height: 0})).toBe("18rem");
    expect(resolveGraphHeight(null)).toBe("18rem");
    expect(resolveGraphHeight({height: "320"})).toBe("18rem");
  });

  it("applies themed colors to layout while preserving existing axis settings", () => {
    const graphStyle = {
      theme: {
        dark: {
          textColor: "#eceff4",
          gridColor: "rgba(203, 213, 225, 0.25)",
          paperBackgroundColor: "#111827",
          plotBackgroundColor: "#0f172a"
        }
      }
    };

    const layout = {
      title: "Samples by Facility",
      legend: {orientation: "h"},
      xaxis: {
        title: "Facility",
        gridcolor: "#123456"
      },
      yaxis: {
        title: {text: "Count"},
        tickfont: {size: 10}
      },
      annotations: [
        {text: "Peak", font: {size: 14, color: "#000000"}},
        {text: "Normal"}
      ],
      margin: {l: 10, r: 10, t: 10, b: 10}
    };

    const themedLayout = resolveThemedGraphLayout(layout, graphStyle, "dark");

    expect(themedLayout.font).toMatchObject({color: "#eceff4"});
    expect(themedLayout.title).toMatchObject({
      text: "Samples by Facility",
      font: {color: "#eceff4"}
    });
    expect(themedLayout.legend).toMatchObject({
      orientation: "h",
      font: {color: "#eceff4"}
    });
    expect(themedLayout.xaxis).toMatchObject({
      color: "#eceff4",
      gridcolor: "#123456",
      zerolinecolor: "rgba(203, 213, 225, 0.25)",
      linecolor: "rgba(203, 213, 225, 0.25)"
    });
    expect(themedLayout.yaxis).toMatchObject({
      color: "#eceff4",
      gridcolor: "rgba(203, 213, 225, 0.25)",
      tickfont: {size: 10, color: "#eceff4"}
    });
    expect(themedLayout.annotations).toMatchObject([
      {text: "Peak", font: {size: 14, color: "#eceff4"}},
      {text: "Normal", font: {color: "#eceff4"}}
    ]);
    expect(themedLayout.paper_bgcolor).toBe("#111827");
    expect(themedLayout.plot_bgcolor).toBe("#0f172a");
  });

  it("does not inject undefined axis/title keys for donut KPI layouts", () => {
    const donutLayout = {
      margin: {b: 10, l: 10, r: 10, t: 10},
      showlegend: false,
      annotations: [
        {
          x: 0.5,
          y: 0.5,
          text: "<b>2,307</b>",
          xref: "paper",
          yref: "paper",
          showarrow: false,
          font: {size: 22}
        }
      ]
    };

    const themedLayout = resolveThemedGraphLayout(donutLayout, {}, "dark");

    expect("title" in themedLayout).toBe(false);
    expect("xaxis" in themedLayout).toBe(false);
    expect("yaxis" in themedLayout).toBe(false);
    expect(themedLayout.annotations).toMatchObject([
      {
        text: "<b>2,307</b>",
        font: {size: 22, color: "#e5e7eb"}
      }
    ]);
  });
});
