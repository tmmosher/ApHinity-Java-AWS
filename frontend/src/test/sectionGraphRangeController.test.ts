import {createRoot, createSignal} from "solid-js";
import {describe, expect, it, vi} from "vitest";
import type {LocationGraph, LocationSectionLayout} from "../types/Types";
import {createSectionGraphRangeController} from "../util/location/createSectionGraphRangeController";

const baseGraph: LocationGraph = {
  id: 1,
  name: "Base",
  data: [],
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z"
};
const rangedGraph: LocationGraph = {...baseGraph, name: "Seven months"};
const section: LocationSectionLayout = {section_id: 4, graph_ids: [1]};

describe("section graph range controller", () => {
  it("keeps section projections separate and can return to the common slate", async () => {
    await new Promise<void>((resolve, reject) => createRoot((dispose) => {
      const [commonMonthRange] = createSignal(3);
      const fetchSectionGraphs = vi.fn(async () => ({
        sectionId: 4,
        monthRange: 7,
        graphs: [rangedGraph],
        missingGraphIds: [99]
      }));
      const controller = createSectionGraphRangeController({
        commonMonthRange,
        graphsForSection: () => [baseGraph],
        missingGraphIdsForSection: () => [],
        fetchSectionGraphs
      });

      void controller.apply(section, 7).then(() => {
        try {
          expect(fetchSectionGraphs).toHaveBeenCalledWith(4, 7, expect.any(AbortSignal));
          expect(controller.monthRange(section)).toBe(7);
          expect(controller.hasOverride(section)).toBe(true);
          expect(controller.graphs(section)[0].name).toBe("Seven months");
          expect(controller.missingGraphIds(section)).toEqual([99]);
          controller.reset(section);
          expect(controller.monthRange(section)).toBe(3);
          expect(controller.graphs(section)[0].name).toBe("Base");
          resolve();
        } catch (error) {
          reject(error);
        } finally {
          dispose();
        }
      });
    }));
  });

  it("aborts superseded requests and ignores their late results", async () => {
    await new Promise<void>((resolve, reject) => createRoot((dispose) => {
      const requests: Array<{
        monthRange: number;
        signal?: AbortSignal;
        resolve: (result: {sectionId: number; monthRange: number; graphs: LocationGraph[]; missingGraphIds: number[]}) => void;
      }> = [];
      const controller = createSectionGraphRangeController({
        commonMonthRange: () => 3,
        graphsForSection: () => [baseGraph],
        missingGraphIdsForSection: () => [],
        fetchSectionGraphs: vi.fn((_, monthRange, signal) => new Promise((requestResolve) => {
          requests.push({monthRange, signal, resolve: requestResolve});
        }))
      });

      const first = controller.apply(section, 7);
      const second = controller.apply(section, 9);
      try {
        expect(requests).toHaveLength(2);
        expect(requests[0].signal?.aborted).toBe(true);
        requests[1].resolve({sectionId: 4, monthRange: 9, graphs: [{...rangedGraph, name: "Nine months"}], missingGraphIds: []});
        void second.then(() => {
          requests[0].resolve({sectionId: 4, monthRange: 7, graphs: [rangedGraph], missingGraphIds: []});
          void first.then(() => {
            try {
              expect(controller.monthRange(section)).toBe(9);
              expect(controller.graphs(section)[0].name).toBe("Nine months");
              resolve();
            } catch (error) {
              reject(error);
            } finally {
              dispose();
            }
          });
        });
      } catch (error) {
        dispose();
        reject(error);
      }
    }));
  });

  it("coalesces duplicate pending and already-active selections", async () => {
    await new Promise<void>((resolve, reject) => createRoot((dispose) => {
      let finish: ((result: {sectionId: number; monthRange: number; graphs: LocationGraph[]; missingGraphIds: number[]}) => void) | undefined;
      const fetchSectionGraphs = vi.fn(() => new Promise<{
        sectionId: number;
        monthRange: number;
        graphs: LocationGraph[];
        missingGraphIds: number[];
      }>((requestResolve) => { finish = requestResolve; }));
      const controller = createSectionGraphRangeController({
        commonMonthRange: () => 3,
        graphsForSection: () => [baseGraph],
        missingGraphIdsForSection: () => [],
        fetchSectionGraphs
      });

      const first = controller.apply(section, 7);
      const duplicate = controller.apply(section, 7);
      expect(fetchSectionGraphs).toHaveBeenCalledOnce();
      finish?.({sectionId: 4, monthRange: 7, graphs: [rangedGraph], missingGraphIds: []});
      void Promise.all([first, duplicate]).then(async () => {
        await controller.apply(section, 7);
        try {
          expect(fetchSectionGraphs).toHaveBeenCalledOnce();
          resolve();
        } catch (error) {
          reject(error);
        } finally {
          dispose();
        }
      });
    }));
  });

  it("aborts a pending request when returning to the common slate", async () => {
    await new Promise<void>((resolve, reject) => createRoot((dispose) => {
      let requestSignal: AbortSignal | undefined;
      const controller = createSectionGraphRangeController({
        commonMonthRange: () => 3,
        graphsForSection: () => [baseGraph],
        missingGraphIdsForSection: () => [],
        fetchSectionGraphs: vi.fn((_, __, signal) => {
          requestSignal = signal;
          return new Promise(() => undefined);
        })
      });

      void controller.apply(section, 7);
      controller.reset(section);
      try {
        expect(requestSignal?.aborted).toBe(true);
        expect(controller.state(section)).toBeUndefined();
        resolve();
      } catch (error) {
        reject(error);
      } finally {
        dispose();
      }
    }));
  });

  it("rejects invalid ranges without invoking the transport", async () => {
    await new Promise<void>((resolve, reject) => createRoot((dispose) => {
      const fetchSectionGraphs = vi.fn();
      const controller = createSectionGraphRangeController({
        commonMonthRange: () => 3,
        graphsForSection: () => [baseGraph],
        missingGraphIdsForSection: () => [],
        fetchSectionGraphs
      });

      void controller.apply(section, 0).then(() => {
        try {
          expect(fetchSectionGraphs).not.toHaveBeenCalled();
          expect(controller.state(section)?.error).toContain("positive integer");
          expect(controller.monthRange(section)).toBe(3);
          resolve();
        } catch (error) {
          reject(error);
        } finally {
          dispose();
        }
      });
    }));
  });

  it("retains the common slate when the first section request fails", async () => {
    await new Promise<void>((resolve, reject) => createRoot((dispose) => {
      const controller = createSectionGraphRangeController({
        commonMonthRange: () => 3,
        graphsForSection: () => [baseGraph],
        missingGraphIdsForSection: () => [],
        fetchSectionGraphs: vi.fn(async () => { throw new Error("Graph data is temporarily unavailable"); })
      });

      void controller.apply(section, 7).then(() => {
        try {
          expect(controller.monthRange(section)).toBe(3);
          expect(controller.hasOverride(section)).toBe(false);
          expect(controller.graphs(section)).toEqual([baseGraph]);
          expect(controller.state(section)?.error).toBe("Graph data is temporarily unavailable");
          resolve();
        } catch (error) {
          reject(error);
        } finally {
          dispose();
        }
      });
    }));
  });
});
