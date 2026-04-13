import {renderToString} from "solid-js/web";
import {describe, expect, it} from "vitest";
import {ServiceCalendarIntroPopoverContent} from "../components/service-editor/ServiceCalendarIntroPopover";

describe("ServiceCalendarIntroPopoverContent", () => {
  it("renders the service calendar instructions, legend, and template link", () => {
    const html = renderToString(() => (
      <ServiceCalendarIntroPopoverContent
        templateHref="https://example.test/api/core/locations/42/events/template"
      />
    ));

    expect(html).toContain("How Service Calendar Works");
    expect(html).toContain("View previous, current, and upcoming service events.");
    expect(html).toContain("Get a copy of the Excel template");
    expect(html).toContain("https://example.test/api/core/locations/42/events/template");
    expect(html).toContain("Legend");
    expect(html).toContain("Client");
    expect(html).toContain("Partner");
  });
});
