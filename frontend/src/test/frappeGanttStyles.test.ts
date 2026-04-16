import {existsSync, readFileSync} from "node:fs";
import {fileURLToPath} from "node:url";
import {describe, expect, it} from "vitest";

const cssPath = fileURLToPath(new URL("../styles/frappe-gantt.css", import.meta.url));
const packageCssPath = fileURLToPath(
  new URL("../../../node_modules/frappe-gantt/dist/frappe-gantt.css", import.meta.url)
);

describe("frappe-gantt stylesheet", () => {
  it("points at the installed package stylesheet through a local import shim", () => {
    expect(existsSync(cssPath)).toBe(true);
    expect(existsSync(packageCssPath)).toBe(true);

    const source = readFileSync(cssPath, "utf8").trim();
    expect(source).toBe('@import "../../../node_modules/frappe-gantt/dist/frappe-gantt.css";');
  });
});

