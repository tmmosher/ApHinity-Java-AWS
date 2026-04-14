import {renderToString} from "solid-js/web";
import {describe, expect, it, vi} from "vitest";

let lastPopoverProps: Record<string, unknown> | undefined;

vi.mock("corvu/popover", () => {
  const Popover = (props: {children: unknown} & Record<string, unknown>) => {
    lastPopoverProps = props;
    return props.children ?? null;
  };
  Popover.Trigger = (props: {children?: unknown}) => props.children ?? null;
  Popover.Portal = (props: {children: unknown}) => props.children;
  Popover.Content = (props: {children: unknown}) => props.children;
  Popover.Label = (props: {children: unknown}) => props.children;
  Popover.Description = (props: {children: unknown}) => props.children;
  return {default: Popover};
});

import {ServiceEventCreatePopover} from "../components/service-editor/ServiceEventCreatePopover";

describe("ServiceEventCreatePopover", () => {
  it("uses fixed positioning and flips upward instead of shifting downward", () => {
    renderToString(() => ServiceEventCreatePopover({
      day: new Date("2026-04-21T00:00:00"),
      style: {},
      class: "service-day",
      role: "partner",
      onSave: async () => undefined
    }));

    expect(lastPopoverProps?.strategy).toBe("fixed");
    expect(lastPopoverProps?.floatingOptions).toEqual({
      flip: true,
      shift: {
        mainAxis: false,
        crossAxis: true
      }
    });
  });
});
