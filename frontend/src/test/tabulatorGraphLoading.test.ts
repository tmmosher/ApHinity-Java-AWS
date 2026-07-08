import {describe, expect, it, vi} from "vitest";
import {
  registerTabulatorLoadHandlers,
  resolveTabulatorLoadErrorMessage,
  type TabulatorLoadEventTable
} from "../util/graph/tabulatorGraphLoading";

describe("tabulatorGraphLoading", () => {
  it("registers Tabulator loading and error hooks", () => {
    const listeners = new Map<string, (...args: unknown[]) => void>();
    const table: TabulatorLoadEventTable = {
      on: vi.fn((event: string, callback: (...args: unknown[]) => void) => {
        listeners.set(event, callback);
      })
    };
    const setLoading = vi.fn();
    const notifyError = vi.fn();

    registerTabulatorLoadHandlers(table, {setLoading, notifyError});

    expect(table.on).toHaveBeenCalledWith("dataLoading", expect.any(Function));
    expect(table.on).toHaveBeenCalledWith("dataLoaded", expect.any(Function));
    expect(table.on).toHaveBeenCalledWith("renderComplete", expect.any(Function));
    expect(table.on).toHaveBeenCalledWith("dataLoadError", expect.any(Function));

    listeners.get("dataLoading")?.();
    expect(setLoading).toHaveBeenLastCalledWith(true);

    listeners.get("dataLoaded")?.();
    expect(setLoading).toHaveBeenLastCalledWith(false);

    listeners.get("renderComplete")?.();
    expect(setLoading).toHaveBeenLastCalledWith(false);

    listeners.get("dataLoadError")?.(new Error("Page failed"));
    expect(setLoading).toHaveBeenLastCalledWith(false);
    expect(notifyError).toHaveBeenCalledWith("Page failed");
  });

  it("formats load errors from response-like statuses and unknown values", () => {
    expect(resolveTabulatorLoadErrorMessage({status: 503})).toBe("Unable to load table data (503)");
    expect(resolveTabulatorLoadErrorMessage(null)).toBe("Unable to load table data");
  });
});
