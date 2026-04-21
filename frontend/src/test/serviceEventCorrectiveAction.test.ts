import {describe, expect, it} from "vitest";
import type {LocationServiceEvent} from "../types/Types";
import {
  canCreateCorrectiveActionForSourceEvent,
  isCorrectiveActionServiceEvent
} from "../util/location/serviceEventCorrectiveAction";

const baseEvent = (overrides?: Partial<LocationServiceEvent>): LocationServiceEvent => ({
  id: 8,
  title: "Client kickoff",
  responsibility: "client",
  date: "2026-04-07",
  time: "09:00:00",
  endDate: "2026-04-07",
  endTime: "11:30:00",
  description: "Initial kickoff meeting",
  status: "upcoming",
  createdAt: "2026-03-25T00:00:00Z",
  updatedAt: "2026-03-25T00:00:00Z",
  ...overrides
});

describe("serviceEventCorrectiveAction", () => {
  it("allows clients to create corrective actions for client-responsibility events", () => {
    expect(canCreateCorrectiveActionForSourceEvent("client", baseEvent({responsibility: "client"}))).toBe(true);
  });

  it("prevents clients from creating corrective actions for partner-responsibility events", () => {
    expect(canCreateCorrectiveActionForSourceEvent("client", baseEvent({responsibility: "partner"}))).toBe(false);
  });

  it("hides corrective-action events from corrective-action creation", () => {
    expect(
      canCreateCorrectiveActionForSourceEvent("partner", {
        ...baseEvent({responsibility: "partner"}),
        isCorrectiveAction: true,
        correctiveActionSourceEventId: 4,
        correctiveActionSourceEventTitle: "Monthly maintenance"
      })
    ).toBe(false);
    expect(isCorrectiveActionServiceEvent({
      ...baseEvent(),
      isCorrectiveAction: true,
      correctiveActionSourceEventId: 4,
      correctiveActionSourceEventTitle: "Monthly maintenance"
    })).toBe(true);
  });

  it("allows partners to create corrective actions for partner-responsibility events", () => {
    expect(canCreateCorrectiveActionForSourceEvent("partner", baseEvent({responsibility: "partner"}))).toBe(true);
  });
});
