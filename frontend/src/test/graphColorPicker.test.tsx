import {readFileSync} from "node:fs";
import {fileURLToPath} from "node:url";
import {createRoot, createSignal} from "solid-js";
import {renderToString} from "solid-js/web";
import {afterEach, describe, expect, it, vi} from "vitest";

let latestLibraryProps: Record<string, unknown> | null = null;
let latestPopoverProps: Record<string, unknown> | null = null;
let libraryRenderCount = 0;

vi.mock("@thednp/solid-color-picker", () => ({
  DefaultColorPicker: (props: Record<string, unknown>) => {
    latestLibraryProps = props;
    libraryRenderCount += 1;
    return <div data-library-color-picker="" />;
  }
}));

vi.mock("corvu/popover", () => {
  const Popover = (props: Record<string, unknown>) => {
    latestPopoverProps = props;
    return props.children;
  };
  Popover.Trigger = (props: Record<string, unknown>) => props.children;
  Popover.Portal = (props: Record<string, unknown>) => props.children;
  Popover.Content = (props: Record<string, unknown>) => props.children;
  return {default: Popover};
});

import GraphColorPicker from "../components/graph-editor/GraphColorPicker";

const COLOR_OPTIONS = {
  "Legacy Blue": "#1f77b4",
  "Legacy Green": "#2ca02c"
};
const cssPath = fileURLToPath(new URL("../index.css", import.meta.url));

describe("GraphColorPicker", () => {
  afterEach(() => {
    latestLibraryProps = null;
    latestPopoverProps = null;
    libraryRenderCount = 0;
  });

  it("configures the solid color picker with hex output and the existing palette defaults", () => {
    const html = renderToString(() => (
      <GraphColorPicker
        value="#123456"
        colorOptions={COLOR_OPTIONS}
        onChange={vi.fn()}
      />
    ));

    expect(html).toContain("data-graph-color-picker");
    expect(latestLibraryProps).toMatchObject({
      value: "#123456",
      format: "hex",
      theme: "light",
      colorKeywords: []
    });
    expect(latestPopoverProps).toMatchObject({
      placement: "bottom-start",
      trapFocus: false,
      restoreFocus: false,
      closeOnOutsideFocus: false
    });
    expect(html).toContain("aria-label=\"Preset color\"");
    expect(html).toContain("value=\"#1f77b4\">Legacy Blue</option>");
    expect(html).toContain("value=\"#2ca02c\">Legacy Green</option>");
  });

  it("forwards custom colors while suppressing disabled and unchanged updates", () => {
    const onChange = vi.fn();
    renderToString(() => (
      <GraphColorPicker
        value="#123456"
        colorOptions={COLOR_OPTIONS}
        onChange={onChange}
      />
    ));

    const enabledChange = latestLibraryProps?.onChange;
    expect(typeof enabledChange).toBe("function");
    (enabledChange as (colorHex: string) => void)("#abcdef");
    (enabledChange as (colorHex: string) => void)("#123456");
    expect(onChange).toHaveBeenCalledOnce();
    expect(onChange).toHaveBeenCalledWith("#abcdef");

    renderToString(() => (
      <GraphColorPicker
        value="#123456"
        colorOptions={COLOR_OPTIONS}
        disabled
        onChange={onChange}
      />
    ));
    (latestLibraryProps?.onChange as (colorHex: string) => void)("#fedcba");
    expect(onChange).toHaveBeenCalledOnce();
  });

  it("stays mounted when the parent accepts an interactive color update", () => {
    createRoot((dispose) => {
      const [value, setValue] = createSignal("#123456");
      GraphColorPicker({
        get value() {
          return value();
        },
        colorOptions: COLOR_OPTIONS,
        onChange: setValue
      });

      expect(libraryRenderCount).toBe(1);
      (latestLibraryProps?.onChange as (colorHex: string) => void)("#abcdef");
      expect(value()).toBe("#abcdef");
      expect(libraryRenderCount).toBe(1);
      dispose();
    });
  });

  it("separates the preset dropdown from the floating picker menu", () => {
    const css = readFileSync(cssPath, "utf8");

    expect(css).toContain(
      ".graph-color-picker-popover-content .graph-color-picker .color-dropdown.picker"
    );
    expect(css).toContain("position: static;");
    expect(css).toContain(
      ".graph-color-picker-popover-content .graph-color-picker .menu-toggle"
    );
    expect(css).toContain(
      ".graph-color-picker-popover-content .graph-color-picker .color-dropdown.menu"
    );
    expect(css).toContain("display: none;");
    expect(css).not.toContain(".graph-editor-scroll-region:has");
  });
});
