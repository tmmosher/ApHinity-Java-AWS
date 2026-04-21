import {renderToString} from "solid-js/web";
import {describe, expect, it} from "vitest";
import {GanttTaskEditorBody, type UpdateGanttTaskDraftFn} from "../components/gantt/GanttTaskEditor";

describe("GanttTaskEditorBody", () => {
  it("renders a dependencies button next to the title", () => {
    const updateDraft: UpdateGanttTaskDraftFn = () => undefined;

    const html = renderToString(() => (
      <GanttTaskEditorBody
        draft={{
          title: "Maintenance Window",
          startDate: "2026-04-01",
          endDate: "2026-04-10",
          description: "Planned outage",
          dependencyTaskIds: []
        }}
        isSaving={false}
        saveLabel="Save"
        onCancel={() => undefined}
        onSubmit={() => undefined}
        dependencyDialogApiHost="https://example.test"
        dependencyDialogLocationId="42"
        dependencyDialogCurrentTaskTitle="Maintenance Window"
        updateDraft={updateDraft}
      />
    ));

    expect(html).toContain("Dependencies");
    expect(html).toContain("data-gantt-task-dependencies");
  });
});
