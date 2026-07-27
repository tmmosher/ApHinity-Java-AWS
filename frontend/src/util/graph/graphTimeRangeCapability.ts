import type {LocationGraph} from "../../types/Types";
import {isTabulatorGraph} from "./tabulatorGraph";

export type GraphTimeRangeCapabilityPolicy = {
  supportsSectionTimeRange: (graph: LocationGraph) => boolean;
};

const isRecord = (value: unknown): value is Record<string, unknown> =>
  value !== null && typeof value === "object" && !Array.isArray(value);

/** Honors graph-definition capabilities exported by the backend import metadata. */
export const metadataGraphTimeRangeCapabilityPolicy: GraphTimeRangeCapabilityPolicy = {
  supportsSectionTimeRange: (graph) => {
    if (typeof graph.sectionTimeRangeEnabled === "boolean") {
      return graph.sectionTimeRangeEnabled;
    }
    const meta = isRecord(graph.layout?.meta) ? graph.layout.meta : {};
    const importMetadata = isRecord(meta.aphinityImport) ? meta.aphinityImport : {};
    if (importMetadata.sectionTimeRangeEnabled === false
      || importMetadata.sectionTimeRangeEnabled === "false") {
      return false;
    }
    if (importMetadata.sectionTimeRangeEnabled === true
      || importMetadata.sectionTimeRangeEnabled === "true") {
      return true;
    }
    // Persisted graphs created before capability metadata existed retain their
    // renderer contract. Tabulator tables are the legacy fixed-range case.
    return !isTabulatorGraph(graph);
  }
};

const defaultGraphTimeRangeCapabilityPolicies: readonly GraphTimeRangeCapabilityPolicy[] =
  Object.freeze([metadataGraphTimeRangeCapabilityPolicy]);

export const sectionSupportsTimeRange = (
  graphs: LocationGraph[],
  policies: readonly GraphTimeRangeCapabilityPolicy[] = defaultGraphTimeRangeCapabilityPolicies
): boolean => graphs.length > 0 && graphs.every((graph) =>
  policies.every((policy) => policy.supportsSectionTimeRange(graph))
);
