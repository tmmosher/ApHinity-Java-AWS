import {describe, expect, it} from "vitest";
import {createMapById} from "../util/common/indexById";

describe("createMapById", () => {
  it("creates a map keyed by each item's id", () => {
    const map = createMapById([
      {id: 11, name: "alpha"},
      {id: 12, name: "beta"}
    ]);

    expect(map.get(11)).toEqual({id: 11, name: "alpha"});
    expect(map.get(12)).toEqual({id: 12, name: "beta"});
    expect(map.size).toBe(2);
  });
});
