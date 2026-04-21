import {createRoot} from "solid-js";
import {describe, expect, it, vi, beforeEach} from "vitest";

const mocks = vi.hoisted(() => ({
  parseGanttTaskSpreadsheetFile: vi.fn(),
  createLocationGanttTasksBulkById: vi.fn(),
  toastSuccess: vi.fn(),
  toastError: vi.fn()
}));

vi.mock("solid-toast", () => ({
  toast: {
    success: mocks.toastSuccess,
    error: mocks.toastError
  }
}));

vi.mock("../util/location/ganttTaskSpreadsheet", () => ({
  parseGanttTaskSpreadsheetFile: mocks.parseGanttTaskSpreadsheetFile
}));

vi.mock("../util/location/locationGanttTaskApi", () => ({
  createLocationGanttTasksBulkById: mocks.createLocationGanttTasksBulkById
}));

import {createGanttTaskImportController} from "../util/location/createGanttTaskImportController";

describe("createGanttTaskImportController", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("stages spreadsheet imports and applies them through the gantt bulk endpoint", async () => {
    await createRoot(async (dispose) => {
      try {
        const clearUploadInput = vi.fn();
        const refetchTasks = vi.fn().mockResolvedValue(undefined);
        const controller = createGanttTaskImportController({
          host: "https://example.test",
          locationId: () => "42",
          locationSessionToken: () => 7,
          clearUploadInput,
          refetchTasks
        });

        mocks.parseGanttTaskSpreadsheetFile.mockResolvedValue([
          {
            title: "OPS",
            startDate: "2026-04-10",
            endDate: "2026-04-12",
            description: null,
            dependencyTaskIds: []
          }
        ]);
        mocks.createLocationGanttTasksBulkById.mockResolvedValue([]);

        const file = new File(["placeholder"], "gantt.xlsx", {
          type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        });
        const stagePromise = controller.stageSpreadsheetImportFile(file);

        expect(controller.isImportingSpreadsheet()).toBe(true);
        await stagePromise;
        await Promise.resolve();

        expect(controller.isImportingSpreadsheet()).toBe(false);
        expect(controller.hasStagedTasks()).toBe(true);
        expect(controller.stagedTasks()).toHaveLength(1);
        expect(mocks.parseGanttTaskSpreadsheetFile).toHaveBeenCalledWith(file);
        expect(mocks.toastSuccess).toHaveBeenCalledWith("1 gantt task staged from spreadsheet.");

        await controller.applyStagedImports();
        await Promise.resolve();

        expect(mocks.createLocationGanttTasksBulkById).toHaveBeenCalledWith(
          "https://example.test",
          "42",
          [
            {
              title: "OPS",
              startDate: "2026-04-10",
              endDate: "2026-04-12",
              description: null,
              dependencyTaskIds: []
            }
          ]
        );
        expect(clearUploadInput).toHaveBeenCalledTimes(1);
        expect(refetchTasks).toHaveBeenCalledTimes(1);
        expect(controller.hasStagedTasks()).toBe(false);
        expect(mocks.toastSuccess).toHaveBeenCalledWith("Imported 1 gantt task.");
      } finally {
        dispose();
      }
    });
  });

  it("clears the importing flag and reports parser failures", async () => {
    await createRoot(async (dispose) => {
      try {
        const controller = createGanttTaskImportController({
          host: "https://example.test",
          locationId: () => "42",
          locationSessionToken: () => 7,
          clearUploadInput: vi.fn(),
          refetchTasks: vi.fn()
        });

        mocks.parseGanttTaskSpreadsheetFile.mockRejectedValue(new Error("bad workbook"));

        const file = new File(["placeholder"], "gantt.xlsx", {
          type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        });

        await expect(controller.stageSpreadsheetImportFile(file)).resolves.toBeUndefined();
        await Promise.resolve();

        expect(controller.isImportingSpreadsheet()).toBe(false);
        expect(controller.hasStagedTasks()).toBe(false);
        expect(mocks.toastError).toHaveBeenCalledWith("bad workbook");
      } finally {
        dispose();
      }
    });
  });
});
