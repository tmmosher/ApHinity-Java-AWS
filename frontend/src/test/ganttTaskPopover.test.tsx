import {renderToString} from "solid-js/web";
import {describe, expect, it, vi} from "vitest";
import {formatDisplayDate} from "../util/location/dateUtility";

vi.mock("corvu/popover", () => {
  const Popover = (props: {children?: unknown} & Record<string, unknown>) => <>{props.children}</>;
  Popover.Trigger = (props: {children?: unknown} & Record<string, unknown>) => <button {...props}>{props.children}</button>;
  Popover.Portal = (props: {children?: unknown}) => <>{props.children}</>;
  Popover.Content = (props: {children?: unknown} & Record<string, unknown>) => <div {...props}>{props.children}</div>;
  Popover.Label = (props: {children?: unknown}) => <div>{props.children}</div>;
  Popover.Description = (props: {children?: unknown}) => <div>{props.children}</div>;
  return {default: Popover};
});

import {GanttTaskPopover, requestGanttTaskEdit} from "../components/gantt/GanttTaskPopover";

const baseTask = {
  id: 42,
  title: "Maintenance Window",
  startDate: "2026-04-01",
  endDate: "2026-04-10",
  description: "Planned outage",
  createdAt: "2026-03-01T00:00:00Z",
  updatedAt: "2026-03-02T00:00:00Z"
};

describe("GanttTaskPopover", () => {
  it("renders an information-first popover with an edit action for privileged users", () => {
    const html = renderToString(() => GanttTaskPopover({
      task: baseTask,
      canEdit: true,
      onSave: async () => undefined,
      onDelete: async () => undefined,
      onClose: () => undefined
    }));

    expect(html).toContain('data-gantt-task-popover');
    expect(html).toContain('data-gantt-task-edit');
    expect(html).toContain("Maintenance Window");
    expect(html).toContain(formatDisplayDate("2026-04-01"));
    expect(html).toContain(formatDisplayDate("2026-04-10"));
    expect(html).toContain("Planned outage");
  });

  it("hides the edit action when the viewer cannot edit gantt tasks", () => {
    const html = renderToString(() => GanttTaskPopover({
      task: baseTask,
      canEdit: false,
      onSave: async () => undefined,
      onDelete: async () => undefined,
      onClose: () => undefined
    }));

    expect(html).not.toContain('data-gantt-task-edit');
  });

  it("switches the popover into edit mode when requested", () => {
    const setIsEditing = vi.fn();

    requestGanttTaskEdit(setIsEditing);

    expect(setIsEditing).toHaveBeenCalledWith(true);
  });
});
