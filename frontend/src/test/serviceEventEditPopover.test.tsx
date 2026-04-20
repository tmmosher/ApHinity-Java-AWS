import {renderToString} from "solid-js/web";
import {describe, expect, it, vi} from "vitest";

let lastPopoverProps: Record<string, unknown> | undefined;

vi.mock("corvu/popover", () => {
  const Popover = (props: {
    children: (api: {open: boolean; setOpen: (open: boolean) => void}) => unknown;
  } & Record<string, unknown>) => {
    lastPopoverProps = props;
    return props.children({open: true, setOpen: () => undefined});
  };
  Popover.Trigger = (props: {children?: unknown}) => props.children ?? null;
  Popover.Portal = (props: {children: unknown}) => props.children;
  Popover.Content = (props: {children: unknown}) => props.children;
  Popover.Label = (props: {children: unknown}) => props.children;
  Popover.Description = (props: {children: unknown}) => props.children;
  return {default: Popover};
});

import {ServiceEventEditPopover} from "../components/service-editor/ServiceEventEditPopover";

const baseEvent = {
  id: 8,
  title: "Client kickoff",
  responsibility: "client" as const,
  date: "2026-04-07",
  time: "09:00:00",
  endDate: "2026-04-07",
  endTime: "11:30:00",
  description: "Initial kickoff meeting",
  status: "upcoming" as const,
  createdAt: "2026-03-25T00:00:00Z",
  updatedAt: "2026-03-25T00:00:00Z"
};

describe("ServiceEventEditPopover", () => {
  it("uses fixed positioning and flips upward instead of shifting downward", () => {
    renderToString(() => ServiceEventEditPopover({
      event: baseEvent,
      canEdit: false,
      canComplete: true,
      role: "client",
      onComplete: async () => undefined,
      children: "Open"
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

  it("renders a mark-complete button for authorized incomplete events in detail view", () => {
    const html = renderToString(() => ServiceEventEditPopover({
      event: baseEvent,
      canEdit: false,
      canComplete: true,
      role: "client",
      onComplete: async () => undefined,
      children: "Open"
    }));

    expect(html).toContain("Mark Complete");
    expect(html).toContain("data-service-event-complete");
  });

  it("hides the mark-complete button when the event is already completed", () => {
    const html = renderToString(() => ServiceEventEditPopover({
      event: {
        ...baseEvent,
        status: "completed"
      },
      canEdit: false,
      canComplete: true,
      role: "partner",
      onComplete: async () => undefined,
      children: "Open"
    }));

    expect(html).not.toContain("data-service-event-complete");
  });

  it("hides the mark-complete button when the viewer lacks completion permission", () => {
    const html = renderToString(() => ServiceEventEditPopover({
      event: baseEvent,
      canEdit: false,
      canComplete: false,
      role: "client",
      onComplete: async () => undefined,
      children: "Open"
    }));

    expect(html).not.toContain("data-service-event-complete");
  });

  it("renders a corrective-action button when creation is available", () => {
    const html = renderToString(() => ServiceEventEditPopover({
      event: baseEvent,
      canEdit: false,
      canComplete: false,
      role: "partner",
      onCreateCorrectiveAction: async () => undefined,
      children: "Open"
    }));

    expect(html).toContain("Create Corrective Action");
    expect(html).toContain("data-service-event-create-corrective-action");
  });

  it("hides the corrective-action button for corrective-action events", () => {
    const html = renderToString(() => ServiceEventEditPopover({
      event: {
        ...baseEvent,
        isCorrectiveAction: true,
        correctiveActionSourceEventId: 4,
        correctiveActionSourceEventTitle: "Monthly maintenance"
      },
      canEdit: false,
      canComplete: false,
      role: "partner",
      onCreateCorrectiveAction: async () => undefined,
      children: "Open"
    }));

    expect(html).not.toContain("data-service-event-create-corrective-action");
    expect(html).toContain("Source Event");
    expect(html).toContain("Monthly maintenance");
  });

  it("keeps the title column constrained so action buttons stay visible", () => {
    const html = renderToString(() => ServiceEventEditPopover({
      event: {
        ...baseEvent,
        title: "This is a very long service event title that should not push the action buttons away"
      },
      canEdit: true,
      canComplete: false,
      role: "partner",
      onSave: async () => undefined,
      onCreateCorrectiveAction: async () => undefined,
      children: "Open"
    }));

    expect(html).toContain("data-service-event-create-corrective-action");
    expect(html).toContain("data-service-event-edit");
    expect(html).toContain("class=\"min-w-0 flex-1\"");
    expect(html).toContain("class=\"flex shrink-0 items-center gap-2\"");
  });
});
