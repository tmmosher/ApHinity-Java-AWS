import Calendar from "corvu/calendar";
import Popover from "corvu/popover";
import {For, Show, type JSX} from "solid-js";
import type {
  AccountRole,
  CreateLocationServiceEventRequest,
  LocationServiceEvent
} from "../../types/Types";
import {
  formatDisplayDate,
  formatWeekdayLong,
  formatWeekdayShort,
  isSameCalendarMonth
} from "../../util/location/dateUtility";
import {
  type ServiceCalendarVisibleSegment,
  type ServiceCalendarWeekLayout
} from "../../util/location/serviceCalendarLayout";
import ServiceEventCreatePopover from "./ServiceEventCreatePopover";
import ServiceEventEditPopover from "./ServiceEventEditPopover";

type ServiceCalendarMonthGridProps = {
  month: Date;
  weekdays: readonly Date[];
  weeks: readonly (readonly Date[])[];
  weekLayouts: readonly ServiceCalendarWeekLayout[];
  eventEditorRole?: AccountRole | undefined;
  onCreateEventSave?: (request: CreateLocationServiceEventRequest) => Promise<void>;
  canEditEvent?: (event: LocationServiceEvent) => boolean;
  canCompleteEvent?: (event: LocationServiceEvent) => boolean;
  onEditEventSave?: (
    event: LocationServiceEvent,
    request: CreateLocationServiceEventRequest
  ) => Promise<void>;
  onCompleteEvent?: (event: LocationServiceEvent) => Promise<void>;
  canDeleteEvent?: (event: LocationServiceEvent) => boolean;
  onDeleteEvent?: (event: LocationServiceEvent) => Promise<void>;
};

type ServiceEventSegmentProps = {
  segment: ServiceCalendarVisibleSegment;
  canEdit: boolean;
  canComplete: boolean;
  editRole?: AccountRole | undefined;
  onEditEventSave?: (
    event: LocationServiceEvent,
    request: CreateLocationServiceEventRequest
  ) => Promise<void>;
  onCompleteEvent?: (event: LocationServiceEvent) => Promise<void>;
  canDeleteEvent?: (event: LocationServiceEvent) => boolean;
  onDeleteEvent?: (event: LocationServiceEvent) => Promise<void>;
};

type HiddenDayEventsPopoverProps = {
  dayIndex: number;
  hiddenEvents: readonly LocationServiceEvent[];
  editRole?: AccountRole | undefined;
  canEditEvent?: (event: LocationServiceEvent) => boolean;
  canCompleteEvent?: (event: LocationServiceEvent) => boolean;
  onEditEventSave?: (
    event: LocationServiceEvent,
    request: CreateLocationServiceEventRequest
  ) => Promise<void>;
  onCompleteEvent?: (event: LocationServiceEvent) => Promise<void>;
  canDeleteEvent?: (event: LocationServiceEvent) => boolean;
  onDeleteEvent?: (event: LocationServiceEvent) => Promise<void>;
};

type ServiceCalendarDayBackgroundProps = {
  day: Date;
  dayIndex: number;
  month: Date;
  eventEditorRole?: AccountRole | undefined;
  onCreateEventSave?: (request: CreateLocationServiceEventRequest) => Promise<void>;
};

type ServiceCalendarWeekRowProps = {
  week: readonly Date[];
  month: Date;
  layout: ServiceCalendarWeekLayout;
  eventEditorRole?: AccountRole | undefined;
  onCreateEventSave?: (request: CreateLocationServiceEventRequest) => Promise<void>;
  canEditEvent?: (event: LocationServiceEvent) => boolean;
  canCompleteEvent?: (event: LocationServiceEvent) => boolean;
  onEditEventSave?: (
    event: LocationServiceEvent,
    request: CreateLocationServiceEventRequest
  ) => Promise<void>;
  onCompleteEvent?: (event: LocationServiceEvent) => Promise<void>;
  canDeleteEvent?: (event: LocationServiceEvent) => boolean;
  onDeleteEvent?: (event: LocationServiceEvent) => Promise<void>;
};

const MAX_VISIBLE_WEEK_EVENT_LANES = 2;
const EVENT_GRID_ROW_OFFSET = 2;
const OVERFLOW_GRID_ROW = MAX_VISIBLE_WEEK_EVENT_LANES + EVENT_GRID_ROW_OFFSET;
const WEEK_GRID_END_LINE = OVERFLOW_GRID_ROW + 1;

const WEEK_ROW_GRID_CLASS =
  "relative isolate grid grid-cols-7 grid-rows-[1.35rem_1.05rem_1.05rem_1.05rem] gap-px bg-base-300/70 " +
  "md:grid-rows-[1.45rem_1.125rem_1.125rem_1.125rem] lg:grid-rows-[1.55rem_1.125rem_1.125rem_1.125rem]";

const CALENDAR_POPOVER_PROPS = {
  placement: "bottom-start" as const,
  trapFocus: false,
  restoreFocus: false,
  closeOnOutsideFocus: false
};

const createGridPlacementStyle = (
  columnStart: number,
  columnEnd: number,
  rowStart: number,
  rowEnd?: number
): JSX.CSSProperties => ({
  "grid-column": `${columnStart} / ${columnEnd}`,
  "grid-row": rowEnd === undefined ? `${rowStart}` : `${rowStart} / ${rowEnd}`
});

const createDayBackgroundStyle = (dayIndex: number): JSX.CSSProperties => (
  createGridPlacementStyle(dayIndex + 1, dayIndex + 2, 1, WEEK_GRID_END_LINE)
);

const createVisibleSegmentStyle = (segment: ServiceCalendarVisibleSegment): JSX.CSSProperties => (
  createGridPlacementStyle(
    segment.startDayIndex + 1,
    segment.endDayIndex + 2,
    segment.lane + EVENT_GRID_ROW_OFFSET
  )
);

const createHiddenEventsTriggerStyle = (dayIndex: number): JSX.CSSProperties => (
  createGridPlacementStyle(dayIndex + 1, dayIndex + 2, OVERFLOW_GRID_ROW)
);

const weekdayHeaderClass =
  "bg-base-200/55 px-3 py-3 text-left text-[11px] font-semibold uppercase tracking-[0.18em] text-base-content/60 md:px-4 md:text-xs";

const dayBackgroundClass =
  "relative flex min-h-0 cursor-pointer flex-col items-start justify-start px-2.5 py-1.5 text-left transition duration-150 ease-out " +
  "motion-reduce:transform-none motion-reduce:transition-none focus-visible:z-10 focus-visible:outline-none " +
  "focus-visible:ring-2 focus-visible:ring-primary/40 hover:-translate-y-px hover:bg-base-100/95 hover:shadow-sm " +
  "active:translate-y-px active:scale-[0.99] data-selected:bg-primary/12 data-today:ring-1 data-today:ring-accent/45 " +
  "md:px-3 md:py-2";

const calendarEventSegmentClass =
  "relative z-[2] h-full min-w-0 truncate border px-1.5 text-left text-[9px] font-semibold " +
  "leading-[1.05rem] shadow-sm transition hover:-translate-y-px hover:shadow md:text-[10px] md:leading-[1.125rem]";

const calendarInteractiveCardClass =
  "relative z-[2] rounded-lg border text-left shadow-sm transition hover:-translate-y-px hover:shadow";

const eventSegmentClass = (
  responsibility: LocationServiceEvent["responsibility"],
  segment: ServiceCalendarVisibleSegment
): string => (
  `${calendarEventSegmentClass} ${segment.startsWithinWeek ? "rounded-l-lg" : "rounded-l-none"} ` +
  `${segment.endsWithinWeek ? "rounded-r-lg" : "rounded-r-none"} ` +
  `${isStagedCalendarEvent(segment.event) ? "border-dashed ring-1 ring-primary/45 ring-inset " : ""}` +
  (responsibility === "client"
    ? "border-[#f59e0b]/40 bg-[#f59e0b]/18 text-[#9a3412]"
    : "border-[#86efac] bg-[#dcfce7] text-[#166534]")
);

const overflowTriggerClass =
  `${calendarInteractiveCardClass} flex h-full min-w-0 items-center justify-center px-1.5 text-xs font-semibold ` +
  "leading-[1.05rem] border-base-300/80 bg-base-200/80 text-base-content/65 md:leading-[1.125rem]";

const overflowListItemClass =
  `${calendarInteractiveCardClass} flex flex-col gap-1 px-2 py-1.5 border-base-300/80 bg-base-100 text-base-content`;

const isStagedCalendarEvent = (event: LocationServiceEvent): boolean => (
  "isStaged" in event && event.isStaged === true
);

const formatEventDateRange = (event: LocationServiceEvent): string => (
  event.date === event.endDate
    ? formatDisplayDate(event.date)
    : `${formatDisplayDate(event.date)} - ${formatDisplayDate(event.endDate)}`
);

const ServiceEventSegment = (props: ServiceEventSegmentProps) => (
  <ServiceEventEditPopover
    event={props.segment.event}
    canEdit={props.canEdit}
    canComplete={props.canComplete}
    canDelete={props.canDeleteEvent?.(props.segment.event) ?? false}
    role={props.editRole}
    onSave={props.onEditEventSave}
    onComplete={props.onCompleteEvent}
    onDelete={props.onDeleteEvent}
    deleteLabel="Delete"
  >
    <Popover.Trigger
      type="button"
      style={createVisibleSegmentStyle(props.segment)}
      class={eventSegmentClass(props.segment.event.responsibility, props.segment)}
      title={props.segment.event.title}
      data-service-event-bar=""
      onClick={(event) => event.stopPropagation()}
    >
      {props.segment.event.title}
    </Popover.Trigger>
  </ServiceEventEditPopover>
);

const HiddenDayEventListItem = (props: {
  event: LocationServiceEvent;
  canEdit: boolean;
  canComplete: boolean;
  editRole?: AccountRole | undefined;
  onEditEventSave?: (
    event: LocationServiceEvent,
    request: CreateLocationServiceEventRequest
  ) => Promise<void>;
  onCompleteEvent?: (event: LocationServiceEvent) => Promise<void>;
  canDeleteEvent?: (event: LocationServiceEvent) => boolean;
  onDeleteEvent?: (event: LocationServiceEvent) => Promise<void>;
}) => (
  <ServiceEventEditPopover
    event={props.event}
    canEdit={props.canEdit}
    canComplete={props.canComplete}
    canDelete={props.canDeleteEvent?.(props.event) ?? false}
    role={props.editRole}
    onSave={props.onEditEventSave}
    onComplete={props.onCompleteEvent}
    onDelete={props.onDeleteEvent}
    deleteLabel="Delete"
  >
    <Popover.Trigger
      type="button"
      class={overflowListItemClass + (isStagedCalendarEvent(props.event) ? " border-dashed ring-1 ring-primary/45 ring-inset" : "")}
      title={props.event.title}
      data-service-event-overflow-item=""
      onClick={(event) => event.stopPropagation()}
    >
      <span class="truncate text-xs font-semibold leading-4">
        {props.event.title}
      </span>
      <span class="truncate text-[10px] font-medium leading-4 text-base-content/60">
        {formatEventDateRange(props.event)}
      </span>
    </Popover.Trigger>
  </ServiceEventEditPopover>
);

const HiddenDayEventsPopover = (props: HiddenDayEventsPopoverProps) => (
  <Popover {...CALENDAR_POPOVER_PROPS}>
    <Popover.Trigger
      type="button"
      style={createHiddenEventsTriggerStyle(props.dayIndex)}
      class={overflowTriggerClass}
      title="Show additional service events"
      data-service-event-overflow-trigger=""
      onClick={(event) => event.stopPropagation()}
    >
      ...
    </Popover.Trigger>

    <Popover.Portal>
      <Popover.Content class="z-50 w-[min(92vw,24rem)] rounded-2xl border border-base-300 bg-base-100 shadow-2xl">
        <div class="space-y-3 p-3 md:p-4">
          <div class="space-y-1">
            <Popover.Label class="text-sm font-semibold">
              Additional Service Events
            </Popover.Label>
            <Popover.Description class="text-xs leading-5 text-base-content/65">
              Select an event to view its full details.
            </Popover.Description>
          </div>

          <div class="space-y-2">
            <For each={props.hiddenEvents}>
              {(event) => (
                <HiddenDayEventListItem
                  event={event}
                  canEdit={props.canEditEvent?.(event) ?? false}
                  canComplete={props.canCompleteEvent?.(event) ?? false}
                  editRole={props.editRole}
                  onEditEventSave={props.onEditEventSave}
                  onCompleteEvent={props.onCompleteEvent}
                  canDeleteEvent={props.canDeleteEvent}
                  onDeleteEvent={props.onDeleteEvent}
                />
              )}
            </For>
          </div>
        </div>
      </Popover.Content>
    </Popover.Portal>
  </Popover>
);

const ServiceCalendarDayBackground = (props: ServiceCalendarDayBackgroundProps) => {
  const outsideMonth = () => !isSameCalendarMonth(props.day, props.month);
  const dayCellClassList = {
    "bg-base-100/85 text-base-content": !outsideMonth(),
    "bg-base-200/40 text-base-content/40": outsideMonth()
  };

  return (
    <Calendar.Cell as="div" class="contents">
      {props.onCreateEventSave ? (
        <ServiceEventCreatePopover
          day={props.day}
          style={createDayBackgroundStyle(props.dayIndex)}
          class={dayBackgroundClass}
          classList={dayCellClassList}
          role={props.eventEditorRole}
          onSave={props.onCreateEventSave}
        />
      ) : (
        <Calendar.CellTrigger
          as="div"
          day={props.day}
          style={createDayBackgroundStyle(props.dayIndex)}
          class={dayBackgroundClass}
          classList={dayCellClassList}
        >
          <span class="text-xs font-semibold tabular-nums md:text-sm">
            {props.day.getDate()}
          </span>
        </Calendar.CellTrigger>
      )}
    </Calendar.Cell>
  );
};

const ServiceCalendarWeekRow = (props: ServiceCalendarWeekRowProps) => (
  <div role="row" class={WEEK_ROW_GRID_CLASS} data-service-calendar-week-row="">
    <For each={props.week}>
      {(day, dayIndex) => (
        <ServiceCalendarDayBackground
          day={day}
          dayIndex={dayIndex()}
          month={props.month}
          eventEditorRole={props.eventEditorRole}
          onCreateEventSave={props.onCreateEventSave}
        />
      )}
    </For>

    <For each={props.layout.visibleSegments}>
      {(segment) => (
        <ServiceEventSegment
          segment={segment}
          canEdit={props.canEditEvent?.(segment.event) ?? false}
          canComplete={props.canCompleteEvent?.(segment.event) ?? false}
          editRole={props.eventEditorRole}
          onEditEventSave={props.onEditEventSave}
          onCompleteEvent={props.onCompleteEvent}
          canDeleteEvent={props.canDeleteEvent}
          onDeleteEvent={props.onDeleteEvent}
        />
      )}
    </For>

    <For each={props.layout.hiddenEventsByDay}>
      {(hiddenEvents, dayIndex) => (
        <Show when={hiddenEvents.length > 0}>
          <HiddenDayEventsPopover
            dayIndex={dayIndex()}
            hiddenEvents={hiddenEvents}
            editRole={props.eventEditorRole}
            canEditEvent={props.canEditEvent}
            canCompleteEvent={props.canCompleteEvent}
            onEditEventSave={props.onEditEventSave}
            onCompleteEvent={props.onCompleteEvent}
            canDeleteEvent={props.canDeleteEvent}
            onDeleteEvent={props.onDeleteEvent}
          />
        </Show>
      )}
    </For>
  </div>
);

const WeekdayHeaderCell = (props: {day: Date}) => (
  <div
    role="columnheader"
    aria-label={formatWeekdayLong(props.day)}
    class={weekdayHeaderClass}
  >
    {formatWeekdayShort(props.day)}
  </div>
);

export const buildServiceCalendarWeekLayoutsForGrid = (
  weekLayouts: readonly ServiceCalendarWeekLayout[],
  weekIndex: number
): ServiceCalendarWeekLayout => weekLayouts[weekIndex] ?? {visibleSegments: [], hiddenEventsByDay: []};

export const ServiceCalendarMonthGrid = (props: ServiceCalendarMonthGridProps) => (
  <div role="grid" aria-label="Service schedule calendar" class="grid h-full grid-rows-[auto_1fr] gap-px">
    <div role="row" class="grid grid-cols-7 gap-px bg-base-300/70">
      <For each={props.weekdays}>
        {(weekday) => <WeekdayHeaderCell day={weekday} />}
      </For>
    </div>

    <div class="grid min-h-0 gap-px bg-base-300/70">
      <For each={props.weeks}>
        {(week, weekIndex) => (
          <ServiceCalendarWeekRow
            week={week}
            month={props.month}
            layout={buildServiceCalendarWeekLayoutsForGrid(props.weekLayouts, weekIndex())}
            eventEditorRole={props.eventEditorRole}
            onCreateEventSave={props.onCreateEventSave}
            canEditEvent={props.canEditEvent}
            canCompleteEvent={props.canCompleteEvent}
            onEditEventSave={props.onEditEventSave}
            onCompleteEvent={props.onCompleteEvent}
            canDeleteEvent={props.canDeleteEvent}
            onDeleteEvent={props.onDeleteEvent}
          />
        )}
      </For>
    </div>
  </div>
);

export default ServiceCalendarMonthGrid;
