import {renderToString} from "solid-js/web";
import {describe, expect, it} from "vitest";
import {GraphLoadingPlaceholder} from "../components/graph/GraphLoadingPlaceholder";

describe("GraphLoadingPlaceholder", () => {
  it("renders a gray loading shimmer for dashboard graphs", () => {
    const html = renderToString(() => GraphLoadingPlaceholder({
      graphName: "Compliance Trend",
      height: "640px"
    }));

    expect(html).toContain("data-graph-loading-placeholder");
    expect(html).toContain("graph-loading-shine");
    expect(html).toContain("height:640px");
    expect(html).toContain('aria-label="Graph loading: Compliance Trend"');
    expect(html).toContain("aria-busy=\"true\"");
  });
});
