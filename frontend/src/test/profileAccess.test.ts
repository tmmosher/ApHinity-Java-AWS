import {describe, expect, it} from "vitest";
import {canEditProfileEmail} from "../util/profileAccess";

describe("profileAccess", () => {
  it("disallows email editing for partner accounts", () => {
    expect(canEditProfileEmail("partner")).toBe(false);
  });

  it("allows email editing for admin and client accounts", () => {
    expect(canEditProfileEmail("admin")).toBe(true);
    expect(canEditProfileEmail("client")).toBe(true);
  });
});
