import {renderToString} from "solid-js/web";
import {describe, expect, it, vi} from "vitest";

let lastPopoverProps: Record<string, unknown> | undefined;

vi.mock("corvu/popover", () => {
  const Popover = (props: {children?: unknown} & Record<string, unknown>) => {
    lastPopoverProps = props;
    return <>{props.children}</>;
  };
  Popover.Trigger = (props: {children?: JSX.Element} & Record<string, unknown>) => <button {...props}>{props.children}</button>;
  Popover.Portal = (props: {children?: JSX.Element}) => <>{props.children}</>;
  Popover.Content = (props: {children?: JSX.Element} & Record<string, unknown>) => <div {...props}>{props.children}</div>;
  Popover.Label = (props: {children?: JSX.Element}) => <div>{props.children}</div>;
  Popover.Description = (props: {children?: JSX.Element}) => <div>{props.children}</div>;
  return {default: Popover};
});

import {GanttTaskCreatePopover} from "../components/gantt/GanttTaskCreatePopover";
import {JSX} from "solid-js";

describe("GanttTaskCreatePopover", () => {
  it("renders a trigger and create form with the expected task fields", () => {
    const html = renderToString(() => GanttTaskCreatePopover({
      apiHost: "https://example.test",
      locationId: "42",
      onCreate: async () => undefined
    }));

    expect(html).toContain("Add Task");
    expect(html).toContain("Add Gantt Task");
    expect(html).toContain("Title");
    expect(html).toContain("Start date");
    expect(html).toContain("End date");
    expect(html).toContain("Description");
    expect(html).toContain("Dependencies");
    expect(html).toContain("data-gantt-task-create-trigger");
    expect(html).toContain("data-gantt-task-create-popover");
    expect(html).toContain("data-gantt-task-dependencies");
  });

  it("uses fixed positioning and cross-axis shift-friendly popover defaults", () => {
    renderToString(() => GanttTaskCreatePopover({
      apiHost: "https://example.test",
      locationId: "42",
      onCreate: async () => undefined
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
