import {renderToString} from "solid-js/web";
import {describe, expect, it} from "vitest";
import ServiceScheduleCalendar from "../pages/authenticated/panels/location/ServiceScheduleCalendar";

describe("ServiceScheduleCalendar", () => {
  it("renders a full six-week calendar shell with navigation and interactive cell styling", () => {
    const html = renderToString(ServiceScheduleCalendar);

    expect(html).toContain("Service schedule calendar");
    expect(html).toContain("Go to previous month");
    expect(html).toContain("Go to next month");
    expect((html.match(/data-corvu-calendar-headcell/g) ?? [])).toHaveLength(7);
    expect((html.match(/data-corvu-calendar-celltrigger/g) ?? [])).toHaveLength(42);
    expect(html).toContain("hover:-translate-y-px");
    expect(html).toContain("active:translate-y-px");
  });
});
