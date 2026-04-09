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

vi.mock("../components/graph-editor/GraphEditorModal", () => ({
  __esModule: true,
  default: () => null
}));

vi.mock("../pages/authenticated/panels/location/LocationDetailContext", () => ({
  useLocationDetail: () => ({
    location: () => undefined,
    graphs: () => undefined,
    graphsLoading: () => false,
    graphsError: () => undefined,
    refetchLocation: async () => undefined,
    refetchGraphs: async () => undefined
  })
}));

import {advanceGraphLoadAnimationToken} from "../pages/authenticated/panels/location/LocationDashboardPanel";

describe("LocationDashboardPanel graph animation token", () => {
  it("increments only when graphs finish loading with data available", () => {
    expect(advanceGraphLoadAnimationToken(0, false, true, false)).toBe(0);
    expect(advanceGraphLoadAnimationToken(0, true, true, true)).toBe(0);
    expect(advanceGraphLoadAnimationToken(0, true, false, false)).toBe(0);
    expect(advanceGraphLoadAnimationToken(0, true, false, true)).toBe(1);
  });

  it("does not increment when the dashboard renders cached graphs without a new load", () => {
    expect(advanceGraphLoadAnimationToken(1, false, false, true)).toBe(1);
    expect(advanceGraphLoadAnimationToken(4, false, false, true)).toBe(4);
  });

  it("keeps the initial animation token stable after the first successful load", () => {
    expect(advanceGraphLoadAnimationToken(1, true, false, true)).toBe(1);
  });
});
