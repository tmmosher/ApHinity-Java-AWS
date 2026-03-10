import {beforeEach, describe, expect, it, vi} from "vitest";
import {apiFetch} from "../util/common/apiFetch";
import {fetchManagedUsers, updateManagedUserRole} from "../util/common/adminApi";

vi.mock("../util/common/apiFetch", () => ({
  apiFetch: vi.fn()
}));

const createMockResponse = (ok: boolean, payload: unknown): Response =>
  ({
    ok,
    json: vi.fn().mockResolvedValue(payload)
  }) as unknown as Response;

describe("adminApi", () => {
  const host = "https://example.test";
  const apiFetchMock = vi.mocked(apiFetch);

  beforeEach(() => {
    apiFetchMock.mockReset();
  });

  it("loads a page of managed users", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(true, {
      users: [
        {
          id: 4,
          name: "Admin User",
          email: "admin@example.com",
          role: "admin"
        }
      ],
      page: 1,
      size: 20,
      totalElements: 41,
      totalPages: 3
    }));

    const response = await fetchManagedUsers(host, 1, 20);

    expect(apiFetchMock).toHaveBeenCalledWith(host + "/api/core/admin/users?page=1&size=20", {
      method: "GET"
    });
    expect(response.users).toHaveLength(1);
    expect(response.users[0].email).toBe("admin@example.com");
    expect(response.totalPages).toBe(3);
  });

  it("uses API messages when managed user loading fails", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(false, {
      message: "Insufficient permissions"
    }));

    await expect(fetchManagedUsers(host, 0, 20))
      .rejects
      .toThrowError("Insufficient permissions");
  });

  it("rejects invalid managed user page payloads", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(true, {
      users: [
        {
          id: 4,
          email: "admin@example.com",
          role: "owner"
        }
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1
    }));

    await expect(fetchManagedUsers(host, 0, 20))
      .rejects
      .toThrowError("Invalid user role");
  });

  it("updates a managed user's role", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(true, {
      id: 9,
      name: "Partner User",
      email: "partner@example.com",
      role: "partner"
    }));

    const updatedUser = await updateManagedUserRole(host, 9, "partner");

    expect(apiFetchMock).toHaveBeenCalledWith(host + "/api/core/admin/users/9/role", {
      method: "PUT",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        role: "partner"
      })
    });
    expect(updatedUser.role).toBe("partner");
  });

  it("uses fallback message when role updates fail without API details", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(false, {}));

    await expect(updateManagedUserRole(host, 9, "client"))
      .rejects
      .toThrowError("Unable to update user role.");
  });
});
