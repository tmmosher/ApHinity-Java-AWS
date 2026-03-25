import type {ParentProps} from "solid-js";
import {renderToString} from "solid-js/web";
import {describe, expect, it, vi} from "vitest";
import {withDashboardRouteBoundary} from "../components/withDashboardRouteBoundary";

vi.mock("@solidjs/router", () => ({
  A: (props: {children?: unknown}) => props.children
}));

describe("withDashboardRouteBoundary", () => {
  it("forwards nested route children to wrapped panels", () => {
    const ParentPanel = (props: ParentProps) => props.children;
    const WrappedPanel = withDashboardRouteBoundary(ParentPanel, "Location Dashboard");

    const html = renderToString(() =>
      WrappedPanel({
        get children() {
          return "Child location panel";
        }
      })
    );

    expect(html).toContain("Child location panel");
  });
});
