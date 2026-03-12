import {createRoot} from "solid-js";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {
  createManagedUserSearchControl,
  getManagedUserEmptyStateMessage,
  getManagedUserPageRangeLabel,
  MANAGED_USER_SEARCH_DEBOUNCE_MS
} from "../util/common/managedUserSearchControl";

describe("managedUserSearchControl", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("debounces search updates and resets the current page after the timer expires", () => {
    createRoot((dispose) => {
      const control = createManagedUserSearchControl();

      control.setPage(2);
      control.updateSearchDraft("  ops-team  ");

      vi.advanceTimersByTime(MANAGED_USER_SEARCH_DEBOUNCE_MS - 1);
      expect(control.searchQuery()).toBe("");
      expect(control.page()).toBe(2);

      vi.advanceTimersByTime(1);
      expect(control.searchQuery()).toBe("ops-team");
      expect(control.page()).toBe(0);

      dispose();
    });
  });

  it("restarts the debounce window when additional keystrokes arrive", () => {
    createRoot((dispose) => {
      const control = createManagedUserSearchControl();

      control.updateSearchDraft("ops");
      vi.advanceTimersByTime(MANAGED_USER_SEARCH_DEBOUNCE_MS - 100);
      control.updateSearchDraft("ops-team");

      vi.advanceTimersByTime(MANAGED_USER_SEARCH_DEBOUNCE_MS - 1);
      expect(control.searchQuery()).toBe("");

      vi.advanceTimersByTime(1);
      expect(control.searchQuery()).toBe("ops-team");

      dispose();
    });
  });

  it("builds stable page-range and empty-state labels", () => {
    expect(getManagedUserPageRangeLabel(undefined, "")).toBe("No users");
    expect(getManagedUserPageRangeLabel(undefined, "ops")).toBe("No matching users");
    expect(getManagedUserPageRangeLabel({
      users: [{id: 1, name: "User", email: "user@example.com", role: "client", pendingDeletion: false}],
      page: 1,
      size: 12,
      totalElements: 13,
      totalPages: 2
    }, "ops")).toBe("Showing 13-13 of 13 matching users");
    expect(getManagedUserEmptyStateMessage("ops")).toBe("No users matched that email search.");
    expect(getManagedUserEmptyStateMessage("")).toBe("No users are available.");
  });
});
