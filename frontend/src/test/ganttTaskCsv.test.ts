import {describe, expect, it} from "vitest";
import {parseGanttTaskCsvFile} from "../util/location/ganttTaskCsv";

describe("ganttTaskCsv", () => {
  it("parses a valid csv file into gantt task requests", async () => {
    const file = new File(
      [
        "Title,Description,Start Date,End Date\n" +
        "OPS,\"Operational update\",2026-04-10,2026-04-12\n" +
        "QMS,,2026-04-20,2026-04-20\n"
      ],
      "gantt.csv",
      {type: "text/csv"}
    );

    const requests = await parseGanttTaskCsvFile(file);

    expect(requests).toEqual([
      {
        title: "OPS",
        description: "Operational update",
        startDate: "2026-04-10",
        endDate: "2026-04-12"
      },
      {
        title: "QMS",
        description: null,
        startDate: "2026-04-20",
        endDate: "2026-04-20"
      }
    ]);
  });

  it("rejects csv files missing required headers", async () => {
    const file = new File(
      ["Title,Start Date,End Date\nOPS,2026-04-10,2026-04-12\n"],
      "gantt.csv",
      {type: "text/csv"}
    );

    await expect(parseGanttTaskCsvFile(file)).rejects.toThrow(
      "CSV is missing required columns: Description."
    );
  });

  it("rejects csv rows with invalid date ranges", async () => {
    const file = new File(
      ["Title,Description,Start Date,End Date\nOPS,Update,2026-04-12,2026-04-10\n"],
      "gantt.csv",
      {type: "text/csv"}
    );

    await expect(parseGanttTaskCsvFile(file)).rejects.toThrow(
      "Row 2: End Date must be on or after Start Date."
    );
  });
});
