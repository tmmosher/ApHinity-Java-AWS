import {renderToString} from "solid-js/web";
import {describe, expect, it, vi} from "vitest";
import LocationDashboardSectionTimeRangeControl from "../components/location/LocationDashboardSectionTimeRangeControl";

describe("LocationDashboardSectionTimeRangeControl", () => {
  it("renders an active folded corner without disabling the trigger", () => {
    const html = renderToString(() => (
      <LocationDashboardSectionTimeRangeControl
        monthRange={7}
        hasOverride
        loading={false}
        onApply={vi.fn()}
        onReset={vi.fn()}
      />
    ));

    expect(html).toContain("data-section-time-range-trigger");
    expect(html).toContain("bg-secondary");
    expect(html).not.toContain("cursor-not-allowed");
  });

  it("disables the folded corner while dashboard changes are pending", () => {
    const html = renderToString(() => (
      <LocationDashboardSectionTimeRangeControl
        monthRange={3}
        hasOverride={false}
        loading={false}
        disabledReason="Apply or undo pending dashboard changes."
        onApply={vi.fn()}
        onReset={vi.fn()}
      />
    ));

    expect(html).toContain("disabled");
    expect(html).toContain("Apply or undo pending dashboard changes.");
    expect(html).toContain("cursor-not-allowed");
  });
});
