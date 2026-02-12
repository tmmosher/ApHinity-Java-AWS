import {afterEach, beforeEach, describe, expect, it} from "vitest";
import {
  getFavoriteLocationId,
  getFavoriteLocationIdForSave,
  hasSelectableFavoriteLocation,
  setFavoriteLocationId
} from "../util/favoriteLocation";

const FAVORITE_LOCATION_KEY = "aphinity.favoriteLocationId";

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

describe("favoriteLocation", () => {
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

  it("stores and returns a normalized favorite location id", () => {
    setFavoriteLocationId(" 42 ");

    expect(getFavoriteLocationId()).toBe("42");
  });

  it("clears persisted favorite when a non-positive or invalid id is saved", () => {
    setFavoriteLocationId(7);
    expect(getFavoriteLocationId()).toBe("7");

    setFavoriteLocationId(0);
    expect(getFavoriteLocationId()).toBe("");

    setFavoriteLocationId("abc");
    expect(getFavoriteLocationId()).toBe("");
  });

  it("returns empty favorite id when persisted value is not numeric", () => {
    window.localStorage.setItem(FAVORITE_LOCATION_KEY, "invalid");

    expect(getFavoriteLocationId()).toBe("");
  });

  it("checks whether the selected id is one of the available locations", () => {
    const locations = [{id: 1}, {id: 2}, {id: 100}];

    expect(hasSelectableFavoriteLocation("2", locations)).toBe(true);
    expect(hasSelectableFavoriteLocation(" 100 ", locations)).toBe(true);
    expect(hasSelectableFavoriteLocation("3", locations)).toBe(false);
    expect(hasSelectableFavoriteLocation("", locations)).toBe(false);
  });

  it("returns the location id to save only when selection is valid", () => {
    const locations = [{id: 3}, {id: 5}];

    expect(getFavoriteLocationIdForSave("5", locations)).toBe(5);
    expect(getFavoriteLocationIdForSave("005", locations)).toBe(5);
    expect(getFavoriteLocationIdForSave("4", locations)).toBeNull();
    expect(getFavoriteLocationIdForSave("", locations)).toBeNull();
    expect(getFavoriteLocationIdForSave("3", undefined)).toBeNull();
  });

  it("is safe when window/localStorage is unavailable", () => {
    Reflect.deleteProperty(globalThis, "window");

    expect(getFavoriteLocationId()).toBe("");
    expect(() => setFavoriteLocationId(9)).not.toThrow();
  });
});
