import {createRoot} from "solid-js";
import {describe, expect, it, vi} from "vitest";
import {
  createLocationReactiveSearchControl,
  LOCATION_REACTIVE_SEARCH_DEBOUNCE_MS
} from "../util/location/locationReactiveSearchControl";

describe("locationReactiveSearchControl", () => {
  it("debounces search query updates", () => {
    vi.useFakeTimers();
    try {
      createRoot((dispose) => {
        try {
          const control = createLocationReactiveSearchControl("", LOCATION_REACTIVE_SEARCH_DEBOUNCE_MS);

          control.updateSearchDraft("ops");
          expect(control.searchDraft()).toBe("ops");
          expect(control.searchQuery()).toBe("");

          vi.advanceTimersByTime(LOCATION_REACTIVE_SEARCH_DEBOUNCE_MS - 1);
          expect(control.searchQuery()).toBe("");

          vi.advanceTimersByTime(1);
          expect(control.searchQuery()).toBe("ops");
        } finally {
          dispose();
        }
      });
    } finally {
      vi.clearAllTimers();
      vi.useRealTimers();
    }
  });
});
