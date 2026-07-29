import {renderToString} from "solid-js/web";
import {createSignal} from "solid-js";
import {describe, expect, it, vi} from "vitest";
import LocationDashboardSectionTimeRangeControl from "../components/location/LocationDashboardSectionTimeRangeControl";
import {DashboardTimeRangeProvider} from "../context/DashboardTimeRangeContext";

describe("LocationDashboardSectionTimeRangeControl", () => {
  it("renders an active folded corner without disabling the trigger", () => {
    const html = renderToString(() => {
      const hasOverride = createSignal(true);
      return (
        <DashboardTimeRangeProvider monthRange={() => 3}>
          <LocationDashboardSectionTimeRangeControl
            monthRange={() => 7}
            hasOverride={hasOverride}
            loading={false}
            onApply={vi.fn()}
            onReset={vi.fn()}
          />
        </DashboardTimeRangeProvider>
      );
    });

    expect(html).toContain("data-section-time-range-trigger");
    expect(html).toContain("bg-secondary");
    expect(html).not.toContain("cursor-not-allowed");
  });

  it("disables the folded corner while dashboard changes are pending", () => {
    const html = renderToString(() => {
      const hasOverride = createSignal(false);
      return (
        <DashboardTimeRangeProvider monthRange={() => 3}>
          <LocationDashboardSectionTimeRangeControl
            monthRange={() => 3}
            hasOverride={hasOverride}
            loading={false}
            disabledReason="Apply or undo pending dashboard changes."
            onApply={vi.fn()}
            onReset={vi.fn()}
          />
        </DashboardTimeRangeProvider>
      );
    });

    expect(html).toContain("disabled");
    expect(html).toContain("Apply or undo pending dashboard changes.");
    expect(html).toContain("cursor-not-allowed");
  });
});
