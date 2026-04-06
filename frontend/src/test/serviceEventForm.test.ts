import {describe, expect, it} from "vitest";
import {
  canCompleteLocationServiceEvent,
  canEditLocationServiceEvent,
  canChooseServiceEventResponsibility,
  createDefaultServiceEventDraft,
  createLocationServiceEventRequestFromDraft,
  createLocationServiceEventRequestFromEvent,
  createServiceEventDraftFromEvent
} from "../util/location/serviceEventForm";
import {formatDateInputValue} from "../util/location/dateUtility";

describe("serviceEventForm helpers", () => {
  it("formats date values for date inputs using local year-month-day", () => {
    expect(formatDateInputValue(new Date(2026, 2, 25, 9, 30))).toBe("2026-03-25");
  });

  it("defaults client users to client responsibility", () => {
    const draft = createDefaultServiceEventDraft("client", new Date(2026, 2, 25, 9, 30));

    expect(draft.responsibility).toBe("client");
    expect(draft.startDate).toBe("2026-03-25");
    expect(draft.endDate).toBe("2026-03-25");
    expect(draft.startTime).toBe("09:00");
    expect(draft.endTime).toBe("10:00");
    expect(draft.status).toBe("upcoming");
  });

  it("allows partners and admins to choose responsibility", () => {
    expect(canChooseServiceEventResponsibility("partner")).toBe(true);
    expect(canChooseServiceEventResponsibility("admin")).toBe(true);
    expect(canChooseServiceEventResponsibility("client")).toBe(false);
    expect(canChooseServiceEventResponsibility(undefined)).toBe(false);
  });

  it("matches the backend edit permission rules for service events", () => {
    expect(canEditLocationServiceEvent("partner", "partner")).toBe(true);
    expect(canEditLocationServiceEvent("admin", "partner")).toBe(true);
    expect(canEditLocationServiceEvent("client", "client")).toBe(true);
    expect(canEditLocationServiceEvent("client", "partner")).toBe(false);
  });

  it("only allows incomplete events to be marked complete by authorized roles", () => {
    expect(canCompleteLocationServiceEvent("partner", "partner", "upcoming")).toBe(true);
    expect(canCompleteLocationServiceEvent("admin", "partner", "current")).toBe(true);
    expect(canCompleteLocationServiceEvent("client", "client", "overdue")).toBe(true);
    expect(canCompleteLocationServiceEvent("client", "partner", "upcoming")).toBe(false);
    expect(canCompleteLocationServiceEvent("partner", "client", "completed")).toBe(false);
  });

  it("creates an editable draft from an existing service event", () => {
    const draft = createServiceEventDraftFromEvent({
      id: 12,
      title: "Partner inspection",
      responsibility: "partner",
      date: "2026-04-07",
      time: "13:00:00",
      endDate: "2026-04-08",
      endTime: "15:30:00",
      description: "Review site conditions",
      status: "current",
      createdAt: "2026-04-01T00:00:00Z",
      updatedAt: "2026-04-02T00:00:00Z"
    });

    expect(draft).toEqual({
      title: "Partner inspection",
      description: "Review site conditions",
      responsibility: "partner",
      scheduleMode: "timed",
      startDate: "2026-04-07",
      startTime: "13:00",
      endDate: "2026-04-08",
      endTime: "15:30",
      allDayStartDate: "2026-04-07",
      allDayEndDate: "2026-04-08",
      status: "current"
    });
  });

  it("builds a timed create request from the start date and time", () => {
    const request = createLocationServiceEventRequestFromDraft({
      title: "  On-site setup  ",
      description: "  Confirm hardware delivery.  ",
      responsibility: "partner",
      scheduleMode: "timed",
      startDate: "2026-03-27",
      startTime: "08:30",
      endDate: "2026-03-27",
      endTime: "11:00",
      allDayStartDate: "2026-03-28",
      allDayEndDate: "2026-03-29",
      status: "upcoming"
    }, "partner");

    expect(request).toEqual({
      title: "On-site setup",
      responsibility: "partner",
      date: "2026-03-27",
      time: "08:30",
      endDate: "2026-03-27",
      endTime: "11:00",
      description: "Confirm hardware delivery.",
      status: "upcoming"
    });
  });

  it("builds an update request from an existing service event", () => {
    const request = createLocationServiceEventRequestFromEvent({
      id: 21,
      title: "Quarterly review",
      responsibility: "client",
      date: "2026-04-01",
      time: "08:30:00",
      endDate: "2026-04-01",
      endTime: "11:00:00",
      description: "Review current metrics",
      status: "current",
      createdAt: "2026-03-01T00:00:00Z",
      updatedAt: "2026-03-02T00:00:00Z"
    });

    expect(request).toEqual({
      title: "Quarterly review",
      responsibility: "client",
      date: "2026-04-01",
      time: "08:30:00",
      endDate: "2026-04-01",
      endTime: "11:00:00",
      description: "Review current metrics",
      status: "current"
    });
  });

  it("builds an all-day create request with midnight time and client responsibility for clients", () => {
    const request = createLocationServiceEventRequestFromDraft({
      title: "Quarterly review",
      description: "",
      responsibility: "partner",
      scheduleMode: "all-day",
      startDate: "2026-03-27",
      startTime: "08:30",
      endDate: "2026-03-27",
      endTime: "11:00",
      allDayStartDate: "2026-04-01",
      allDayEndDate: "2026-04-02",
      status: "upcoming"
    }, "client");

    expect(request).toEqual({
      title: "Quarterly review",
      responsibility: "client",
      date: "2026-04-01",
      time: "00:00",
      endDate: "2026-04-02",
      endTime: "23:59:59",
      description: null,
      status: "upcoming"
    });
  });

  it("preserves an edited status in the request payload", () => {
    const request = createLocationServiceEventRequestFromDraft({
      title: "Quarterly review",
      description: "",
      responsibility: "client",
      scheduleMode: "timed",
      startDate: "2026-04-01",
      startTime: "08:30",
      endDate: "2026-04-01",
      endTime: "11:00",
      allDayStartDate: "2026-04-01",
      allDayEndDate: "2026-04-01",
      status: "completed"
    }, "client");

    expect(request.status).toBe("completed");
  });

  it("rejects blank titles", () => {
    expect(() => createLocationServiceEventRequestFromDraft({
      title: "   ",
      description: "",
      responsibility: "client",
      scheduleMode: "timed",
      startDate: "2026-03-27",
      startTime: "08:30",
      endDate: "2026-03-27",
      endTime: "11:00",
      allDayStartDate: "2026-04-01",
      allDayEndDate: "2026-04-02",
      status: "upcoming"
    }, "client")).toThrowError("Event title is required.");
  });

  it("rejects titles longer than 42 characters", () => {
    expect(() => createLocationServiceEventRequestFromDraft({
      title: "1234567890123456789012345678901234567890123",
      description: "",
      responsibility: "client",
      scheduleMode: "timed",
      startDate: "2026-03-27",
      startTime: "08:30",
      endDate: "2026-03-27",
      endTime: "11:00",
      allDayStartDate: "2026-04-01",
      allDayEndDate: "2026-04-02",
      status: "upcoming"
    }, "client")).toThrowError("Event title must be 42 characters or fewer.");
  });

  it("rejects timed ranges that end before they start", () => {
    expect(() => createLocationServiceEventRequestFromDraft({
      title: "Site visit",
      description: "",
      responsibility: "client",
      scheduleMode: "timed",
      startDate: "2026-03-27",
      startTime: "11:00",
      endDate: "2026-03-27",
      endTime: "10:00",
      allDayStartDate: "2026-04-01",
      allDayEndDate: "2026-04-02",
      status: "upcoming"
    }, "client")).toThrowError("End date and time must be after the start date and time.");
  });

  it("rejects all-day ranges that end before they start", () => {
    expect(() => createLocationServiceEventRequestFromDraft({
      title: "Site visit",
      description: "",
      responsibility: "client",
      scheduleMode: "all-day",
      startDate: "2026-03-27",
      startTime: "11:00",
      endDate: "2026-03-27",
      endTime: "10:00",
      allDayStartDate: "2026-04-03",
      allDayEndDate: "2026-04-02",
      status: "upcoming"
    }, "client")).toThrowError("End date must be on or after the start date.");
  });
});
