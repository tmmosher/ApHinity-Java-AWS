import {createSignal, type Accessor} from "solid-js";
import type {
  LocationGraph,
  LocationSectionGraphsResult,
  LocationSectionLayout
} from "../../types/Types";

type SectionGraphRangeState = {
  requestId: number;
  monthRange: number;
  graphs: LocationGraph[];
  missingGraphIds: number[];
  loading: boolean;
  active: boolean;
  pendingMonthRange?: number;
  error?: string;
};

type SectionGraphRangeControllerProps = {
  commonMonthRange: Accessor<number>;
  graphsForSection: (section: LocationSectionLayout) => LocationGraph[];
  missingGraphIdsForSection: (section: LocationSectionLayout) => number[];
  fetchSectionGraphs: (
    sectionId: number,
    monthRange: number,
    signal?: AbortSignal
  ) => Promise<LocationSectionGraphsResult>;
};

/** Owns independent section projections without mutating the editable dashboard graph baseline. */
export const createSectionGraphRangeController = (props: SectionGraphRangeControllerProps) => {
  const [states, setStates] = createSignal<Record<number, SectionGraphRangeState>>({});
  const requestControllers = new Map<number, AbortController>();
  let nextRequestId = 0;

  const state = (section: LocationSectionLayout) => states()[section.section_id];
  const graphs = (section: LocationSectionLayout) => state(section)?.graphs ?? props.graphsForSection(section);
  const missingGraphIds = (section: LocationSectionLayout) =>
    state(section)?.missingGraphIds ?? props.missingGraphIdsForSection(section);
  const monthRange = (section: LocationSectionLayout) => state(section)?.monthRange ?? props.commonMonthRange();
  const hasOverride = (section: LocationSectionLayout) => createSignal<boolean>(state(section)?.active);

  const apply = async (section: LocationSectionLayout, requestedMonthRange: number): Promise<void> => {
    const current = state(section);
    if (!Number.isInteger(requestedMonthRange) || requestedMonthRange <= 0) {
      setStates((entries) => ({
        ...entries,
        [section.section_id]: {
          requestId: ++nextRequestId,
          monthRange: current?.monthRange ?? props.commonMonthRange(),
          graphs: current?.graphs ?? props.graphsForSection(section),
          missingGraphIds: current?.missingGraphIds ?? props.missingGraphIdsForSection(section),
          loading: false,
          active: current?.active ?? false,
          error: "Section graph month range must be a positive integer"
        }
      }));
      return;
    }
    if (current?.loading && current.pendingMonthRange === requestedMonthRange) {
      return;
    }
    if (current?.active && current.monthRange === requestedMonthRange && !current.error) {
      return;
    }

    requestControllers.get(section.section_id)?.abort();
    const requestController = new AbortController();
    requestControllers.set(section.section_id, requestController);
    const requestId = ++nextRequestId;
    setStates((entries) => ({
      ...entries,
      [section.section_id]: {
        requestId,
        monthRange: current?.monthRange ?? props.commonMonthRange(),
        graphs: current?.graphs ?? props.graphsForSection(section),
        missingGraphIds: current?.missingGraphIds ?? props.missingGraphIdsForSection(section),
        loading: true,
        active: current?.active ?? false,
        pendingMonthRange: requestedMonthRange
      }
    }));
    try {
      const result = await props.fetchSectionGraphs(
        section.section_id,
        requestedMonthRange,
        requestController.signal
      );
      setStates((entries) => {
        if (entries[section.section_id]?.requestId !== requestId) {
          return entries;
        }
        return {
          ...entries,
          [section.section_id]: {
            requestId,
            monthRange: result.monthRange,
            graphs: result.graphs,
            missingGraphIds: result.missingGraphIds,
            loading: false,
            active: true
          }
        };
      });
    } catch (error) {
      if (requestController.signal.aborted) {
        return;
      }
      setStates((entries) => {
        const pending = entries[section.section_id];
        if (!pending || pending.requestId !== requestId) {
          return entries;
        }
        return {
          ...entries,
          [section.section_id]: {
            ...pending,
            loading: false,
            error: error instanceof Error ? error.message : "Unable to load section graphs"
          }
        };
      });
    } finally {
      if (requestControllers.get(section.section_id) === requestController) {
        requestControllers.delete(section.section_id);
      }
    }
  };

  const reset = (section: LocationSectionLayout) => {
    requestControllers.get(section.section_id)?.abort();
    requestControllers.delete(section.section_id);
    setStates((entries) => {
      const next = {...entries};
      delete next[section.section_id];
      return next;
    });
  };

  const resetAll = () => {
    requestControllers.forEach((controller) => controller.abort());
    requestControllers.clear();
    setStates({});
  };

  return {state, graphs, missingGraphIds, monthRange, hasOverride, apply, reset, resetAll};
};

export type SectionGraphRangeController = ReturnType<typeof createSectionGraphRangeController>;
