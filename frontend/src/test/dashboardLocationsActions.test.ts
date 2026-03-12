import {beforeEach, describe, expect, it, vi} from "vitest";
import {
  runCreateLocationAction,
  runRenameLocationAction,
  sortLocationsByName
} from "../pages/authenticated/panels/dashboardLocationsActions";
import {
  createLocation as createLocationRequest,
  renameLocation as renameLocationRequest
} from "../util/common/locationApi";

vi.mock("../util/common/locationApi", () => ({
  createLocation: vi.fn(),
  renameLocation: vi.fn()
}));

describe("dashboardLocationsActions", () => {
  const host = "https://example.test";
  const createLocationMock = vi.mocked(createLocationRequest);
  const renameLocationMock = vi.mocked(renameLocationRequest);

  beforeEach(() => {
    createLocationMock.mockReset();
    renameLocationMock.mockReset();
  });

  it("calls the rename API helper with host, location id, and name", async () => {
    renameLocationMock.mockResolvedValue({
      id: 12,
      name: "Scottsdale",
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-02T00:00:00Z",
      sectionLayout: {sections: []}
    });

    const result = await runRenameLocationAction(host, 12, "Scottsdale");

    expect(renameLocationMock).toHaveBeenCalledWith(host, 12, "Scottsdale");
    expect(result.ok).toBe(true);
    expect(result.updatedLocation?.name).toBe("Scottsdale");
  });

  it("maps rename API failures into action results", async () => {
    renameLocationMock.mockRejectedValue(new Error("Location name already in use"));

    const result = await runRenameLocationAction(host, 12, "Scottsdale");

    expect(result).toEqual({
      ok: false,
      locationId: 12,
      message: "Location name already in use"
    });
  });

  it("calls the create API helper with host and name", async () => {
    createLocationMock.mockResolvedValue({
      id: 14,
      name: "Phoenix",
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
      sectionLayout: {sections: []}
    });

    const result = await runCreateLocationAction(host, "Phoenix");

    expect(createLocationMock).toHaveBeenCalledWith(host, "Phoenix");
    expect(result.ok).toBe(true);
    expect(result.createdLocation?.name).toBe("Phoenix");
  });

  it("sorts locations by name case-insensitively", () => {
    const sorted = sortLocationsByName([
      {
        id: 2,
        name: "zeta",
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-01T00:00:00Z",
        sectionLayout: {sections: []}
      },
      {
        id: 1,
        name: "Alpha",
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-01T00:00:00Z",
        sectionLayout: {sections: []}
      }
    ]);

    expect(sorted.map((location) => location.name)).toEqual(["Alpha", "zeta"]);
  });
});
