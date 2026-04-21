import {renderToString} from "solid-js/web";
import {describe, expect, it, vi} from "vitest";

const loadedTasks = [
  {
    id: 1,
    title: "Alpha",
    startDate: "2026-04-01",
    endDate: "2026-04-02",
    description: null,
    createdAt: "2026-03-01T00:00:00Z",
    updatedAt: "2026-03-01T00:00:00Z",
    dependencyTaskIds: []
  },
  {
    id: 3,
    title: "Gamma",
    startDate: "2026-04-05",
    endDate: "2026-04-06",
    description: null,
    createdAt: "2026-03-01T00:00:00Z",
    updatedAt: "2026-03-01T00:00:00Z",
    dependencyTaskIds: []
  }
];

vi.mock("solid-js", async () => {
  const actual = await vi.importActual<typeof import("solid-js")>("solid-js");
  const createResource = () => {
    const accessor = Object.assign(() => loadedTasks, {
      loading: false,
      error: undefined
    });
    return [accessor, {refetch: vi.fn()}] as const;
  };

  return {
    ...actual,
    createResource
  };
});

vi.mock("corvu/dialog", () => {
  let dialogOpen = false;
  const Dialog = (props: {children?: unknown; open?: boolean; initialOpen?: boolean} & Record<string, unknown>) => {
    dialogOpen = props.open ?? props.initialOpen ?? false;
    return <>{props.children}</>;
  };
  Dialog.Portal = (props: {children?: unknown}) => (dialogOpen ? <>{props.children}</> : null);
  Dialog.Overlay = ({children: _children, ...props}: {children?: unknown} & Record<string, unknown>) => (
    <div {...props}>{_children}</div>
  );
  Dialog.Content = ({children: _children, ...props}: {children?: unknown} & Record<string, unknown>) => (
    <div {...props}>{_children}</div>
  );
  Dialog.Label = (props: {children?: unknown}) => <div>{props.children}</div>;
  Dialog.Description = (props: {children?: unknown}) => <div>{props.children}</div>;
  return {default: Dialog};
});

import {GanttTaskDependenciesDialog} from "../components/gantt/GanttTaskDependenciesDialog";

describe("GanttTaskDependenciesDialog", () => {
  it("renders the dependency trigger without opening the modal by default", () => {
    const html = renderToString(() => (
      <GanttTaskDependenciesDialog
        apiHost="https://example.test"
        locationId="42"
        currentTaskId={2}
        currentTaskTitle="Current task"
        selectedDependencyTaskIds={[3]}
        onApply={() => undefined}
      />
    ));

    expect(html).toContain('data-gantt-task-dependencies');
    expect(html).not.toContain("Search tasks");
    expect(html).not.toContain("Apply");
  });

  it("renders the dependency modal shell and selected dependencies", () => {
    const html = renderToString(() => (
      <GanttTaskDependenciesDialog
        apiHost="https://example.test"
        locationId="42"
        currentTaskId={2}
        currentTaskTitle="Current task"
        selectedDependencyTaskIds={[2, 3]}
        defaultOpen={true}
        onApply={() => undefined}
      />
    ));

    expect(html).toContain("Dependencies");
    expect(html).toContain('data-gantt-task-dependencies');
    expect(html).toContain("Search tasks");
    expect(html).toContain("Search by task title...");
    expect(html).toContain("Available tasks");
    expect(html).toContain("Selected dependencies");
    expect(html).toContain('data-gantt-task-dependency-selected');
    expect(html).toContain("Gamma");
    expect(html).not.toContain('data-task-id="2"');
    expect(html).toContain("Remove");
    expect(html).toContain("Apply");
  });
});
