import {renderToString} from "solid-js/web";
import {describe, expect, it} from "vitest";
import GradientBorder from "../components/GradientBorder";

describe("GradientBorder", () => {
  it("renders active, focus-animation, and outline state hooks", () => {
    const html = renderToString(() => (
      <GradientBorder
        active
        animationKey={2}
        class="rounded-xl"
        frameClass="opacity-90"
        focusMode="animate-once"
        innerClass="bg-base-100"
      >
        <span>Navigation Item</span>
      </GradientBorder>
    ));

    expect(html).toMatch(/class="gradient-border rounded-xl ?"/);
    expect(html).toContain('data-active="true"');
    expect(html).toContain('data-animating="false"');
    expect(html).toContain('data-focus-mode="animate-once"');
    expect(html).toContain('class="gradient-border-frame opacity-90"');
    expect(html).toContain('class="gradient-border-surface bg-base-100"');
    expect(html).toContain("Navigation Item");
  });
});
