import {afterEach, beforeEach, describe, expect, it} from "vitest";
import {
  getQuickAccessLocations,
  getRecentLocationIds,
  recordRecentLocationIdIfLoaded,
  recordRecentLocationId
} from "../util/common/recentLocation";

const RECENT_LOCATION_IDS_KEY = "aphinity.recentLocationIds";

const createMemoryStorage = (): Storage => {
  const storage = new Map<string, string>();
  return {
    get length() {
      return storage.size;
    },
    clear() {
      storage.clear();
    },
    getItem(key: string): string | null {
      return storage.has(key) ? storage.get(key)! : null;
    },
    key(index: number): string | null {
      return Array.from(storage.keys())[index] ?? null;
    },
    removeItem(key: string) {
      storage.delete(key);
    },
    setItem(key: string, value: string) {
      storage.set(String(key), String(value));
    }
  };
};

const setTestWindow = (storage: Storage) => {
  Object.defineProperty(globalThis, "window", {
    value: {
      localStorage: storage
    } as unknown as Window & typeof globalThis,
    configurable: true,
    writable: true
  });
};

describe("recentLocation", () => {
  let originalWindow: (Window & typeof globalThis) | undefined;

  beforeEach(() => {
    originalWindow = (globalThis as {window?: Window & typeof globalThis}).window;
    setTestWindow(createMemoryStorage());
  });

  afterEach(() => {
    if (originalWindow) {
      Object.defineProperty(globalThis, "window", {
        value: originalWindow,
        configurable: true,
        writable: true
      });
      return;
    }
    Reflect.deleteProperty(globalThis, "window");
  });

  it("stores the three most recent unique location ids", () => {
    recordRecentLocationId("1");
    recordRecentLocationId("2");
    recordRecentLocationId("3");
    recordRecentLocationId("2");
    recordRecentLocationId("4");

    expect(getRecentLocationIds()).toEqual(["4", "2", "3"]);
    expect(window.localStorage.getItem(RECENT_LOCATION_IDS_KEY)).toBe(JSON.stringify(["4", "2", "3"]));
  });

  it("prepends the favorite location before recent ids", () => {
    const locations = [
      {id: 1, name: "One"},
      {id: 2, name: "Two"},
      {id: 3, name: "Three"},
      {id: 4, name: "Four"}
    ];

    const quickAccessLocations = getQuickAccessLocations(locations, "3", ["4", "2", "1"]);

    expect(quickAccessLocations.map((location) => location.id)).toEqual([3, 4, 2, 1]);
  });

  it("skips missing ids while preserving recent order after the favorite", () => {
    const locations = [
      {id: 1, name: "One"},
      {id: 3, name: "Three"}
    ];

    const quickAccessLocations = getQuickAccessLocations(locations, "3", ["2", "1"]);

    expect(quickAccessLocations.map((location) => location.id)).toEqual([3, 1]);
  });

  it("is safe when window/localStorage is unavailable", () => {
    Reflect.deleteProperty(globalThis, "window");

    expect(getRecentLocationIds()).toEqual([]);
    expect(() => recordRecentLocationId(9)).not.toThrow();
  });

  it("records a location id only when a location has loaded", () => {
    recordRecentLocationIdIfLoaded({id: 11});
    recordRecentLocationIdIfLoaded(undefined);

    expect(getRecentLocationIds()).toEqual(["11"]);
  });
});
