import {describe, expect, it} from "vitest";
import {
  canChooseServiceEventResponsibility,
  createDefaultServiceEventDraft,
  createLocationServiceEventRequestFromDraft,
  formatDateInputValue
} from "../util/location/serviceEventForm";

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
  });

  it("allows partners and admins to choose responsibility", () => {
    expect(canChooseServiceEventResponsibility("partner")).toBe(true);
    expect(canChooseServiceEventResponsibility("admin")).toBe(true);
    expect(canChooseServiceEventResponsibility("client")).toBe(false);
    expect(canChooseServiceEventResponsibility(undefined)).toBe(false);
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
      allDayEndDate: "2026-03-29"
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
      allDayEndDate: "2026-04-02"
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
      allDayEndDate: "2026-04-02"
    }, "client")).toThrowError("Event title is required.");
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
      allDayEndDate: "2026-04-02"
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
      allDayEndDate: "2026-04-02"
    }, "client")).toThrowError("End date must be on or after the start date.");
  });
});
