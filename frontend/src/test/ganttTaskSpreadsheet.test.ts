import {describe, expect, it} from "vitest";
import * as XLSX from "xlsx";
import {parseGanttTaskSpreadsheetFile} from "../util/location/ganttTaskSpreadsheet";

const createSpreadsheetFile = (rows: unknown[][], fileName = "gantt.xlsx"): File => {
  const workbook = XLSX.utils.book_new();
  const sheet = XLSX.utils.aoa_to_sheet(rows);
  XLSX.utils.book_append_sheet(workbook, sheet, "Gantt Tasks");
  const workbookArray = XLSX.write(workbook, {type: "array", bookType: "xlsx"});
  return new File([workbookArray], fileName, {
    type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
  });
};

const excelSerialForDate = (year: number, month: number, day: number): number => (
  (Date.UTC(year, month - 1, day) - Date.UTC(1899, 11, 30)) / (24 * 60 * 60 * 1000)
);

describe("ganttTaskSpreadsheet", () => {
  it("parses a valid xlsx file into gantt task requests", async () => {
    const file = createSpreadsheetFile([
      ["Title", "Description", "Start Date", "End Date"],
      ["OPS", "Operational update", excelSerialForDate(2026, 4, 10), excelSerialForDate(2026, 4, 12)],
      ["QMS", "", excelSerialForDate(2026, 4, 20), excelSerialForDate(2026, 4, 20)]
    ]);

    const requests = await parseGanttTaskSpreadsheetFile(file);

    expect(requests).toEqual([
      {
        title: "OPS",
        description: "Operational update",
        startDate: "2026-04-10",
        endDate: "2026-04-12",
        dependencyTaskIds: []
      },
      {
        title: "QMS",
        description: null,
        startDate: "2026-04-20",
        endDate: "2026-04-20",
        dependencyTaskIds: []
      }
    ]);
  });

  it("rejects non-xlsx files", async () => {
    const file = new File(
      ["Title,Description,Start Date,End Date\nOPS,Operational update,2026-04-10,2026-04-12\n"],
      "gantt.csv",
      {type: "text/csv"}
    );

    await expect(parseGanttTaskSpreadsheetFile(file)).rejects.toThrow(
      "Only .xlsx spreadsheets are supported."
    );
  });

  it("rejects spreadsheets missing required headers", async () => {
    const file = createSpreadsheetFile([
      ["Title", "Start Date", "End Date"],
      ["OPS", excelSerialForDate(2026, 4, 10), excelSerialForDate(2026, 4, 12)]
    ]);

    await expect(parseGanttTaskSpreadsheetFile(file)).rejects.toThrow(
      "Spreadsheet is missing required columns: Description."
    );
  });

  it("rejects spreadsheets that contain headers but no gantt tasks", async () => {
    const file = createSpreadsheetFile([
      ["Title", "Description", "Start Date", "End Date"]
    ]);

    await expect(parseGanttTaskSpreadsheetFile(file)).rejects.toThrow(
      "Spreadsheet does not contain any gantt tasks."
    );
  });

  it("rejects gantt rows with invalid date ranges", async () => {
    const file = createSpreadsheetFile([
      ["Title", "Description", "Start Date", "End Date"],
      ["OPS", "Operational update", excelSerialForDate(2026, 4, 12), excelSerialForDate(2026, 4, 10)]
    ]);

    await expect(parseGanttTaskSpreadsheetFile(file)).rejects.toThrow(
      "Row 2: End Date must be on or after Start Date."
    );
  });
});
