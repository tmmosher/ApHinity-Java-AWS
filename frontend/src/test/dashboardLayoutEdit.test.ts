import {describe, expect, it} from "vitest";
import type {LocationSectionLayoutConfig} from "../types/Types";
import {
  areLocationSectionLayoutsEqual,
  cloneLocationSectionLayout,
  findGraphLayoutPosition,
  findSectionLayoutIndex,
  moveGraphWithinLayout,
  moveSectionWithinLayout
} from "../util/location/dashboardLayoutEdit";

const baseLayout: LocationSectionLayoutConfig = {
  sections: [
    {section_id: 1, graph_ids: [11, 12]},
    {section_id: 2, graph_ids: [21, 22]}
  ]
};

describe("dashboardLayoutEdit", () => {
  it("clones a section layout without sharing references", () => {
    const cloned = cloneLocationSectionLayout(baseLayout);

    expect(cloned).toEqual(baseLayout);
    expect(cloned).not.toBe(baseLayout);
    expect(cloned.sections).not.toBe(baseLayout.sections);
    expect(cloned.sections[0]).not.toBe(baseLayout.sections[0]);
  });

  it("reorders sections by relative drop position", () => {
    const reordered = moveSectionWithinLayout(baseLayout, 0, 2);

    expect(reordered.sections.map((section) => section.section_id)).toEqual([2, 1]);
  });

  it("moves graphs across sections and preserves graph order in the target section", () => {
    const moved = moveGraphWithinLayout(baseLayout, 0, 1, 1);

    expect(moved.sections[0].graph_ids).toEqual([11]);
    expect(moved.sections[1].graph_ids).toEqual([21, 22, 12]);
  });

  it("moves graphs before another graph in the same section", () => {
    const moved = moveGraphWithinLayout(baseLayout, 0, 1, 0, 0);

    expect(moved.sections[0].graph_ids).toEqual([12, 11]);
  });

  it("compares layouts by content", () => {
    expect(areLocationSectionLayoutsEqual(baseLayout, cloneLocationSectionLayout(baseLayout))).toBe(true);
    expect(areLocationSectionLayoutsEqual(baseLayout, moveSectionWithinLayout(baseLayout, 1, 0))).toBe(false);
  });

  it("finds sections and graph positions by persisted layout ids", () => {
    expect(findSectionLayoutIndex(baseLayout, 2)).toBe(1);
    expect(findSectionLayoutIndex(baseLayout, 99)).toBe(-1);
    expect(findGraphLayoutPosition(baseLayout, 22)).toEqual({sectionIndex: 1, graphIndex: 1});
    expect(findGraphLayoutPosition(baseLayout, 99)).toBeNull();
  });
});
