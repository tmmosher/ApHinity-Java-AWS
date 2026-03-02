import {beforeEach, describe, expect, it, vi} from "vitest";
import {apiFetch} from "../util/apiFetch";
import {
  createLocationInvite,
  fetchActiveInvites,
  fetchInviteableLocations,
  processLocationInvite
} from "../util/inviteApi";

vi.mock("../util/apiFetch", () => ({
  apiFetch: vi.fn()
}));

const createMockResponse = (ok: boolean, payload: unknown): Response =>
  ({
    ok,
    json: vi.fn().mockResolvedValue(payload)
  }) as unknown as Response;

describe("inviteApi", () => {
  const host = "https://example.test";
  const apiFetchMock = vi.mocked(apiFetch);

  beforeEach(() => {
    apiFetchMock.mockReset();
  });

  it("requests inviteable locations", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(true, [
      {
        id: 12,
        name: "Dallas",
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-02T00:00:00Z",
        sectionLayout: {sections: []}
      }
    ]));

    const locations = await fetchInviteableLocations(host);

    expect(apiFetchMock).toHaveBeenCalledWith(host + "/api/core/location-invites/locations", {
      method: "GET"
    });
    expect(locations).toHaveLength(1);
    expect(locations[0].name).toBe("Dallas");
  });

  it("reports inviteable location load failures", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(false, {
      message: "Location access denied"
    }));

    await expect(fetchInviteableLocations(host))
      .rejects
      .toThrowError("Unable to load locations");
  });

  it("creates an invite with normalized email payload", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(true, {}));

    await createLocationInvite(host, 99, " Client@Example.com ");

    expect(apiFetchMock).toHaveBeenCalledWith(host + "/api/core/location-invites", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        locationId: 99,
        invitedEmail: "client@example.com"
      })
    });
  });

  it("rejects invalid invite request input before making API calls", async () => {
    await expect(createLocationInvite(host, 0, "client@example.com"))
      .rejects
      .toThrowError("Select a location first.");
    await expect(createLocationInvite(host, 99, "   "))
      .rejects
      .toThrowError("Invite email is required.");
    expect(apiFetchMock).not.toHaveBeenCalled();
  });

  it("surfaces API errors when invite creation fails", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(false, {
      message: "Invited account email is not verified"
    }));

    await expect(createLocationInvite(host, 99, "client@example.com"))
      .rejects
      .toThrowError("Invited account email is not verified");
  });

  it("uses fallback message when invite creation fails without API message", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(false, {}));

    await expect(createLocationInvite(host, 99, "client@example.com"))
      .rejects
      .toThrowError("Unable to create invite.");
  });

  it("loads active invites for the authenticated account email", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(true, [
      {
        id: 51,
        locationId: 9,
        locationName: "Austin",
        invitedEmail: "client@example.com",
        invitedByUserId: 3,
        status: "pending",
        expiresAt: "2026-02-01T00:00:00Z",
        createdAt: "2026-01-25T00:00:00Z",
        acceptedAt: null,
        acceptedUserId: null,
        revokedAt: null
      }
    ]));

    const invites = await fetchActiveInvites(host);

    expect(apiFetchMock).toHaveBeenCalledWith(host + "/api/core/location-invites/active", {
      method: "GET"
    });
    expect(invites).toHaveLength(1);
    expect(invites[0].locationName).toBe("Austin");
  });

  it("reports active invite load failures", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(false, {
      message: "Request failed"
    }));

    await expect(fetchActiveInvites(host))
      .rejects
      .toThrowError("Unable to load invites");
  });

  it("posts invite decisions and propagates failures", async () => {
    apiFetchMock.mockResolvedValueOnce(createMockResponse(true, {}));
    await processLocationInvite(host, 51, "accept");
    expect(apiFetchMock).toHaveBeenCalledWith(host + "/api/core/location-invites/51/accept", {
      method: "POST"
    });

    apiFetchMock.mockResolvedValueOnce(createMockResponse(false, {message: "Invite expired"}));
    await expect(processLocationInvite(host, 51, "decline"))
      .rejects
      .toThrowError("Invite expired");
  });

  it("uses fallback message when invite decisions fail without API message", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(false, {}));

    await expect(processLocationInvite(host, 51, "accept"))
      .rejects
      .toThrowError("Unable to update invite.");
  });
});
