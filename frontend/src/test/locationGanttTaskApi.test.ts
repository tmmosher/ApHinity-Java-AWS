import {beforeEach, describe, expect, it, vi} from "vitest";
import {apiFetch} from "../util/common/apiFetch";
import {
  createLocationGanttTaskById,
  createLocationGanttTasksBulkById,
  deleteLocationGanttTaskById,
  fetchLocationGanttTasksById,
  getLocationGanttTaskTemplateDownloadUrl,
  updateLocationGanttTaskById
} from "../util/location/locationGanttTaskApi";

vi.mock("../util/common/apiFetch", () => ({
  apiFetch: vi.fn()
}));

const createMockResponse = (ok: boolean, payload: unknown, status = 200): Response =>
  ({
    ok,
    status,
    json: vi.fn().mockResolvedValue(payload)
  }) as unknown as Response;

describe("locationGanttTaskApi", () => {
  const host = "https://example.test";
  const apiFetchMock = vi.mocked(apiFetch);

  beforeEach(() => {
    apiFetchMock.mockReset();
  });

  it("loads gantt tasks for a location with optional search filtering", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(true, [{
      id: 8,
      title: "OPS",
      startDate: "2026-04-01",
      endDate: "2026-04-03",
      description: "Operational update",
      createdAt: "2026-03-01T00:00:00Z",
      updatedAt: "2026-03-01T00:00:00Z"
    }]));

    const tasks = await fetchLocationGanttTasksById(host, "42", "ops");

    expect(apiFetchMock).toHaveBeenCalledWith(
      host + "/api/core/locations/42/gantt-tasks?search=ops",
      {method: "GET"}
    );
    expect(tasks[0].title).toBe("OPS");
  });

  it("builds the gantt chart template download URL", () => {
    expect(getLocationGanttTaskTemplateDownloadUrl(host, "42"))
      .toBe(host + "/api/core/locations/42/gantt-tasks/template");
  });

  it("creates a single gantt task", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(true, {
      id: 9,
      title: "QMS",
      startDate: "2026-04-02",
      endDate: "2026-04-04",
      description: null,
      createdAt: "2026-03-01T00:00:00Z",
      updatedAt: "2026-03-01T00:00:00Z"
    }, 201));

    const created = await createLocationGanttTaskById(host, "42", {
      title: "QMS",
      startDate: "2026-04-02",
      endDate: "2026-04-04",
      description: null
    });

    expect(apiFetchMock).toHaveBeenCalledWith(host + "/api/core/locations/42/gantt-tasks", {
      method: "POST",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify({
        title: "QMS",
        startDate: "2026-04-02",
        endDate: "2026-04-04",
        description: null
      })
    });
    expect(created.id).toBe(9);
  });

  it("creates gantt tasks in bulk through the bulk endpoint", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(true, [
      {
        id: 10,
        title: "OPS",
        startDate: "2026-04-01",
        endDate: "2026-04-03",
        description: null,
        createdAt: "2026-03-01T00:00:00Z",
        updatedAt: "2026-03-01T00:00:00Z"
      },
      {
        id: 11,
        title: "QMS",
        startDate: "2026-04-05",
        endDate: "2026-04-08",
        description: "Validation",
        createdAt: "2026-03-01T00:00:00Z",
        updatedAt: "2026-03-01T00:00:00Z"
      }
    ], 201));

    const requests = [
      {
        title: "OPS",
        startDate: "2026-04-01",
        endDate: "2026-04-03",
        description: null
      },
      {
        title: "QMS",
        startDate: "2026-04-05",
        endDate: "2026-04-08",
        description: "Validation"
      }
    ] as const;

    const created = await createLocationGanttTasksBulkById(host, "42", requests);

    expect(apiFetchMock).toHaveBeenCalledWith(host + "/api/core/locations/42/gantt-tasks/bulk", {
      method: "POST",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify(requests)
    });
    expect(created).toHaveLength(2);
  });

  it("skips dispatch for empty bulk payloads", async () => {
    const created = await createLocationGanttTasksBulkById(host, "42", []);
    expect(apiFetchMock).not.toHaveBeenCalled();
    expect(created).toEqual([]);
  });

  it("updates and deletes persisted gantt tasks", async () => {
    apiFetchMock
      .mockResolvedValueOnce(createMockResponse(true, {
        id: 8,
        title: "OPS Updated",
        startDate: "2026-04-02",
        endDate: "2026-04-04",
        description: "Updated",
        createdAt: "2026-03-01T00:00:00Z",
        updatedAt: "2026-03-02T00:00:00Z"
      }))
      .mockResolvedValueOnce(createMockResponse(true, null, 204));

    const updated = await updateLocationGanttTaskById(host, "42", 8, {
      title: "OPS Updated",
      startDate: "2026-04-02",
      endDate: "2026-04-04",
      description: "Updated"
    });
    await deleteLocationGanttTaskById(host, "42", 8);

    expect(updated.title).toBe("OPS Updated");
    expect(apiFetchMock).toHaveBeenNthCalledWith(1, host + "/api/core/locations/42/gantt-tasks/8", {
      method: "PUT",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify({
        title: "OPS Updated",
        startDate: "2026-04-02",
        endDate: "2026-04-04",
        description: "Updated"
      })
    });
    expect(apiFetchMock).toHaveBeenNthCalledWith(2, host + "/api/core/locations/42/gantt-tasks/8", {
      method: "DELETE"
    });
  });

  it("rejects invalid route ids before network calls", async () => {
    await expect(fetchLocationGanttTasksById(host, "0")).rejects.toThrowError("Invalid location id");
    await expect(updateLocationGanttTaskById(host, "42", 0, {
      title: "OPS",
      startDate: "2026-04-01",
      endDate: "2026-04-02",
      description: null
    })).rejects.toThrowError("Invalid task id");
    expect(apiFetchMock).not.toHaveBeenCalled();
  });
});
