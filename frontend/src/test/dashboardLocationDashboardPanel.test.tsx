import {renderToString} from "solid-js/web";
import {beforeEach, describe, expect, it, vi} from "vitest";
import type {LocationGraph} from "../types/Types";

const locationDetailMock = vi.hoisted(() => {
  const dashboardEdit = {
    orderedSections: () => [] as Array<{section_id: number; graph_ids: number[]}>,
    sectionGraphs: () => [],
    missingGraphIds: () => [] as number[],
    hasPendingDashboardChanges: () => false,
    isGraphMutationBusy: () => false,
    canCreateGraphs: () => true,
    isCreatingGraph: () => false,
    pendingDashboardMutationCount: () => 0,
    updatedAtLabel: () => "-",
    openCreateGraphModal: vi.fn(),
    applyGraphChanges: vi.fn(),
    openLayoutEditor: vi.fn(),
    applySpreadsheetUploadPreview: vi.fn(),
    undoLastDashboardEdit: vi.fn(),
    retryAll: vi.fn(),
    openGraphEditor: vi.fn(),
    isCreateGraphModalOpen: () => false,
    isLayoutEditorOpen: () => false,
    editingGraphId: () => null,
    editingGraphForTimeRange: () => undefined,
    isDeletingGraph: () => false,
    isSavingGraphChanges: () => false,
    hasPendingGraphChanges: () => false,
    sectionOptions: () => [],
    nextSectionId: () => 1,
    workingSectionLayout: () => ({sections: []}),
    workingGraphs: () => [],
    createGraphFromModal: vi.fn(),
    closeCreateGraphModal: vi.fn(),
    closeGraphEditor: vi.fn(),
    deleteGraphFromModal: vi.fn(),
    renameGraphFromModal: vi.fn(),
    applyLocalGraphEdit: vi.fn(),
    applyLocalSectionLayoutEdit: vi.fn(),
    closeLayoutEditor: vi.fn()
  };

  return {
    location: () => undefined,
    graphs: () => undefined,
    graphsLoading: () => false,
    graphsError: () => undefined as unknown,
    graphTimeRange: () => "threeMonths",
    setGraphTimeRange: vi.fn(),
    dashboardEdit,
    serviceCalendarStaging: {
      stageImportedRequests: vi.fn()
    },
    refetchLocation: async () => undefined,
    refetchGraphs: async () => undefined
  };
});

vi.mock("@solidjs/router", () => ({
  A: (props: {children?: unknown}) => props.children ?? null
}));

vi.mock("../context/ApiHostContext", () => ({
  useApiHost: () => "https://example.test"
}));

vi.mock("../context/ProfileContext", () => ({
  useProfile: () => ({
    profile: () => ({role: "partner"})
  })
}));

vi.mock("../context/LocationDetailContext", () => ({
  useLocationDetail: () => locationDetailMock
}));

vi.mock("../components/Chart", () => ({
  __esModule: true,
  default: () => null,
  loadPlotlyModule: vi.fn(async () => null)
}));

vi.mock("../components/graph-editor/GraphCreateModal", () => ({
  __esModule: true,
  default: () => null
}));

vi.mock("../components/graph-editor/GraphEditorModal", () => ({
  __esModule: true,
  default: () => null
}));

vi.mock("../components/location/LocationDashboardLayoutModal", () => ({
  __esModule: true,
  default: () => null
}));

vi.mock("corvu/popover", () => {
  const Popover = (props: {children?: unknown}) =>
    typeof props.children === "function"
      ? props.children({setOpen: vi.fn()})
      : props.children ?? null;

  Popover.Trigger = (props: Record<string, unknown>) => {
    const {children, ...rest} = props;
    return <button {...rest}>{children as Element}</button>;
  };
  Popover.Portal = (props: Record<string, unknown>) => {
    const {children} = props;
    return <>{children as unknown}</>;
  };
  Popover.Content = (props: Record<string, unknown>) => {
    const {children, ...rest} = props;
    return <div {...rest}>{children as Element}</div>;
  };
  Popover.Close = (props: Record<string, unknown>) => {
    const {children, ...rest} = props;
    return <button {...rest}>{children as Element}</button>;
  };
  Popover.Label = (props: Record<string, unknown>) => {
    const {children, ...rest} = props;
    return <div {...rest}>{children as Element}</div>;
  };
  Popover.Description = (props: Record<string, unknown>) => {
    const {children, ...rest} = props;
    return <div {...rest}>{children as Element}</div>;
  };

  return {
    default: Popover
  };
});

import {LocationDashboardPanel} from "../pages/authenticated/panels/location/LocationDashboardPanel";

describe("LocationDashboardPanel", () => {
  beforeEach(() => {
    locationDetailMock.graphs = () => undefined;
    locationDetailMock.graphsError = () => undefined;
    locationDetailMock.dashboardEdit.orderedSections = () => [];
    locationDetailMock.dashboardEdit.sectionGraphs = () => [];
    locationDetailMock.dashboardEdit.missingGraphIds = () => [];
  });

  it("renders the dashboard toolbar with the updated title and overflow actions", () => {
    const html = renderToString(() => <LocationDashboardPanel locationId="42" />);

    expect(html).toContain("Dashboard");
    expect(html).toContain("Work Order Email");
    expect(html).toContain("Upload Spreadsheet");
    expect(html).toContain("Dashboard spreadsheets can only be uploaded from All Data.");
    expect(html).toContain("Add New Graph");
    expect(html).toContain("Edit Layout");
    expect(html).toContain("Apply");
    expect(html).toContain("Undo");
    expect(html).toContain("Last updated");
    expect(html).toContain("Date Range");
    expect(html).toContain("3 Months");
    expect(html).toContain("12 Months");
    expect(html).toContain("All Data");
    expect(html).toContain("btn h-11 min-h-11 rounded-2xl");
    expect(html).toContain("aria-label=\"More actions\"");
    expect(html).toContain("aria-label=\"Dashboard date range selector\"");
    expect(html).toContain("accept=\".xlsx,.xlsm\"");
    expect(html).toMatch(/style="[^"]*width:2rem[^"]*height:2rem/);
  });

  it("keeps dashboard sections visible when graph payload loading fails", () => {
    locationDetailMock.graphsError = () => new Error("missing graph");
    locationDetailMock.dashboardEdit.orderedSections = () => [{section_id: 1, graph_ids: [999]}];
    locationDetailMock.dashboardEdit.missingGraphIds = () => [999];

    const html = renderToString(() => <LocationDashboardPanel locationId="42" />);

    expect(html).toContain("Some graph payloads could not be loaded");
    expect(html).toContain("Retry Graphs");
    expect(html).toContain("Missing graph IDs:");
    expect(html).toContain("999");
    expect(html).toContain("grid gap-4 xl:grid-cols-2");
    expect(html).not.toContain("Unable to load location graphs");
  });

  it("renders normal sections in left-to-right column stacks around full-width table breaks", () => {
    const standardGraph: LocationGraph = {
      id: 101,
      name: "Compliance",
      data: [{type: "bar", x: ["A"], y: [1]}],
      layout: {},
      config: {},
      style: {height: 320},
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-02T00:00:00Z"
    };
    const tableGraph: LocationGraph = {
      id: 202,
      name: "Recent Sample Measurements",
      data: [{
        type: "table",
        header: {values: ["Facility", "Follow-ups"]},
        cells: {values: [["Newport Beach"], [1]]},
        meta: {renderer: "tabulator"}
      }],
      layout: {},
      config: {},
      style: {height: 640},
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-02T00:00:00Z"
    };
    locationDetailMock.graphs = () => [standardGraph, tableGraph];
    locationDetailMock.dashboardEdit.orderedSections = () => [
      {section_id: 1, graph_ids: [101]},
      {section_id: 2, graph_ids: [202]},
      {section_id: 3, graph_ids: [101]}
    ];
    locationDetailMock.dashboardEdit.sectionGraphs = (section: {section_id: number}) =>
      section.section_id === 2 ? [tableGraph] : [standardGraph];

    const html = renderToString(() => <LocationDashboardPanel locationId="42" />);

    expect(html).toContain("space-y-4");
    expect(html).toContain("grid gap-4 xl:grid-cols-2");
    expect(html).toMatch(/data-section-id="1"[\s\S]*data-section-id="2"[\s\S]*data-section-id="3"/);
    expect(html).toMatch(/class="w-full rounded-xl[^"]*break-inside-avoid" data-section-id="1"/);
    expect(html).toMatch(/class="w-full rounded-xl[^"]*shadow-sm " data-section-id="2"/);
  });
});
