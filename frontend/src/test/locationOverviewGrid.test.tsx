import {renderToString} from "solid-js/web";
import {describe, expect, it, vi} from "vitest";

vi.mock("@solidjs/router", () => ({
  A: (props: {children?: unknown; href: string}) => <a href={props.href}>{props.children}</a>
}));

vi.mock("corvu/popover", () => {
  const Popover = (props: {children: unknown}) => (
    <>
      {typeof props.children === "function"
        ? props.children({setOpen: vi.fn()})
        : props.children}
    </>
  );
  Popover.Trigger = (props: Record<string, unknown>) => {
    const {children, ...rest} = props;
    return <button {...rest}>{children}</button>;
  };
  Popover.Portal = (props: {children: unknown}) => <>{props.children}</>;
  Popover.Content = (props: Record<string, unknown>) => {
    const {children, ...rest} = props;
    return <div {...rest}>{children}</div>;
  };
  Popover.Label = (props: Record<string, unknown>) => {
    const {children, ...rest} = props;
    return <p {...rest}>{children}</p>;
  };
  Popover.Description = (props: Record<string, unknown>) => {
    const {children, ...rest} = props;
    return <p {...rest}>{children}</p>;
  };
  Popover.Close = (props: Record<string, unknown>) => {
    const {children, ...rest} = props;
    return <button {...rest}>{children}</button>;
  };
  return {default: Popover};
});

import {LocationOverviewGrid} from "../components/location/LocationOverviewGrid";
import type {LocationSummary} from "../types/Types";

const locations = Object.assign(
  () => ([
    {
      id: 1,
      name: "Alpha",
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-02T00:00:00Z",
      sectionLayout: {sections: []},
      thumbnailAvailable: true
    },
    {
      id: 2,
      name: "Beta",
      createdAt: "2026-01-03T00:00:00Z",
      updatedAt: "2026-01-04T00:00:00Z",
      sectionLayout: {sections: []}
    }
  ] as LocationSummary[]),
  {
    loading: false,
    error: undefined
  }
);

describe("LocationOverviewGrid", () => {
  it("renders a separate star and ellipsis svg for each card", () => {
    const html = renderToString(() => (
      <LocationOverviewGrid
        title="Locations"
        description="All locations"
        apiHost="https://example.test"
        locations={locations}
        favoriteLocationId="1"
        canEditLocations={true}
        emptyMessage="No locations."
        onFavorite={vi.fn()}
        onRename={vi.fn(async () => true)}
        onThumbnailUpload={vi.fn(async () => true)}
        onRetry={vi.fn()}
      />
    ));

    expect((html.match(/<svg\b[^>]*aria-hidden="true"/g) ?? []).length).toBe(4);
    expect((html.match(/aria-label="More location actions"/g) ?? []).length).toBe(2);
    expect((html.match(/aria-label="Rename location"/g) ?? []).length).toBe(2);
    expect((html.match(/aria-label="Upload thumbnail"/g) ?? []).length).toBe(1);
    expect((html.match(/aria-label="Replace thumbnail"/g) ?? []).length).toBe(1);
    expect((html.match(/aria-label="Remove favorite location"/g) ?? []).length).toBe(1);
    expect((html.match(/aria-label="Set favorite location"/g) ?? []).length).toBe(1);
    expect((html.match(/aria-pressed="true"/g) ?? []).length).toBe(1);
    expect((html.match(/aria-pressed="false"/g) ?? []).length).toBe(1);
    expect((html.match(/<img\b[^>]*src="https:\/\/example\.test\/api\/core\/locations\/1\/thumbnail"/g) ?? []).length).toBe(1);
  });
});
