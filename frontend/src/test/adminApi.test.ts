import {beforeEach, describe, expect, it, vi} from "vitest";
import {apiFetch} from "../util/common/apiFetch";
import {
  fetchManagedUsers,
  markManagedUserForDeletion,
  restoreManagedUserDeletion,
  updateManagedUserRole
} from "../util/common/adminApi";

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
          name: "Partner User",
          email: "partner@example.com",
          role: "partner",
          pendingDeletion: false
        }
      ],
      page: 1,
      size: 12,
      totalElements: 13,
      totalPages: 2
    }));

    const response = await fetchManagedUsers(host, 1, 12);

    expect(apiFetchMock).toHaveBeenCalledWith(host + "/api/core/admin/users?page=1&size=12", {
      method: "GET"
    });
    expect(response.users).toHaveLength(1);
    expect(response.users[0].email).toBe("partner@example.com");
    expect(response.users[0].pendingDeletion).toBe(false);
    expect(response.totalPages).toBe(2);
  });

  it("passes a trimmed search query through to the managed user request", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(true, {
      users: [],
      page: 0,
      size: 12,
      totalElements: 0,
      totalPages: 0
    }));

    await fetchManagedUsers(host, 0, 12, "  ops-team  ");

    expect(apiFetchMock).toHaveBeenCalledWith(
      host + "/api/core/admin/users?page=0&size=12&query=ops-team",
      {method: "GET"}
    );
  });

  it("uses API messages when managed user loading fails", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(false, {
      message: "Insufficient permissions"
    }));

    await expect(fetchManagedUsers(host, 0, 12))
      .rejects
      .toThrowError("Insufficient permissions");
  });

  it("rejects invalid managed user page payloads", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(true, {
      users: [
        {
          id: 4,
          email: "partner@example.com",
          role: "partner"
        }
      ],
      page: 0,
      size: 12,
      totalElements: 1,
      totalPages: 1
    }));

    await expect(fetchManagedUsers(host, 0, 12))
      .rejects
      .toThrowError("Invalid managed user shape");
  });

  it("updates a managed user's role", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(true, {
      id: 9,
      name: "Partner User",
      email: "partner@example.com",
      role: "partner",
      pendingDeletion: false
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

  it("queues a managed user for deletion", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(true, {
      id: 9,
      name: "Partner User",
      email: "partner@example.com",
      role: "partner",
      pendingDeletion: true
    }));

    const updatedUser = await markManagedUserForDeletion(host, 9);

    expect(apiFetchMock).toHaveBeenCalledWith(host + "/api/core/admin/users/9/deletion", {
      method: "PUT"
    });
    expect(updatedUser.pendingDeletion).toBe(true);
  });

  it("restores a managed user from deletion", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(true, {
      id: 9,
      name: "Partner User",
      email: "partner@example.com",
      role: "partner",
      pendingDeletion: false
    }));

    const updatedUser = await restoreManagedUserDeletion(host, 9);

    expect(apiFetchMock).toHaveBeenCalledWith(host + "/api/core/admin/users/9/deletion", {
      method: "DELETE"
    });
    expect(updatedUser.pendingDeletion).toBe(false);
  });

  it("uses fallback message when role updates fail without API details", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(false, {}));

    await expect(updateManagedUserRole(host, 9, "client"))
      .rejects
      .toThrowError("Unable to update user role.");
  });
});
