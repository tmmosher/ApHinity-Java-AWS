import {renderToString} from "solid-js/web";
import {describe, expect, it, vi} from "vitest";

vi.mock("@solidjs/router", () => ({
  A: (props: {children?: unknown}) => props.children ?? null,
  useParams: () => ({locationId: "42"})
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
    refetchLocation: async () => undefined,
    refetchGraphs: async () => undefined
  })
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

import {LocationDashboardPanel} from "../pages/authenticated/panels/location/LocationDashboardPanel";

describe("LocationDashboardPanel", () => {
  it("renders the dashboard toolbar with the updated title and action buttons", () => {
    const html = renderToString(LocationDashboardPanel);

    expect(html).toContain("Dashboard");
    expect(html).toContain("Add Graph");
    expect(html).toContain("Apply");
    expect(html).toContain("Undo");
    expect(html).toContain("Edit Layout");
    expect(html).toContain("Last updated");
    expect(html).toContain("btn h-11 min-h-11 rounded-2xl");
  });
});
