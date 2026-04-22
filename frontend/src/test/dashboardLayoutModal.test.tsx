import {renderToString} from "solid-js/web";
import type {JSX} from "solid-js";
import {describe, expect, it, vi} from "vitest";
import type {LocationGraph, LocationSectionLayoutConfig} from "../types/Types";

vi.mock("corvu/dialog", () => {
  type DialogChildrenProps = {children?: JSX.Element};
  type DialogDivProps = DialogChildrenProps & JSX.HTMLAttributes<HTMLDivElement>;

  const Dialog = (props: DialogChildrenProps & {open?: boolean}) => props.children;
  Dialog.Portal = (props: DialogChildrenProps) => props.children;
  Dialog.Overlay = () => null;
  Dialog.Content = (props: DialogDivProps) => <div {...props} />;
  Dialog.Label = (props: DialogDivProps) => <div {...props} />;
  Dialog.Description = (props: DialogDivProps) => <div {...props} />;
  Dialog.Close = (props: DialogChildrenProps) => props.children ?? null;
  return {default: Dialog};
});

import {LocationDashboardLayoutModal} from "../components/location/LocationDashboardLayoutModal";

describe("LocationDashboardLayoutModal", () => {
  it("renders the combined section and graph layout shell with accessible labels", () => {
    const sectionLayout: LocationSectionLayoutConfig = {
      sections: [
        {section_id: 7, graph_ids: [101]}
      ]
    };
    const graphs: LocationGraph[] = [
      {
        id: 101,
        name: "Revenue",
        data: [],
        layout: {title: {text: "Revenue by Month"}},
        config: null,
        style: null,
        createdAt: "2026-04-01T00:00:00Z",
        updatedAt: "2026-04-01T00:00:00Z"
      }
    ];

    const html = renderToString(() => (
      <LocationDashboardLayoutModal
        isOpen={true}
        sectionLayout={sectionLayout}
        graphs={graphs}
        onSave={() => undefined}
        onClose={() => undefined}
      />
    ));

    expect(html).toContain("Reorder Dashboard Layout");
    expect(html).toContain("Drag the blue sections to reorder the dashboard");
    expect(html).toContain("dashboard-layout-modal-title");
    expect(html).toContain("dashboard-layout-modal-description");
    expect(html).toContain("Section");
    expect(html).toContain("7");
    expect(html).toContain("Revenue");
    expect(html).toContain("Revenue by Month");
    expect(html).toContain("draggable=\"true\"");
    expect(html).toContain("aria-roledescription=\"draggable graph card\"");
    expect(html).toContain("Move section");
    expect(html).toContain("Move graph");
    expect(html).toContain("Save");
    expect(html).toContain("Cancel");
  });
});
