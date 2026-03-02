import {describe, expect, it} from "vitest";
import {canEditLocationGraphs, canEditProfileEmail} from "../util/profileAccess";

describe("profileAccess", () => {
  it("disallows email editing for partner accounts", () => {
    expect(canEditProfileEmail("partner")).toBe(false);
  });

  it("allows email editing for admin and client accounts", () => {
    expect(canEditProfileEmail("admin")).toBe(true);
    expect(canEditProfileEmail("client")).toBe(true);
  });

  it("allows location graph editing for partner and admin accounts only", () => {
    expect(canEditLocationGraphs("admin")).toBe(true);
    expect(canEditLocationGraphs("partner")).toBe(true);
    expect(canEditLocationGraphs("client")).toBe(false);
    expect(canEditLocationGraphs(undefined)).toBe(false);
  });
});
