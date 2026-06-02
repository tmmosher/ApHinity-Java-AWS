import {renderToString} from "solid-js/web";
import {afterEach, describe, expect, it, vi} from "vitest";

let latestLibraryProps: Record<string, unknown> | null = null;

vi.mock("@thednp/solid-color-picker", () => ({
  DefaultColorPicker: (props: Record<string, unknown>) => {
    latestLibraryProps = props;
    return <div data-library-color-picker="" />;
  }
}));

import GraphColorPicker from "../components/graph-editor/GraphColorPicker";

const COLOR_OPTIONS = {
  "Legacy Blue": "#1f77b4",
  "Legacy Green": "#2ca02c"
};

describe("GraphColorPicker", () => {
  afterEach(() => {
    latestLibraryProps = null;
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
      colorKeywords: [
        {"Legacy Blue": "#1f77b4"},
        {"Legacy Green": "#2ca02c"}
      ]
    });
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
});
