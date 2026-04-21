import {renderToString} from "solid-js/web";
import {afterEach, describe, expect, it, vi} from "vitest";

vi.mock("@solidjs/router", () => ({
  A: (props: {
    class?: string;
    children?: JSX.Element;
    href?: string;
    "aria-label"?: string;
  }) => (
    <a class={props.class} href={props.href} aria-label={props["aria-label"]}>
      {props.children}
    </a>
  )
}));

import {ErrorPage, goBackInSiteHistory} from "../pages/landing/ErrorPage";
import {JSX} from "solid-js";

describe("ErrorPage", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("renders a button that traverses back in the site history", () => {
    const html = renderToString(() => ErrorPage());

    expect(html).toContain("Page Not Found");
    expect(html).toContain("The page you requested could not be found.");
    expect(html).toContain("data-error-page-go-back");
    expect(html).toContain("Go back to the previous page");
    expect(html).toContain("Back");
    expect(html).toContain("Main Page");
    expect(html).toContain("Login");
  });

  it("calls the browser history back action when requested", () => {
    const historyBack = vi.fn();
    vi.stubGlobal("window", {
      history: {
        back: historyBack
      }
    });

    goBackInSiteHistory();

    expect(historyBack).toHaveBeenCalledTimes(1);
  });
});
