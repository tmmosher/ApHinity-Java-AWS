import {renderToString} from "solid-js/web";
import {describe, expect, it} from "vitest";
import {createDefaultServiceEventDraft} from "../util/location/serviceEventForm";
import {ServiceEventEditorBody} from "../pages/authenticated/panels/location/ServiceEventEditor";

describe("ServiceEventEditor", () => {
  it("shows status controls when status editing is enabled", () => {
    const html = renderToString(() => ServiceEventEditorBody({
      draft: {
        ...createDefaultServiceEventDraft("partner", new Date("2026-04-07T00:00:00")),
        status: "current"
      },
      isSaving: false,
      allowStatusEditing: true,
      canChooseResponsibility: true,
      updateDraft: () => undefined,
      submissionError: "",
      saveLabel: "Save Changes",
      onCancel: () => undefined,
      onSubmit: () => undefined
    }));

    expect(html).toContain("Status");
    expect(html).toContain(">Upcoming<");
    expect(html).toContain(">Current<");
    expect(html).toContain(">Overdue<");
    expect(html).toContain(">Completed<");
  });

  it("keeps status controls out of the create-event editor body", () => {
    const html = renderToString(() => ServiceEventEditorBody({
      draft: createDefaultServiceEventDraft("partner", new Date("2026-04-07T00:00:00")),
      isSaving: false,
      allowStatusEditing: false,
      canChooseResponsibility: true,
      updateDraft: () => undefined,
      submissionError: "",
      saveLabel: "Save",
      onCancel: () => undefined,
      onSubmit: () => undefined
    }));

    expect(html).not.toContain("Status");
    expect(html).not.toContain(">Completed<");
  });
});
