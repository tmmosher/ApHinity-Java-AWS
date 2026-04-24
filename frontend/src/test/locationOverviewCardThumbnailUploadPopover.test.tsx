import {renderToString} from "solid-js/web";
import {describe, expect, it, vi} from "vitest";

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

import {LocationOverviewCardThumbnailUploadPopover} from "../components/location/LocationOverviewCardThumbnailUploadPopover";
import type {LocationSummary} from "../types/Types";

describe("LocationOverviewCardThumbnailUploadPopover", () => {
  const baseLocation: LocationSummary = {
    id: 1,
    name: "Alpha",
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-02T00:00:00Z",
    sectionLayout: {sections: []}
  };

  it("renders upload wording when no thumbnail exists", () => {
    const html = renderToString(() => (
      <LocationOverviewCardThumbnailUploadPopover
        location={baseLocation}
        onUpload={vi.fn(async () => true)}
      />
    ));

    expect(html).toContain("aria-label=\"Upload thumbnail\"");
    expect(html).toContain(">Upload thumbnail<");
    expect(html).toContain("accept=\".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp\"");
  });

  it("renders replace wording when a thumbnail exists", () => {
    const html = renderToString(() => (
      <LocationOverviewCardThumbnailUploadPopover
        location={{
          ...baseLocation,
          thumbnailAvailable: true
        }}
        onUpload={vi.fn(async () => true)}
      />
    ));

    expect(html).toContain("aria-label=\"Replace thumbnail\"");
    expect(html).toContain(">Replace thumbnail<");
  });
});
