import {createRenderEffect, createRoot} from "solid-js";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {LocationDetailShell} from "../pages/authenticated/panels/location/LocationDetailShell";

const RECENT_LOCATION_IDS_KEY = "aphinity.recentLocationIds";

vi.mock("@solidjs/router", () => ({
  A: (props: {children?: unknown; href: string}) => <a href={props.href}>{props.children}</a>
}));

vi.mock("../context/ApiHostContext", () => ({
  useApiHost: () => "https://example.test"
}));

vi.mock("../util/graph/locationDetailApi", () => ({
  fetchLocationById: vi.fn(async () => ({
    id: 55,
    name: "Austin",
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-02T00:00:00Z",
    sectionLayout: {sections: []}
  })),
  fetchLocationGraphsById: vi.fn(async () => [])
}));

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

const flush = async () => {
  await Promise.resolve();
  await Promise.resolve();
  await Promise.resolve();
};

describe("LocationDetailShell recent location recording", () => {
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

  it("records a location id only after the location has loaded successfully", async () => {
    await createRoot(async (dispose) => {
      try {
        createRenderEffect(() => {
          LocationDetailShell({locationId: "55", currentView: "dashboard"});
        });
        await flush();
      } finally {
        dispose();
      }
    });

    expect(window.localStorage.getItem(RECENT_LOCATION_IDS_KEY)).toBe(JSON.stringify(["55"]));
  });

  it("does not record a location id when the location fails to load", async () => {
    const {fetchLocationById} = await import("../util/graph/locationDetailApi");
    vi.mocked(fetchLocationById).mockRejectedValueOnce(new Error("Unable to load location"));

    await createRoot(async (dispose) => {
      try {
        createRenderEffect(() => {
          LocationDetailShell({locationId: "99", currentView: "dashboard"});
        });
        await flush();
      } finally {
        dispose();
      }
    });

    expect(window.localStorage.getItem(RECENT_LOCATION_IDS_KEY)).toBeNull();
  });
});
