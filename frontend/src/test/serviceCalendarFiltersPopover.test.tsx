import {renderToString} from "solid-js/web";
import {describe, expect, it, vi} from "vitest";
import ServiceCalendarFiltersPopover from "../pages/authenticated/panels/location/ServiceCalendarFiltersPopover";
import {createDefaultServiceCalendarFilters} from "../util/location/serviceCalendarFilters";

vi.mock("corvu/popover", () => {
  const Popover = (props: {children: unknown}) => props.children;
  Popover.Trigger = (props: Record<string, unknown>) => {
    const {children, ...rest} = props;
    return <button {...rest}>{children}</button>;
  };
  Popover.Portal = (props: {children: unknown}) => props.children;
  Popover.Content = (props: Record<string, unknown>) => {
    const {children, ...rest} = props;
    return <div {...rest}>{children}</div>;
  };
  Popover.Label = (props: Record<string, unknown>) => {
    const {children, ...rest} = props;
    return <p {...rest}>{children}</p>;
  };
  Popover.Description = (props: Record<string, unknown>) => {
    const {children, ...rest} = props;
    return <p {...rest}>{children}</p>;
  };
  return {default: Popover};
});

const markupNear = (html: string, marker: string): string => {
  const index = html.indexOf(marker);
  if (index === -1) {
    throw new Error(`Unable to find marker: ${marker}`);
  }
  return html.slice(Math.max(0, index - 120), index + marker.length + 120);
};

describe("ServiceCalendarFiltersPopover", () => {
  it("renders the filter menu with inactive controls disabled by default", () => {
    const html = renderToString(() => (
      <ServiceCalendarFiltersPopover
        filters={createDefaultServiceCalendarFilters()}
        activeFilterCount={0}
        onUpdate={() => undefined}
      />
    ));

    expect(html).toContain("Calendar Filters");
    expect(html).toContain("data-service-calendar-filter-trigger");
    expect(html).not.toContain("badge badge-primary");
    expect(markupNear(html, "data-service-calendar-filter-responsibility-value")).toContain("disabled");
    expect(markupNear(html, "data-service-calendar-filter-date-value")).toContain("disabled");
    expect(markupNear(html, "data-service-calendar-filter-status-value")).toContain("disabled");
  });

  it("shows the active count and enables configured filter controls", () => {
    const html = renderToString(() => (
      <ServiceCalendarFiltersPopover
        filters={{
          responsibility: {
            enabled: true,
            value: "client"
          },
          date: {
            enabled: true,
            value: "2026-04-08"
          },
          status: {
            enabled: true,
            value: "current"
          }
        }}
        activeFilterCount={3}
        onUpdate={() => undefined}
      />
    ));

    expect(html).toContain("badge badge-primary badge-sm\">3</span>");
    expect(markupNear(html, "data-service-calendar-filter-responsibility-toggle")).toContain("checked");
    expect(markupNear(html, "data-service-calendar-filter-date-toggle")).toContain("checked");
    expect(markupNear(html, "data-service-calendar-filter-status-toggle")).toContain("checked");
    expect(markupNear(html, "data-service-calendar-filter-responsibility-value")).not.toContain("disabled");
    expect(markupNear(html, "data-service-calendar-filter-date-value")).not.toContain("disabled");
    expect(markupNear(html, "data-service-calendar-filter-status-value")).not.toContain("disabled");
  });
});
