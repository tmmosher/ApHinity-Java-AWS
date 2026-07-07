import {renderToString} from "solid-js/web";
import {describe, expect, it, vi} from "vitest";
import type {LocationDashboardSpreadsheetUploadResult, LocationGraph} from "../types/Types";

const refetchGraphsMock = vi.fn(async () => undefined);
const applySpreadsheetUploadPreviewMock = vi.fn();
const stageImportedRequestsMock = vi.fn();
const capturedToolbarProps: Array<Record<string, unknown>> = [];

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
  useLocationDetail: () => ({
    location: () => undefined,
    graphs: () => undefined,
    graphsLoading: () => false,
    graphsError: () => undefined,
    graphTimeRange: () => "threeMonths",
    setGraphTimeRange: vi.fn(),
    dashboardEdit: {
      orderedSections: () => [],
      sectionGraphs: () => [],
      missingGraphIds: () => [],
      hasPendingDashboardChanges: () => false,
      isGraphMutationBusy: () => false,
      canCreateGraphs: () => true,
      isCreatingGraph: () => false,
      pendingDashboardMutationCount: () => 0,
      updatedAtLabel: () => "-",
      openCreateGraphModal: vi.fn(),
      applyGraphChanges: vi.fn(),
      openLayoutEditor: vi.fn(),
      applySpreadsheetUploadPreview: applySpreadsheetUploadPreviewMock,
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
    },
    serviceCalendarStaging: {
      stageImportedRequests: stageImportedRequestsMock
    },
    refetchLocation: async () => undefined,
    refetchGraphs: refetchGraphsMock
  })
}));

vi.mock("../components/location/LocationDashboardToolbar", () => ({
  __esModule: true,
  default: (props: Record<string, unknown>) => {
    capturedToolbarProps.push(props);
    return null;
  }
}));

vi.mock("../components/location/LocationDashboardSection", () => ({
  __esModule: true,
  default: () => null
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

vi.mock("../components/common/Chart", () => ({
  loadPlotlyModule: vi.fn(async () => null)
}));

import {LocationDashboardPanel} from "../pages/authenticated/panels/location/LocationDashboardPanel";

describe("LocationDashboardPanel upload refresh", () => {
  it("passes uploaded graphs through the toolbar upload success callback", async () => {
    capturedToolbarProps.length = 0;
    refetchGraphsMock.mockClear();
    applySpreadsheetUploadPreviewMock.mockClear();
    stageImportedRequestsMock.mockClear();

    renderToString(() => <LocationDashboardPanel locationId="42" />);

    expect(capturedToolbarProps).toHaveLength(1);
    expect(capturedToolbarProps[0].monthRange).toBe(3);
    const onUploadSpreadsheetSuccess = capturedToolbarProps[0].onUploadSpreadsheetSuccess;
    expect(typeof onUploadSpreadsheetSuccess).toBe("function");

    const uploadedGraphs: LocationGraph[] = [
      {
        id: 99,
        name: "Water Quality Compliance",
        data: [],
        layout: null,
        config: null,
        style: null,
        createdAt: "2026-05-06T00:00:00Z",
        updatedAt: "2026-05-06T00:00:00Z"
      }
    ];

    const result: LocationDashboardSpreadsheetUploadResult = {
      graphs: uploadedGraphs,
      correctiveActions: [{
        title: "Corrective Action",
        responsibility: "partner",
        date: "2026-05-06",
        time: "00:00:00",
        endDate: "2026-05-06",
        endTime: "23:59:59",
        description: null,
        status: "current"
      }]
    };

    const file = new File(["dashboard"], "dashboard.xlsx", {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    });

    await (onUploadSpreadsheetSuccess as (
      result: LocationDashboardSpreadsheetUploadResult,
      file: File
    ) => Promise<void>)(result, file);

    expect(applySpreadsheetUploadPreviewMock).toHaveBeenCalledWith(uploadedGraphs, file);
    expect(stageImportedRequestsMock).toHaveBeenCalledWith(result.correctiveActions, {isCorrectiveAction: true});
    expect(refetchGraphsMock).not.toHaveBeenCalled();
  });
});
