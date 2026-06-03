import {renderToString} from "solid-js/web";
import {describe, expect, it, vi} from "vitest";
import type {LocationGraph} from "../types/Types";

const refetchGraphsMock = vi.fn(async () => undefined);
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
    graphTimeRange: () => "allTime",
    setGraphTimeRange: vi.fn(),
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

    renderToString(() => <LocationDashboardPanel locationId="42" />);

    expect(capturedToolbarProps).toHaveLength(1);
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

    await (onUploadSpreadsheetSuccess as (graphs: LocationGraph[]) => Promise<void>)(uploadedGraphs);

    expect(refetchGraphsMock).not.toHaveBeenCalled();
  });
});
