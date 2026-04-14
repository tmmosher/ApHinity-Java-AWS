import type {LocationServiceEvent} from "../../types/Types";
import {
  compareDates,
  formatDateInputValue,
  parseDateInputValue,
  parseDateTimeValue
} from "./dateUtility";

export type ServiceCalendarVisibleSegment = {
  event: LocationServiceEvent;
  lane: number;
  startDayIndex: number;
  endDayIndex: number;
  startsWithinWeek: boolean;
  endsWithinWeek: boolean;
};

export type ServiceCalendarWeekLayout = {
  visibleSegments: ReadonlyArray<ServiceCalendarVisibleSegment>;
  hiddenEventsByDay: ReadonlyArray<ReadonlyArray<LocationServiceEvent>>;
};

type ParsedServiceEvent = {
  event: LocationServiceEvent;
  startDay: Date;
  endDay: Date;
  startDateTime: Date;
  endDateTime: Date;
};

type SortableWeekSegment = Omit<ServiceCalendarVisibleSegment, "lane"> & {
  startDateTime: Date;
  endDateTime: Date;
};

const normalizeCalendarDate = (date: Date): Date => (
  new Date(date.getFullYear(), date.getMonth(), date.getDate())
);

const createWeekDayIndexMap = (week: readonly Date[]): Map<string, number> => (
  new Map(
    week.map((day, index) => [formatDateInputValue(normalizeCalendarDate(day)), index])
  )
);

const parseServiceEvent = (event: LocationServiceEvent): ParsedServiceEvent => ({
  event,
  startDay: parseDateInputValue(event.date),
  endDay: parseDateInputValue(event.endDate),
  startDateTime: parseDateTimeValue(event.date, event.time, "Invalid service event start"),
  endDateTime: parseDateTimeValue(event.endDate, event.endTime, "Invalid service event end")
});

const selectLaterDate = (left: Date, right: Date): Date => (
  compareDates(left, right) >= 0 ? left : right
);

const selectEarlierDate = (left: Date, right: Date): Date => (
  compareDates(left, right) <= 0 ? left : right
);

const compareVisibleSegmentPriority = (
  left: SortableWeekSegment,
  right: SortableWeekSegment
): number => {
  if (left.startDayIndex !== right.startDayIndex) {
    return left.startDayIndex - right.startDayIndex;
  }

  const startDateTimeComparison = compareDates(left.startDateTime, right.startDateTime);
  if (startDateTimeComparison !== 0) {
    return startDateTimeComparison;
  }

  const leftSpan = left.endDayIndex - left.startDayIndex;
  const rightSpan = right.endDayIndex - right.startDayIndex;
  if (leftSpan !== rightSpan) {
    return rightSpan - leftSpan;
  }

  const endDateTimeComparison = compareDates(left.endDateTime, right.endDateTime);
  if (endDateTimeComparison !== 0) {
    return endDateTimeComparison;
  }

  return left.event.id - right.event.id;
};

const createWeekSegment = (
  parsedEvent: ParsedServiceEvent,
  weekStart: Date,
  weekEnd: Date,
  weekDayIndexMap: ReadonlyMap<string, number>
): SortableWeekSegment | undefined => {
  if (
    compareDates(parsedEvent.startDay, weekEnd) > 0 ||
    compareDates(parsedEvent.endDay, weekStart) < 0
  ) {
    return undefined;
  }

  const segmentStart = selectLaterDate(parsedEvent.startDay, weekStart);
  const segmentEnd = selectEarlierDate(parsedEvent.endDay, weekEnd);
  const startDayIndex = weekDayIndexMap.get(formatDateInputValue(segmentStart));
  const endDayIndex = weekDayIndexMap.get(formatDateInputValue(segmentEnd));

  if (startDayIndex === undefined || endDayIndex === undefined) {
    throw new Error("Service calendar segment did not align with the provided week.");
  }

  return {
    event: parsedEvent.event,
    startDayIndex,
    endDayIndex,
    startsWithinWeek: compareDates(parsedEvent.startDay, weekStart) >= 0,
    endsWithinWeek: compareDates(parsedEvent.endDay, weekEnd) <= 0,
    startDateTime: parsedEvent.startDateTime,
    endDateTime: parsedEvent.endDateTime
  };
};

const assignSegmentLanes = (
  segments: readonly SortableWeekSegment[]
): ServiceCalendarVisibleSegment[] => {
  const occupiedUntilByLane: number[] = [];

  return [...segments]
    .sort(compareVisibleSegmentPriority)
    .map((segment) => {
      let lane = 0;
      while (occupiedUntilByLane[lane] !== undefined && occupiedUntilByLane[lane] >= segment.startDayIndex) {
        lane += 1;
      }

      occupiedUntilByLane[lane] = segment.endDayIndex;
      const {startDateTime: _startDateTime, endDateTime: _endDateTime, ...visibleSegment} = segment;
      return {
        ...visibleSegment,
        lane
      };
    });
};

const createWeekLayout = (
  week: readonly Date[],
  parsedEvents: readonly ParsedServiceEvent[],
  visibleLaneCount: number
): ServiceCalendarWeekLayout => {
  const weekStart = normalizeCalendarDate(week[0]);
  const weekEnd = normalizeCalendarDate(week[week.length - 1]);
  const weekDayIndexMap = createWeekDayIndexMap(week);

  const segments = assignSegmentLanes(
    parsedEvents
      .map((parsedEvent) => createWeekSegment(parsedEvent, weekStart, weekEnd, weekDayIndexMap))
      .filter((segment): segment is SortableWeekSegment => segment !== undefined)
  );

  const visibleSegments: ServiceCalendarVisibleSegment[] = [];
  const hiddenEventsByDay = Array.from(
    {length: week.length},
    () => [] as LocationServiceEvent[]
  );

  for (const segment of segments) {
    if (segment.lane < visibleLaneCount) {
      visibleSegments.push(segment);
      continue;
    }

    for (let dayIndex = segment.startDayIndex; dayIndex <= segment.endDayIndex; dayIndex += 1) {
      hiddenEventsByDay[dayIndex].push(segment.event);
    }
  }

  return {
    visibleSegments,
    hiddenEventsByDay
  };
};

export const buildServiceCalendarWeekLayouts = (
  weeks: readonly (readonly Date[])[],
  events: readonly LocationServiceEvent[],
  visibleLaneCount: number
): ServiceCalendarWeekLayout[] => {
  const parsedEvents = events.map(parseServiceEvent);
  return weeks.map((week) => createWeekLayout(week, parsedEvents, visibleLaneCount));
};
