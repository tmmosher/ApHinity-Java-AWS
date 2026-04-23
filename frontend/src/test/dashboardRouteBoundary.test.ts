import {createComponent} from "solid-js";
import {renderToString} from "solid-js/web";
import {describe, expect, it, vi} from "vitest";
import {DashboardRouteBoundary} from "../components/common/DashboardRouteBoundary";

vi.mock("@solidjs/router", () => ({
  A: (props: {children?: unknown}) => props.children
}));

const ThrowingPanel = () => {
  throw new Error("Panel blew up");
};

describe("DashboardRouteBoundary", () => {
  it("renders a recovery fallback when the panel throws", () => {
    const html = renderToString(() =>
      createComponent(DashboardRouteBoundary, {
        title: "Location Dashboard",
        backHref: "/dashboard/locations",
        get children() {
          return createComponent(ThrowingPanel, {});
        }
      })
    );

    expect(html).toContain("Location Dashboard");
    expect(html).toContain("This panel hit an unexpected error.");
    expect(html).toContain("Panel blew up");
    expect(html).toContain("Retry panel");
    expect(html).toContain("Back");
  });
});
