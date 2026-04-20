import {describe, expect, it, vi} from "vitest";
import {fitGanttTaskLabels, truncateTextToWidth} from "../util/location/ganttTaskLabelTruncation";

describe("ganttTaskLabelTruncation", () => {
  it("leaves labels that already fit unchanged", () => {
    expect(
      truncateTextToWidth("Short", 80, (candidate) => candidate.length * 8)
    ).toBe("Short");
  });

  it("truncates labels to fit the available width", () => {
    expect(
      truncateTextToWidth("ABCDEFGHIJ", 48, (candidate) => candidate.length * 8)
    ).toBe("ABC...");
  });

  it("fits gantt labels inside the task width and preserves the full title metadata", () => {
    const label = {
      dataset: {} as Record<string, string>,
      textContent: "ABCDEFGHIJ",
      setAttribute: vi.fn()
    };
    const bar = {
      getWidth: () => 60
    };
    const barWrapper = {
      querySelector: (selector: string) => {
        if (selector === ".bar") {
          return bar;
        }
        if (selector === ".bar-label") {
          return label;
        }
        return null;
      }
    };
    const root = {
      querySelectorAll: () => [barWrapper]
    } as unknown as ParentNode;

    fitGanttTaskLabels(root);

    expect(label.textContent).toBe("ABC...");
    expect(label.dataset.fullLabel).toBe("ABCDEFGHIJ");
    expect(label.setAttribute).toHaveBeenCalledWith("title", "ABCDEFGHIJ");
    expect(label.setAttribute).toHaveBeenCalledWith("aria-label", "ABCDEFGHIJ");
  });
});
