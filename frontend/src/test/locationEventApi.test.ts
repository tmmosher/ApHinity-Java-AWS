import {beforeEach, describe, expect, it, vi} from "vitest";
import {apiFetch} from "../util/common/apiFetch";
import {createLocationEventById} from "../util/location/locationEventApi";

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
