import {beforeEach, describe, expect, it, vi} from "vitest";
import {apiFetch} from "../util/common/apiFetch";
import {
  createLocationEventById,
  fetchLocationEventsById,
  updateLocationEventById
} from "../util/location/locationEventApi";
import {formatLocationEventMonth} from "../util/location/dateUtility";

vi.mock("../util/common/apiFetch", () => ({
  apiFetch: vi.fn()
}));

const createMockResponse = (ok: boolean, payload: unknown, status = 200): Response =>
  ({
    ok,
    status,
    json: vi.fn().mockResolvedValue(payload)
  }) as unknown as Response;

describe("locationEventApi", () => {
  const host = "https://example.test";
  const apiFetchMock = vi.mocked(apiFetch);

  beforeEach(() => {
    apiFetchMock.mockReset();
  });

  it("formats calendar months for the event query", () => {
    expect(formatLocationEventMonth(new Date("2026-04-18T14:00:00"))).toBe("2026-04");
  });

  it("loads service events for a requested calendar month", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(true, [
      {
        id: 8,
        title: "Client kickoff",
        responsibility: "client",
        date: "2026-03-28",
        time: "09:00",
        endDate: "2026-03-28",
        endTime: "11:30",
        description: "Initial kickoff meeting",
        status: "upcoming",
        createdAt: "2026-03-25T00:00:00Z",
        updatedAt: "2026-03-25T00:00:00Z"
      }
    ]));

    const events = await fetchLocationEventsById(host, "42", "2026-04");

    expect(apiFetchMock).toHaveBeenCalledWith(host + "/api/core/locations/42/events?month=2026-04", {
      method: "GET"
    });
    expect(events).toEqual([
      {
        id: 8,
        title: "Client kickoff",
        responsibility: "client",
        date: "2026-03-28",
        time: "09:00",
        endDate: "2026-03-28",
        endTime: "11:30",
        description: "Initial kickoff meeting",
        status: "upcoming",
        createdAt: "2026-03-25T00:00:00Z",
        updatedAt: "2026-03-25T00:00:00Z"
      }
    ]);
  });

  it("rejects invalid service event months before dispatch", async () => {
    await expect(fetchLocationEventsById(host, "42", "2026/04")).rejects.toThrowError("Invalid service event month");

    expect(apiFetchMock).not.toHaveBeenCalled();
  });

  it("posts a service-event create request and parses the response", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(true, {
      id: 8,
      title: "Client kickoff",
      responsibility: "client",
      date: "2026-03-28",
      time: "09:00",
      endDate: "2026-03-28",
      endTime: "11:30",
      description: "Initial kickoff meeting",
      status: "upcoming",
      createdAt: "2026-03-25T00:00:00Z",
      updatedAt: "2026-03-25T00:00:00Z"
    }));

    const event = await createLocationEventById(host, "42", {
      title: "Client kickoff",
      responsibility: "client",
      date: "2026-03-28",
      time: "09:00",
      endDate: "2026-03-28",
      endTime: "11:30",
      description: "Initial kickoff meeting",
      status: "upcoming"
    });

    expect(apiFetchMock).toHaveBeenCalledWith(host + "/api/core/locations/42/events", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        title: "Client kickoff",
        responsibility: "client",
        date: "2026-03-28",
        time: "09:00",
        endDate: "2026-03-28",
        endTime: "11:30",
        description: "Initial kickoff meeting",
        status: "upcoming"
      })
    });
    expect(event).toEqual({
      id: 8,
      title: "Client kickoff",
      responsibility: "client",
      date: "2026-03-28",
      time: "09:00",
      endDate: "2026-03-28",
      endTime: "11:30",
      description: "Initial kickoff meeting",
      status: "upcoming",
      createdAt: "2026-03-25T00:00:00Z",
      updatedAt: "2026-03-25T00:00:00Z"
    });
  });

  it("puts a service-event update request and parses the response", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(true, {
      id: 8,
      title: "Updated kickoff",
      responsibility: "client",
      date: "2026-03-29",
      time: "10:00",
      endDate: "2026-03-29",
      endTime: "12:00",
      description: "Updated details",
      status: "current",
      createdAt: "2026-03-25T00:00:00Z",
      updatedAt: "2026-03-26T00:00:00Z"
    }));

    const event = await updateLocationEventById(host, "42", 8, {
      title: "Updated kickoff",
      responsibility: "client",
      date: "2026-03-29",
      time: "10:00",
      endDate: "2026-03-29",
      endTime: "12:00",
      description: "Updated details",
      status: "current"
    });

    expect(apiFetchMock).toHaveBeenCalledWith(host + "/api/core/locations/42/events/8", {
      method: "PUT",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        title: "Updated kickoff",
        responsibility: "client",
        date: "2026-03-29",
        time: "10:00",
        endDate: "2026-03-29",
        endTime: "12:00",
        description: "Updated details",
        status: "current"
      })
    });
    expect(event).toEqual({
      id: 8,
      title: "Updated kickoff",
      responsibility: "client",
      date: "2026-03-29",
      time: "10:00",
      endDate: "2026-03-29",
      endTime: "12:00",
      description: "Updated details",
      status: "current",
      createdAt: "2026-03-25T00:00:00Z",
      updatedAt: "2026-03-26T00:00:00Z"
    });
  });

  it("rejects invalid route location ids before dispatch", async () => {
    await expect(createLocationEventById(host, "0", {
      title: "Client kickoff",
      responsibility: "client",
      date: "2026-03-28",
      time: "09:00",
      endDate: "2026-03-28",
      endTime: "11:30",
      description: null,
      status: "upcoming"
    })).rejects.toThrowError("Invalid location id");

    expect(apiFetchMock).not.toHaveBeenCalled();
  });

  it("rejects invalid route event ids before update dispatch", async () => {
    await expect(updateLocationEventById(host, "42", 0, {
      title: "Client kickoff",
      responsibility: "client",
      date: "2026-03-28",
      time: "09:00",
      endDate: "2026-03-28",
      endTime: "11:30",
      description: null,
      status: "upcoming"
    })).rejects.toThrowError("Invalid event id");

    expect(apiFetchMock).not.toHaveBeenCalled();
  });

  it("throws the backend validation message when available", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(false, {
      message: "Event title is required"
    }, 400));

    await expect(createLocationEventById(host, "42", {
      title: "",
      responsibility: "client",
      date: "2026-03-28",
      time: "09:00",
      endDate: "2026-03-28",
      endTime: "11:30",
      description: null,
      status: "upcoming"
    })).rejects.toThrowError("Event title is required");
  });

  it("throws a permission error for explicit forbidden codes", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(false, {
      code: "forbidden",
      message: "Insufficient permissions"
    }, 403));

    await expect(createLocationEventById(host, "42", {
      title: "Partner kickoff",
      responsibility: "partner",
      date: "2026-03-28",
      time: "09:00",
      endDate: "2026-03-28",
      endTime: "11:30",
      description: null,
      status: "upcoming"
    })).rejects.toThrowError("Insufficient permissions");
  });

  it("throws a security-token error for generic Spring Security 403 payloads", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(false, {
      status: 403,
      error: "Forbidden",
      path: "/api/core/locations/42/events"
    }, 403));

    await expect(createLocationEventById(host, "42", {
      title: "Partner kickoff",
      responsibility: "partner",
      date: "2026-03-28",
      time: "09:00",
      endDate: "2026-03-28",
      endTime: "11:30",
      description: null,
      status: "upcoming"
    })).rejects.toThrowError("Security token rejected");
  });
});
