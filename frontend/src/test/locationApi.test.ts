import {beforeEach, describe, expect, it, vi} from "vitest";
import {apiFetch} from "../util/common/apiFetch";
import {createLocation, renameLocation} from "../util/common/locationApi";

vi.mock("../util/common/apiFetch", () => ({
  apiFetch: vi.fn()
}));

const createMockResponse = (ok: boolean, payload: unknown): Response =>
  ({
    ok,
    json: vi.fn().mockResolvedValue(payload)
  }) as unknown as Response;

describe("locationApi", () => {
  const host = "https://example.test";
  const apiFetchMock = vi.mocked(apiFetch);

  beforeEach(() => {
    apiFetchMock.mockReset();
  });

  it("creates a location with normalized name", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(true, {
      id: 12,
      name: "Phoenix",
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
      sectionLayout: {sections: []}
    }));

    const location = await createLocation(host, " Phoenix ");

    expect(apiFetchMock).toHaveBeenCalledWith(host + "/api/core/locations", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        name: "Phoenix"
      })
    });
    expect(location.name).toBe("Phoenix");
  });

  it("rejects blank location names before create requests", async () => {
    await expect(createLocation(host, "   "))
      .rejects
      .toThrowError("Location name is required");

    expect(apiFetchMock).not.toHaveBeenCalled();
  });

  it("surfaces API errors when location creation fails", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(false, {
      message: "Location name already in use"
    }));

    await expect(createLocation(host, "Phoenix"))
      .rejects
      .toThrowError("Location name already in use");
  });

  it("renames a location with normalized name", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(true, {
      id: 12,
      name: "Scottsdale",
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-02T00:00:00Z",
      sectionLayout: {sections: []}
    }));

    const location = await renameLocation(host, 12, " Scottsdale ");

    expect(apiFetchMock).toHaveBeenCalledWith(host + "/api/core/locations/12", {
      method: "PUT",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        name: "Scottsdale"
      })
    });
    expect(location.name).toBe("Scottsdale");
  });

  it("uses fallback message when rename fails without API details", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(false, {}));

    await expect(renameLocation(host, 12, "Mesa"))
      .rejects
      .toThrowError("Unable to update location name.");
  });
});
