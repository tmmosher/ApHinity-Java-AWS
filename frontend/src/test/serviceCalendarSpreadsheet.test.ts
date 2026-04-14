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

const createWorkbookFile = (mutateWorkbook: (workbook: XLSX.WorkBook) => void): File => {
  const workbook = XLSX.utils.book_new();
  mutateWorkbook(workbook);
  const workbookArray = XLSX.write(workbook, {type: "array", bookType: "xlsx"});
  return new File([workbookArray], "service_calendar_upload.xlsx", {
    type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
  });
};

const excelSerialForDate = (year: number, month: number, day: number): number => (
  (Date.UTC(year, month - 1, day) - Date.UTC(1899, 11, 30)) / (24 * 60 * 60 * 1000)
);

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

  it("rejects impossible calendar dates instead of normalizing them", async () => {
    const file = createSpreadsheetFile([
      ["Title", "Description", "Start Date", "End Date", "Start Time", "End Time", "All Day", "Responsibility"],
      ["Pump visit", "", "2/31/2026", "2026-04-14", "09:15", "11:45", "False", "Partner"]
    ]);

    await expect(parseServiceCalendarSpreadsheetFile(file, "partner"))
      .rejects.toThrowError("Row 2: Start Date is invalid.");
  });

  it("parses timed rows that span into the next day", async () => {
    const file = createSpreadsheetFile([
      ["Title", "Description", "Start Date", "End Date", "Start Time", "End Time", "All Day", "Responsibility", "Status"],
      ["Overnight visit", "Inspect overnight system behavior", "2026-04-14", "2026-04-15", "22:15", "01:30", "False", "Partner", ""]
    ]);

    const requests = await parseServiceCalendarSpreadsheetFile(file, "partner");

    expect(requests).toEqual([
      {
        title: "Overnight visit",
        description: "Inspect overnight system behavior",
        responsibility: "partner",
        date: "2026-04-14",
        time: "22:15:00",
        endDate: "2026-04-15",
        endTime: "01:30:00",
        status: "upcoming"
      }
    ]);
  });

  it("rejects rows when the end date is before the start date", async () => {
    const file = createSpreadsheetFile([
      ["Title", "Description", "Start Date", "End Date", "Start Time", "End Time", "All Day", "Responsibility"],
      ["Backwards visit", "", "2026-04-14", "2026-04-13", "09:15", "11:45", "False", "Partner"]
    ]);

    await expect(parseServiceCalendarSpreadsheetFile(file, "partner"))
      .rejects.toThrowError("Row 2: End date and time must be on or after the start date and time.");
  });

  it("rejects all-day rows when the end date is before the start date", async () => {
    const file = createSpreadsheetFile([
      ["Title", "Description", "Start Date", "End Date", "Start Time", "End Time", "All Day", "Responsibility"],
      ["Backwards all-day visit", "", "2026-04-14", "2026-04-13", "", "", "True", "Partner"]
    ]);

    await expect(parseServiceCalendarSpreadsheetFile(file, "partner"))
      .rejects.toThrowError("Row 2: End date and time must be on or after the start date and time.");
  });

  it("rejects rows when the end time is before the start time on the same day", async () => {
    const file = createSpreadsheetFile([
      ["Title", "Description", "Start Date", "End Date", "Start Time", "End Time", "All Day", "Responsibility"],
      ["Backwards time visit", "", "2026-04-14", "2026-04-14", "11:45", "09:15", "False", "Partner"]
    ]);

    await expect(parseServiceCalendarSpreadsheetFile(file, "partner"))
      .rejects.toThrowError("Row 2: End date and time must be on or after the start date and time.");
  });

  it("rejects timed rows without a start time", async () => {
    const file = createSpreadsheetFile([
      ["Title", "Description", "Start Date", "End Date", "Start Time", "End Time", "All Day", "Responsibility"],
      ["Missing time visit", "", "2026-04-14", "2026-04-14", "", "11:45", "False", "Partner"]
    ]);

    await expect(parseServiceCalendarSpreadsheetFile(file, "partner"))
      .rejects.toThrowError("Row 2: Start Time is required.");
  });

  it("rejects invalid status values", async () => {
    const file = createSpreadsheetFile([
      ["Title", "Description", "Start Date", "End Date", "Start Time", "End Time", "All Day", "Responsibility", "Status"],
      ["Pump visit", "", "2026-04-14", "2026-04-14", "09:15", "11:45", "False", "Partner", "Delayed"]
    ]);

    await expect(parseServiceCalendarSpreadsheetFile(file, "partner"))
      .rejects.toThrowError("Row 2: Status must be Upcoming, Current, Overdue, or Completed.");
  });

  it("rejects spreadsheets that are missing required columns", async () => {
    const file = createSpreadsheetFile([
      ["Title", "Description", "Start Date", "End Date", "Start Time", "End Time", "All Day"],
      ["Pump visit", "", "2026-04-14", "2026-04-14", "09:15", "11:45", "False"]
    ]);

    await expect(parseServiceCalendarSpreadsheetFile(file, "partner"))
      .rejects.toThrowError("Spreadsheet is missing required columns: Responsibility.");
  });

  it("parses numeric Excel date and time cells", async () => {
    const file = createSpreadsheetFile([
      ["Title", "Description", "Start Date", "End Date", "Start Time", "End Time", "All Day", "Responsibility", "Status"],
      [
        "Numeric pump visit",
        "Numeric schedule",
        excelSerialForDate(2026, 4, 14),
        excelSerialForDate(2026, 4, 15),
        (9 * 60 + 15) / (24 * 60),
        (11 * 60 + 45) / (24 * 60),
        false,
        "Partner",
        "Upcoming"
      ]
    ]);

    const requests = await parseServiceCalendarSpreadsheetFile(file, "partner");

    expect(requests).toEqual([
      {
        title: "Numeric pump visit",
        description: "Numeric schedule",
        responsibility: "partner",
        date: "2026-04-14",
        time: "09:15:00",
        endDate: "2026-04-15",
        endTime: "11:45:00",
        status: "upcoming"
      }
    ]);
  });

  it("rejects sheets without a header row", async () => {
    const file = createWorkbookFile((workbook) => {
      const sheet = XLSX.utils.aoa_to_sheet([]);
      XLSX.utils.book_append_sheet(workbook, sheet, "Service Calendar");
    });

    await expect(parseServiceCalendarSpreadsheetFile(file, "partner"))
      .rejects.toThrowError("Spreadsheet is missing the header row.");
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
