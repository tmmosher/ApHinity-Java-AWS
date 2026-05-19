import {renderToString} from "solid-js/web";
import {describe, expect, it, vi} from "vitest";
import {LocationDashboardTimeRangeSelector} from "../components/location/LocationDashboardTimeRangeSelector";

describe("LocationDashboardTimeRangeSelector", () => {
  it("uses the shared tri-selector shell and marks the active range", () => {
    const html = renderToString(() => (
      <LocationDashboardTimeRangeSelector
        selectedRange={() => "threeMonths"}
        onSelectRange={vi.fn()}
      />
    ));

    expect(html).toContain("aria-label=\"Dashboard date range selector\"");
    expect(html).toContain("inline-grid grid-cols-3 divide-x divide-base-300");
    expect(html).toContain("Rolling quarter");
    expect(html).toContain("aria-pressed=\"true\"");
    expect(html).toContain("cursor-default");
  });
});
