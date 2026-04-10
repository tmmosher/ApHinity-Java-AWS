import {describe, expect, it} from "vitest";
import {
  buildServiceCalendarRequestsFromStagedEvents,
  completeStagedServiceCalendarEvent,
  deleteStagedServiceCalendarEvent,
  editStagedServiceCalendarEvent,
  stageImportedServiceCalendarEvents,
  undoStagedServiceCalendarMutation
} from "../util/location/stagedServiceCalendar";

describe("stagedServiceCalendar", () => {
  it("stages imported requests and can undo the import", () => {
    const staged = stageImportedServiceCalendarEvents([], [], [
      {
        title: "Pump visit",
        description: "Inspect pump pressure",
        responsibility: "partner",
        date: "2026-04-14",
        time: "09:15:00",
        endDate: "2026-04-14",
        endTime: "11:45:00",
        status: "upcoming"
      }
    ]);

    expect(staged.nextEvents).toHaveLength(1);
    expect(staged.nextUndoStack).toHaveLength(1);
    expect(staged.nextEvents[0].id).toBeLessThan(0);

    const undone = undoStagedServiceCalendarMutation(staged.nextEvents, staged.nextUndoStack);
    expect(undone.nextEvents).toEqual([]);
    expect(undone.nextUndoStack).toEqual([]);
  });

  it("edits, completes, and deletes staged events", () => {
    const imported = stageImportedServiceCalendarEvents([], [], [
      {
        title: "Pump visit",
        description: "Inspect pump pressure",
        responsibility: "partner",
        date: "2026-04-14",
        time: "09:15:00",
        endDate: "2026-04-14",
        endTime: "11:45:00",
        status: "upcoming"
      }
    ]);
    const eventId = imported.nextEvents[0].id;

    const edited = editStagedServiceCalendarEvent(imported.nextEvents, imported.nextUndoStack, eventId, {
      title: "Updated visit",
      description: null,
      responsibility: "client",
      date: "2026-04-15",
      time: "10:00:00",
      endDate: "2026-04-15",
      endTime: "12:00:00",
      status: "upcoming"
    });
    expect(edited.nextEvents[0].title).toBe("Updated visit");
    expect(edited.nextEvents[0].responsibility).toBe("client");

    const completed = completeStagedServiceCalendarEvent(edited.nextEvents, edited.nextUndoStack, eventId);
    expect(completed.nextEvents[0].status).toBe("completed");
    expect(buildServiceCalendarRequestsFromStagedEvents(completed.nextEvents)).toEqual([
      {
        title: "Updated visit",
        description: null,
        responsibility: "client",
        date: "2026-04-15",
        time: "10:00:00",
        endDate: "2026-04-15",
        endTime: "12:00:00",
        status: "completed"
      }
    ]);

    const deleted = deleteStagedServiceCalendarEvent(completed.nextEvents, completed.nextUndoStack, eventId);
    expect(deleted.nextEvents).toEqual([]);
  });
});
