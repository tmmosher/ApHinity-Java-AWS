import {renderToString} from "solid-js/web";
import {describe, expect, it, vi} from "vitest";

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
  it("renders the dashboard toolbar with the updated title and overflow actions", () => {
    const html = renderToString(() => <LocationDashboardPanel locationId="42" />);

    expect(html).toContain("Dashboard");
    expect(html).toContain("Work Order Email");
    expect(html).toContain("Upload Spreadsheet");
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
    expect(html).toContain("accept=\".xlsx\"");
    expect(html).toMatch(/style="[^"]*width:2rem[^"]*height:2rem/);
  });
});
