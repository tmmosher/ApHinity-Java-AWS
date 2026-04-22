import type {LocationSectionLayout, LocationSectionLayoutConfig} from "../../types/Types";

export const cloneLocationSectionLayout = (
  layout: LocationSectionLayoutConfig
): LocationSectionLayoutConfig => ({
  sections: layout.sections.map((section) => ({
    section_id: section.section_id,
    graph_ids: [...section.graph_ids]
  }))
});

export const areLocationSectionLayoutsEqual = (
  left: LocationSectionLayoutConfig,
  right: LocationSectionLayoutConfig
): boolean => JSON.stringify(left) === JSON.stringify(right);

const cloneSections = (sections: LocationSectionLayout[]): LocationSectionLayout[] =>
  sections.map((section) => ({
    section_id: section.section_id,
    graph_ids: [...section.graph_ids]
  }));

export type GraphLayoutPosition = {
  sectionIndex: number;
  graphIndex: number;
};

export const findSectionLayoutIndex = (
  layout: LocationSectionLayoutConfig,
  sectionId: number
): number => layout.sections.findIndex((section) => section.section_id === sectionId);

export const findGraphLayoutPosition = (
  layout: LocationSectionLayoutConfig,
  graphId: number
): GraphLayoutPosition | null => {
  for (let sectionIndex = 0; sectionIndex < layout.sections.length; sectionIndex += 1) {
    const graphIndex = layout.sections[sectionIndex].graph_ids.indexOf(graphId);
    if (graphIndex >= 0) {
      return {sectionIndex, graphIndex};
    }
  }

  return null;
};

const moveItem = <T>(items: T[], fromIndex: number, toIndex: number): T[] => {
  if (fromIndex === toIndex || fromIndex < 0 || toIndex < 0 || fromIndex >= items.length || toIndex > items.length) {
    return [...items];
  }

  const nextItems = [...items];
  const [item] = nextItems.splice(fromIndex, 1);
  if (item === undefined) {
    return [...items];
  }

  const normalizedTargetIndex = fromIndex < toIndex ? toIndex - 1 : toIndex;
  nextItems.splice(normalizedTargetIndex, 0, item);
  return nextItems;
};

export const moveSectionWithinLayout = (
  layout: LocationSectionLayoutConfig,
  fromIndex: number,
  toIndex: number
): LocationSectionLayoutConfig => ({
  sections: moveItem(cloneSections(layout.sections), fromIndex, toIndex)
});

export const moveGraphWithinLayout = (
  layout: LocationSectionLayoutConfig,
  sourceSectionIndex: number,
  sourceGraphIndex: number,
  targetSectionIndex: number,
  targetGraphIndex?: number
): LocationSectionLayoutConfig => {
  if (
    sourceSectionIndex < 0
    || sourceGraphIndex < 0
    || targetSectionIndex < 0
    || sourceSectionIndex >= layout.sections.length
    || targetSectionIndex >= layout.sections.length
  ) {
    return cloneLocationSectionLayout(layout);
  }

  const nextSections = cloneSections(layout.sections);
  const sourceSection = nextSections[sourceSectionIndex];
  const targetSection = nextSections[targetSectionIndex];
  const sourceGraphId = sourceSection.graph_ids[sourceGraphIndex];

  if (sourceGraphId === undefined || targetSection === undefined) {
    return cloneLocationSectionLayout(layout);
  }

  if (sourceSectionIndex === targetSectionIndex && targetGraphIndex !== undefined) {
    if (sourceGraphIndex === targetGraphIndex || sourceGraphIndex + 1 === targetGraphIndex) {
      return cloneLocationSectionLayout(layout);
    }
  }

  const [graphId] = sourceSection.graph_ids.splice(sourceGraphIndex, 1);
  if (graphId === undefined) {
    return cloneLocationSectionLayout(layout);
  }

  const insertIndex =
    targetGraphIndex === undefined
      ? targetSection.graph_ids.length
      : (sourceSectionIndex === targetSectionIndex && sourceGraphIndex < targetGraphIndex
          ? targetGraphIndex - 1
          : targetGraphIndex);

  targetSection.graph_ids.splice(insertIndex, 0, graphId);
  return {
    sections: nextSections
  };
};
