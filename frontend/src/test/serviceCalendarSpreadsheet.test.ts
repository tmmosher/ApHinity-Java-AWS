import {describe, expect, it} from "vitest";
import * as XLSX from "xlsx";
import {
  buildServiceCalendarSpreadsheetBlob,
  parseServiceCalendarSpreadsheetFile
} from "../util/location/serviceCalendarSpreadsheet";

const createSpreadsheetFile = (rows: unknown[][]): File => {
  const workbook = XLSX.utils.book_new();
  const sheet = XLSX.utils.aoa_to_sheet(rows);
  XLSX.utils.book_append_sheet(workbook, sheet, "Service Calendar");
  const workbookArray = XLSX.write(workbook, {type: "array", bookType: "xlsx"});
  return new File([workbookArray], "service_calendar_upload.xlsx", {
    type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
  });
};

describe("serviceCalendarSpreadsheet", () => {
  it("parses timed spreadsheet rows into service event requests", async () => {
    const file = createSpreadsheetFile([
      ["Title", "Description", "Start Date", "End Date", "Start Time", "End Time", "All Day", "Responsibility"],
      ["Pump visit", "Inspect pump pressure", "2026-04-14", "2026-04-14", "09:15", "11:45", "False", "Partner"]
    ]);

    const requests = await parseServiceCalendarSpreadsheetFile(file, "partner");

    expect(requests).toEqual([
      {
        title: "Pump visit",
        description: "Inspect pump pressure",
        responsibility: "partner",
        date: "2026-04-14",
        time: "09:15:00",
        endDate: "2026-04-14",
        endTime: "11:45:00",
        status: "upcoming"
      }
    ]);
  });

  it("rejects partner rows for client imports", async () => {
    const file = createSpreadsheetFile([
      ["Title", "Description", "Start Date", "End Date", "Start Time", "End Time", "All Day", "Responsibility"],
      ["Pump visit", "", "2026-04-14", "2026-04-14", "09:15", "11:45", "False", "Partner"]
    ]);

    await expect(parseServiceCalendarSpreadsheetFile(file, "client"))
      .rejects.toThrowError("Row 2: You do not have permission to import Partner events.");
  });

  it("builds a spreadsheet blob that round-trips imported events", async () => {
    const blob = await buildServiceCalendarSpreadsheetBlob([
      {
        title: "Site shutdown",
        description: null,
        responsibility: "client",
        date: "2026-04-20",
        time: "00:00:00",
        endDate: "2026-04-21",
        endTime: "23:59:59",
        status: "completed"
      }
    ]);

    const file = new File([blob], "roundtrip.xlsx", {type: blob.type});
    const requests = await parseServiceCalendarSpreadsheetFile(file, "partner");

    expect(requests).toEqual([
      {
        title: "Site shutdown",
        description: null,
        responsibility: "client",
        date: "2026-04-20",
        time: "00:00:00",
        endDate: "2026-04-21",
        endTime: "23:59:59",
        status: "completed"
      }
    ]);
  });
});
