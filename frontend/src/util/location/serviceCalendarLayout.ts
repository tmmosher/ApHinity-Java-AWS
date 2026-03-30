import type {LocationServiceEvent} from "../../types/Types";
import {compareDates, formatDateInputValue, parseDateInputValue} from "./dateUtility";

export type ServiceCalendarDayPiece = {
  event: LocationServiceEvent;
  isStart: boolean;
  isEnd: boolean;
};

export type ServiceCalendarWeekLayout = {
  visiblePiecesByDay: ReadonlyArray<ReadonlyArray<ServiceCalendarDayPiece | undefined>>;
  hiddenEventsByDay: ReadonlyArray<ReadonlyArray<LocationServiceEvent>>;
};

type ParsedServiceEvent = {
  event: LocationServiceEvent;
  startDay: Date;
  endDay: Date;
};

type WeekEventSegment = {
  event: LocationServiceEvent;
  startDayIndex: number;
  endDayIndex: number;
  startsWithinWeek: boolean;
  endsWithinWeek: boolean;
  lane: number;
};

const normalizeCalendarDate = (date: Date): Date => (
  new Date(date.getFullYear(), date.getMonth(), date.getDate())
);

const createWeekDayIndexMap = (week: readonly Date[]): Map<string, number> => (
  new Map(
    week.map((day, index) => [formatDateInputValue(normalizeCalendarDate(day)), index])
  )
);

const selectLaterDate = (left: Date, right: Date): Date => (
  compareDates(left, right) >= 0 ? left : right
);

const selectEarlierDate = (left: Date, right: Date): Date => (
  compareDates(left, right) <= 0 ? left : right
);

const parseServiceEvent = (event: LocationServiceEvent): ParsedServiceEvent => ({
  event,
  startDay: parseDateInputValue(event.date),
  endDay: parseDateInputValue(event.endDate)
});

const compareSegmentPriority = (left: WeekEventSegment, right: WeekEventSegment): number => {
  if (left.startDayIndex !== right.startDayIndex) {
    return left.startDayIndex - right.startDayIndex;
  }

  const leftSpan = left.endDayIndex - left.startDayIndex;
  const rightSpan = right.endDayIndex - right.startDayIndex;
  if (leftSpan !== rightSpan) {
    return rightSpan - leftSpan;
  }

  return left.event.id - right.event.id;
};

const createWeekEventSegment = (
  parsedEvent: ParsedServiceEvent,
  weekStart: Date,
  weekEnd: Date,
  weekDayIndexMap: ReadonlyMap<string, number>
): WeekEventSegment | undefined => {
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
    lane: -1
  };
};

const assignSegmentLanes = (segments: readonly WeekEventSegment[]): WeekEventSegment[] => {
  const occupiedUntilByLane: number[] = [];

  return [...segments]
    .sort(compareSegmentPriority)
    .map((segment) => {
      let lane = 0;
      while (occupiedUntilByLane[lane] !== undefined && occupiedUntilByLane[lane] >= segment.startDayIndex) {
        lane += 1;
      }

      occupiedUntilByLane[lane] = segment.endDayIndex;
      return {
        ...segment,
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
      .map((parsedEvent) => createWeekEventSegment(parsedEvent, weekStart, weekEnd, weekDayIndexMap))
      .filter((segment): segment is WeekEventSegment => segment !== undefined)
  );

  const visiblePiecesByDay = Array.from(
    {length: week.length},
    () => Array<ServiceCalendarDayPiece | undefined>(visibleLaneCount).fill(undefined)
  );
  const hiddenEventsByDay = Array.from(
    {length: week.length},
    () => [] as LocationServiceEvent[]
  );

  for (const segment of segments) {
    if (segment.lane < visibleLaneCount) {
      for (let dayIndex = segment.startDayIndex; dayIndex <= segment.endDayIndex; dayIndex += 1) {
        visiblePiecesByDay[dayIndex][segment.lane] = {
          event: segment.event,
          isStart: segment.startsWithinWeek && dayIndex === segment.startDayIndex,
          isEnd: segment.endsWithinWeek && dayIndex === segment.endDayIndex
        };
      }
      continue;
    }

    for (let dayIndex = segment.startDayIndex; dayIndex <= segment.endDayIndex; dayIndex += 1) {
      hiddenEventsByDay[dayIndex].push(segment.event);
    }
  }

  return {
    visiblePiecesByDay,
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
