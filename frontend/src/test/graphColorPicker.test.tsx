import {readFileSync} from "node:fs";
import {fileURLToPath} from "node:url";
import {createRoot, createSignal} from "solid-js";
import {renderToString} from "solid-js/web";
import {afterEach, describe, expect, it, vi} from "vitest";

let latestLibraryProps: Record<string, unknown> | null = null;
let latestPopoverProps: Record<string, unknown> | null = null;
let libraryRenderCount = 0;
let portalRenderCount = 0;

const createMockColor = (color: string, alpha = 1) => ({
  getChannelValue: vi.fn((channel: string) => channel === "alpha" ? alpha : 0),
  toFormat: vi.fn(() => createMockColor(color, alpha)),
  toString: vi.fn((format?: string) => {
    if (format === "hex") {
      return color;
    }
    if (format === "hexa") {
      return `${color}80`;
    }
    return color;
  })
});

vi.mock("@ark-ui/solid", () => {
  const renderChildren = (props: Record<string, unknown>) => props.children;
  const ColorPicker = {
    Root: (props: Record<string, unknown>) => {
      latestLibraryProps = props;
      libraryRenderCount += 1;
      return props.children;
    },
    HiddenInput: () => <input type="hidden" data-ark-color-picker-hidden-input="" />,
    Content: renderChildren,
    Area: renderChildren,
    AreaBackground: () => <div data-ark-color-picker-area-background="" />,
    AreaThumb: (props: Record<string, unknown>) => <div {...props} data-ark-color-picker-area-thumb="" />,
    ChannelSlider: renderChildren,
    ChannelSliderTrack: (props: Record<string, unknown>) => <div {...props} data-ark-color-picker-slider-track="" />,
    ChannelSliderThumb: (props: Record<string, unknown>) => <div {...props} data-ark-color-picker-slider-thumb="" />,
    TransparencyGrid: () => <div data-ark-color-picker-transparency-grid="" />,
    ChannelInput: (props: Record<string, unknown>) => <input {...props} data-ark-color-picker-channel-input="" />
  };

  return {
    ColorPicker,
    parseColor: (color: string) => createMockColor(color)
  };
});

vi.mock("corvu/popover", () => {
  const Popover = (props: Record<string, unknown>) => {
    latestPopoverProps = props;
    return props.children;
  };
  Popover.Trigger = (props: Record<string, unknown>) => props.children;
  Popover.Portal = (props: Record<string, unknown>) => {
    portalRenderCount += 1;
    return props.children;
  };
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
    portalRenderCount = 0;
  });

  it("configures the Ark color picker inline with rgba controls and the existing palette defaults", () => {
    const html = renderToString(() => (
      <GraphColorPicker
        value="#123456"
        colorOptions={COLOR_OPTIONS}
        onChange={vi.fn()}
      />
    ));

    expect(html).toContain("data-graph-color-picker");
    expect(latestLibraryProps?.format).toBe("rgba");
    expect(latestLibraryProps?.inline).toBe(true);
    expect(latestLibraryProps?.disabled).toBeUndefined();
    expect(latestPopoverProps).toMatchObject({
      placement: "bottom-start",
      trapFocus: false,
      restoreFocus: false,
      closeOnOutsideFocus: false
    });
    expect(portalRenderCount).toBe(1);
    expect(html).toContain("aria-label=\"Preset color\"");
    expect(html).toContain("value=\"#1f77b4\">Legacy Blue</option>");
    expect(html).toContain("value=\"#2ca02c\">Legacy Green</option>");
  });

  it("forwards opaque and transparent custom colors while suppressing disabled and unchanged updates", () => {
    const onChange = vi.fn();
    renderToString(() => (
      <GraphColorPicker
        value="#123456"
        colorOptions={COLOR_OPTIONS}
        onChange={onChange}
      />
    ));

    const enabledChange = latestLibraryProps?.onValueChange;
    expect(typeof enabledChange).toBe("function");
    (enabledChange as (details: {value: ReturnType<typeof createMockColor>}) => void)({
      value: createMockColor("#ABCDEF")
    });
    (enabledChange as (details: {value: ReturnType<typeof createMockColor>}) => void)({
      value: createMockColor("#123456")
    });
    (enabledChange as (details: {value: ReturnType<typeof createMockColor>}) => void)({
      value: createMockColor("#ABCDEF", 0.5)
    });
    expect(onChange).toHaveBeenCalledTimes(2);
    expect(onChange).toHaveBeenNthCalledWith(1, "#ABCDEF");
    expect(onChange).toHaveBeenNthCalledWith(2, "#ABCDEF80");

    renderToString(() => (
      <GraphColorPicker
        value="#123456"
        colorOptions={COLOR_OPTIONS}
        disabled
        onChange={onChange}
      />
    ));
    (latestLibraryProps?.onValueChange as (details: {value: ReturnType<typeof createMockColor>}) => void)({
      value: createMockColor("#FEDCBA")
    });
    expect(onChange).toHaveBeenCalledTimes(2);
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
      (latestLibraryProps?.onValueChange as (details: {value: ReturnType<typeof createMockColor>}) => void)({
        value: createMockColor("#ABCDEF")
      });
      expect(value()).toBe("#ABCDEF");
      expect(libraryRenderCount).toBe(1);
      dispose();
    });
  });

  it("styles the Ark picker controls inside the wrapper popover", () => {
    const css = readFileSync(cssPath, "utf8");

    expect(css).toContain(".graph-color-picker-area");
    expect(css).toContain(".graph-color-picker-area-background");
    expect(css).toContain("inset: 0;");
    expect(css).toContain(".graph-color-picker-slider");
    expect(css).toContain(".graph-color-picker-slider-track");
    expect(css).toContain(".graph-color-picker-thumb");
    expect(css).toContain("touch-action: none;");
    expect(css).not.toContain(".graph-editor-scroll-region:has");
  });
});
