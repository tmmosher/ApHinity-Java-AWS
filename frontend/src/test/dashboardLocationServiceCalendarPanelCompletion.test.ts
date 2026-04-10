import {beforeEach, describe, expect, it, vi} from "vitest";
import {renderToString} from "solid-js/web";
import type {LocationServiceEvent} from "../types/Types";

const mocks = vi.hoisted(() => ({
  currentRole: "partner" as "partner" | "client",
  latestCalendarProps: null as Record<string, unknown> | null,
  createLocationEventById: vi.fn(),
  fetchLocationEventsById: vi.fn(async () => []),
  uploadLocationEventCalendarById: vi.fn(),
  updateLocationEventById: vi.fn(async () => undefined),
  toastSuccess: vi.fn()
}));

vi.mock("@solidjs/router", () => ({
  useParams: () => ({locationId: "42"})
}));

vi.mock("../context/ApiHostContext", () => ({
  useApiHost: () => "https://example.test"
}));

vi.mock("../context/ProfileContext", () => ({
  useProfile: () => ({
    profile: () => ({role: mocks.currentRole})
  })
}));

vi.mock("solid-toast", () => ({
  toast: {
    success: mocks.toastSuccess
  }
}));

vi.mock("../util/location/locationEventApi", () => ({
  createLocationEventById: mocks.createLocationEventById,
  fetchLocationEventsById: mocks.fetchLocationEventsById,
  getLocationEventTemplateDownloadUrl: vi.fn((host: string, locationId: string) =>
    host + "/api/core/locations/" + locationId + "/events/template"
  ),
  uploadLocationEventCalendarById: mocks.uploadLocationEventCalendarById,
  updateLocationEventById: mocks.updateLocationEventById
}));

vi.mock("../pages/authenticated/panels/location/ServiceScheduleCalendar", () => ({
  default: (props: Record<string, unknown>) => {
    mocks.latestCalendarProps = props;
    return null;
  }
}));

import {LocationServiceCalendarPanel} from "../pages/authenticated/panels/location/LocationServiceCalendarPanel";

const createEvent = (overrides?: Partial<LocationServiceEvent>): LocationServiceEvent => ({
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

const renderPanel = () => {
  mocks.latestCalendarProps = null;
  renderToString(LocationServiceCalendarPanel);

  if (!mocks.latestCalendarProps) {
    throw new Error("ServiceScheduleCalendar was not rendered.");
  }

  return mocks.latestCalendarProps as {
    canCompleteEvent: (event: LocationServiceEvent) => boolean;
    onCompleteEvent: (event: LocationServiceEvent) => Promise<void>;
  };
};

describe("LocationServiceCalendarPanel completion wiring", () => {
  beforeEach(() => {
    mocks.currentRole = "partner";
    mocks.latestCalendarProps = null;
    mocks.createLocationEventById.mockReset();
    mocks.fetchLocationEventsById.mockReset();
    mocks.fetchLocationEventsById.mockResolvedValue([]);
    mocks.uploadLocationEventCalendarById.mockReset();
    mocks.updateLocationEventById.mockReset();
    mocks.updateLocationEventById.mockResolvedValue(undefined);
    mocks.toastSuccess.mockReset();
  });

  it("passes role-aware completion permissions to the calendar", () => {
    mocks.currentRole = "client";
    const props = renderPanel();

    expect(props.canCompleteEvent(createEvent({responsibility: "client", status: "upcoming"}))).toBe(true);
    expect(props.canCompleteEvent(createEvent({responsibility: "partner", status: "upcoming"}))).toBe(false);
    expect(props.canCompleteEvent(createEvent({responsibility: "client", status: "completed"}))).toBe(false);
  });

  it("marks an event complete by reusing the update endpoint with the existing event payload", async () => {
    const props = renderPanel();
    const event = createEvent({
      responsibility: "partner",
      description: null,
      status: "current"
    });

    await props.onCompleteEvent(event);

    expect(mocks.updateLocationEventById).toHaveBeenCalledWith("https://example.test", "42", 8, {
      title: "Client kickoff",
      responsibility: "partner",
      date: "2026-04-07",
      time: "09:00:00",
      endDate: "2026-04-07",
      endTime: "11:30:00",
      description: null,
      status: "completed"
    });
    expect(mocks.toastSuccess).toHaveBeenCalledWith("Service event marked complete.");
  });
});
