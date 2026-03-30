import Calendar from "corvu/calendar";
import Popover from "corvu/popover";
import {For, Index, Show, createMemo, type Component, type ParentProps} from "solid-js";
import type {LocationServiceEvent} from "../../../../types/Types";
import {
  formatDisplayDate,
  formatDisplayTime,
  formatMonthLabel,
  formatWeekdayLong,
  formatWeekdayShort,
  isSameCalendarMonth
} from "../../../../util/location/dateUtility";
import {
  buildServiceCalendarWeekLayouts,
  type ServiceCalendarDayPiece,
  type ServiceCalendarWeekLayout
} from "../../../../util/location/serviceCalendarLayout";

type ServiceScheduleCalendarProps = {
  month?: Date;
  onMonthChange?: (month: Date) => void;
  events?: LocationServiceEvent[];
  isLoading?: boolean;
  error?: string;
  onRetry?: () => void;
  canEditEvent?: (event: LocationServiceEvent) => boolean;
  onEditEvent?: (event: LocationServiceEvent) => void;
};

type ServiceEventPopoverContentProps = {
  event: LocationServiceEvent;
  canEdit: boolean;
  onEdit?: (event: LocationServiceEvent) => void;
  closePopover: () => void;
};

type ServiceEventPieceProps = {
  piece: ServiceCalendarDayPiece;
  canEdit: boolean;
  onEditEvent?: (event: LocationServiceEvent) => void;
};

type HiddenDayEventsPopoverProps = {
  hiddenEvents: LocationServiceEvent[];
  canEditEvent?: (event: LocationServiceEvent) => boolean;
  onEditEvent?: (event: LocationServiceEvent) => void;
};

type CalendarDayCellProps = {
  day: Date;
  month: Date;
  dayIndex: number;
  weekLayout: ServiceCalendarWeekLayout;
  canEditEvent?: (event: LocationServiceEvent) => boolean;
  onEditEvent?: (event: LocationServiceEvent) => void;
};

const CalendarChevron = (props: {direction: "left" | "right"}) => (
  <svg
    aria-hidden="true"
    viewBox="0 0 20 20"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    class="size-4"
  >
    <path
      d={props.direction === "left" ? "M11.75 4.75L6.5 10L11.75 15.25" : "M8.25 4.75L13.5 10L8.25 15.25"}
      stroke="currentColor"
      stroke-width="1.75"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>
);

const MAX_VISIBLE_WEEK_EVENT_LANES = 2;
const CALENDAR_POPOVER_PROPS = {
  placement: "bottom-start" as const,
  trapFocus: false,
  restoreFocus: false,
  closeOnOutsideFocus: false
};
const calendarEventPillClass =
  "relative z-[1] h-[1.05rem] w-full rounded-lg border text-left shadow-sm transition hover:-translate-y-px hover:shadow md:h-[1.125rem]";

const calendarEventCardClass =
  "w-full rounded-lg border text-left shadow-sm transition hover:-translate-y-px hover:shadow";

const eventPieceClass = (
  responsibility: LocationServiceEvent["responsibility"],
  piece: ServiceCalendarDayPiece
): string => (
  `${calendarEventPillClass} truncate px-1.5 py-0 text-[9px] font-semibold leading-[1.05rem] md:text-[10px] md:leading-[1.125rem] ` +
  `${piece.isStart ? "" : "-ml-2.5 rounded-l-none border-l-0 md:-ml-3 "} ` +
  `${piece.isEnd ? "" : "-mr-2.5 rounded-r-none border-r-0 md:-mr-3 "} ` +
  (responsibility === "client"
    ? "border-[#f59e0b]/40 bg-[#f59e0b]/18 text-[#9a3412]"
    : "border-[#86efac] bg-[#dcfce7] text-[#166534]")
);

const overflowTriggerClass =
  `${calendarEventPillClass} flex items-center justify-center rounded-lg px-1.5 py-0 text-xs font-semibold leading-[1.05rem] md:leading-[1.125rem] ` +
  "border-base-300/80 bg-base-200/80 text-base-content/65";

const overflowListItemClass =
  `${calendarEventCardClass} flex flex-col gap-1 px-2 py-1.5 ` +
  "border-base-300/80 bg-base-100 text-base-content";

const formatResponsibilityLabel = (responsibility: LocationServiceEvent["responsibility"]): string => (
  responsibility === "client" ? "Client" : "Partner"
);

const formatStatusLabel = (status: LocationServiceEvent["status"]): string => (
  status.charAt(0).toUpperCase() + status.slice(1)
);

const formatEventDateTime = (date: string, time: string): string => (
  `${formatDisplayDate(date)} at ${formatDisplayTime(time)}`
);

const formatEventDateRange = (event: LocationServiceEvent): string => (
  event.date === event.endDate
    ? formatDisplayDate(event.date)
    : `${formatDisplayDate(event.date)} - ${formatDisplayDate(event.endDate)}`
);

const ServiceEventDetailItem = (props: {label: string; value: string}) => (
  <div class="space-y-1">
    <dt class="text-xs font-semibold uppercase tracking-[0.16em] text-base-content/55">
      {props.label}
    </dt>
    <dd class="text-base-content/80">{props.value}</dd>
  </div>
);

export const requestServiceEventEdit = (
  closePopover: () => void,
  event: LocationServiceEvent,
  onEditEvent?: (event: LocationServiceEvent) => void
): void => {
  closePopover();
  onEditEvent?.(event);
};

const ServiceEventDetailPopover = (
  props: ParentProps<{
    event: LocationServiceEvent;
    canEdit: boolean;
    onEditEvent?: (event: LocationServiceEvent) => void;
  }>
) => (
  <Popover {...CALENDAR_POPOVER_PROPS}>
    {(popover) => (
      <>
        {props.children}

        <Popover.Portal>
          <Popover.Content
            class="z-50 w-[min(92vw,24rem)] rounded-2xl border border-base-300 bg-base-100 shadow-2xl"
            data-service-event-popover=""
          >
            <ServiceEventPopoverContent
              event={props.event}
              canEdit={props.canEdit}
              onEdit={props.onEditEvent}
              closePopover={() => popover.setOpen(false)}
            />
          </Popover.Content>
        </Popover.Portal>
      </>
    )}
  </Popover>
);

const ServiceEventPopoverContent = (props: ServiceEventPopoverContentProps) => (
  <div class="space-y-4 p-4 md:p-5">
    <div class="flex items-start justify-between gap-4">
      <div class="min-w-0">
        <Popover.Label class="text-base font-semibold leading-tight">
          {props.event.title}
        </Popover.Label>
      </div>

      <Show when={props.canEdit && props.onEdit}>
        <button
          type="button"
          class="btn btn-primary btn-xs"
          data-service-event-edit=""
          onClick={(event) => {
            event.stopPropagation();
            requestServiceEventEdit(props.closePopover, props.event, props.onEdit);
          }}
        >
          Edit
        </button>
      </Show>
    </div>

    <dl class="grid gap-3 text-sm md:grid-cols-2">
      <ServiceEventDetailItem
        label="Start"
        value={formatEventDateTime(props.event.date, props.event.time)}
      />
      <ServiceEventDetailItem
        label="End"
        value={formatEventDateTime(props.event.endDate, props.event.endTime)}
      />
      <ServiceEventDetailItem
        label="Responsibility"
        value={formatResponsibilityLabel(props.event.responsibility)}
      />
      <ServiceEventDetailItem
        label="Status"
        value={formatStatusLabel(props.event.status)}
      />
    </dl>

    <div class="space-y-1">
      <p class="text-xs font-semibold uppercase tracking-[0.16em] text-base-content/55">
        Description
      </p>
      <Popover.Description class="text-sm leading-6 text-base-content/80">
        {props.event.description ?? "No description provided."}
      </Popover.Description>
    </div>
  </div>
);

const ServiceEventPiece = (props: ServiceEventPieceProps) => (
  <ServiceEventDetailPopover
    event={props.piece.event}
    canEdit={props.canEdit}
    onEditEvent={props.onEditEvent}
  >
    <Popover.Trigger
      type="button"
      class={eventPieceClass(props.piece.event.responsibility, props.piece)}
      title={props.piece.event.title}
      data-service-event-bar=""
      onClick={(event) => event.stopPropagation()}
    >
      {props.piece.event.title}
    </Popover.Trigger>
  </ServiceEventDetailPopover>
);

const HiddenDayEventListItem = (props: {
  event: LocationServiceEvent;
  canEdit: boolean;
  onEditEvent?: (event: LocationServiceEvent) => void;
}) => (
  <ServiceEventDetailPopover
    event={props.event}
    canEdit={props.canEdit}
    onEditEvent={props.onEditEvent}
  >
    <Popover.Trigger
      type="button"
      class={overflowListItemClass}
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
  </ServiceEventDetailPopover>
);

const HiddenDayEventsPopover = (props: HiddenDayEventsPopoverProps) => (
  <Popover {...CALENDAR_POPOVER_PROPS}>
    <Popover.Trigger
      type="button"
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
                  onEditEvent={props.onEditEvent}
                />
              )}
            </For>
          </div>
        </div>
      </Popover.Content>
    </Popover.Portal>
  </Popover>
);

const CalendarDayCell = (props: CalendarDayCellProps) => {
  const outsideMonth = () => !isSameCalendarMonth(props.day, props.month);
  const visibleDayPieces = createMemo(() => props.weekLayout.visiblePiecesByDay[props.dayIndex] ?? []);
  const hiddenDayEvents = createMemo(() => props.weekLayout.hiddenEventsByDay[props.dayIndex] ?? []);

  return (
    <Calendar.Cell class="w-[14.2857%] p-0 align-top">
      <Calendar.CellTrigger
        as="div"
        day={props.day}
        class="relative -ml-px -mt-px flex h-[5.75rem] w-full transform-gpu flex-col items-start justify-start overflow-hidden border border-base-300/70 px-2.5 py-1.5 text-left shadow-[0_0_0_0_rgba(0,0,0,0)] transition duration-150 ease-out motion-reduce:transform-none motion-reduce:transition-none focus-visible:z-10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/40 hover:-translate-y-px hover:bg-base-100/95 hover:shadow-sm active:translate-y-px active:scale-[0.99] data-selected:border-primary/35 data-selected:bg-primary/12 data-selected:shadow-sm data-today:ring-1 data-today:ring-accent/45 md:h-[6.5rem] md:px-3 md:py-2 lg:h-[7.25rem] xl:h-[7.75rem]"
        classList={{
          "bg-base-100/85 text-base-content": !outsideMonth(),
          "bg-base-200/40 text-base-content/40": outsideMonth()
        }}
      >
        <span class="text-xs font-semibold tabular-nums md:text-sm">
          {props.day.getDate()}
        </span>

        <div class="mt-1.5 flex w-full min-h-0 flex-1 flex-col gap-0.5 overflow-hidden">
          <For each={visibleDayPieces()}>
            {(piece) => (
              <Show
                when={piece}
                fallback={<div class="h-[1.05rem] w-full md:h-[1.125rem]" aria-hidden="true" />}
              >
                {(visiblePiece) => (
                  <ServiceEventPiece
                    piece={visiblePiece()}
                    canEdit={props.canEditEvent?.(visiblePiece().event) ?? false}
                    onEditEvent={props.onEditEvent}
                  />
                )}
              </Show>
            )}
          </For>

          <Show when={hiddenDayEvents().length > 0}>
            <HiddenDayEventsPopover
              hiddenEvents={hiddenDayEvents()}
              canEditEvent={props.canEditEvent}
              onEditEvent={props.onEditEvent}
            />
          </Show>
        </div>
      </Calendar.CellTrigger>
    </Calendar.Cell>
  );
};

export const ServiceScheduleCalendar: Component<ServiceScheduleCalendarProps> = (props) => {
  return (
    <Calendar
      mode="single"
      fixedWeeks
      disableOutsideDays={false}
      startOfWeek={0}
      month={props.month}
      onMonthChange={props.onMonthChange}
    >
      {(calendar) => (
        (() => {
          const weekLayouts = createMemo(() => (
            buildServiceCalendarWeekLayouts(
              calendar.weeks,
              props.events ?? [],
              MAX_VISIBLE_WEEK_EVENT_LANES
            )
          ));

          return (
            <div class="grid h-full min-h-[42rem] grid-rows-[auto_auto_1fr] gap-5">
              <div class="flex items-center justify-between gap-3">
                <Calendar.Nav
                  action="prev-month"
                  aria-label="Go to previous month"
                  class="btn btn-sm h-11 min-h-11 w-11 min-w-11 rounded-2xl border border-base-300 bg-base-100/80 px-0 text-base-content/70 shadow-sm transition duration-150 ease-out motion-reduce:transform-none motion-reduce:transition-none hover:-translate-y-px hover:bg-base-200/80 hover:text-base-content active:translate-y-px active:scale-[0.98]"
                >
                  <CalendarChevron direction="left" />
                </Calendar.Nav>

                <div class="min-w-0 text-center">
                  <p class="text-xs font-semibold uppercase tracking-[0.22em] text-base-content/55">
                    Viewing Month
                  </p>
                  <Calendar.Label class="mt-1 text-lg font-semibold tracking-tight text-base-content md:text-2xl">
                    {formatMonthLabel(calendar.month)} {calendar.month.getFullYear()}
                  </Calendar.Label>
                </div>

                <Calendar.Nav
                  action="next-month"
                  aria-label="Go to next month"
                  class="btn btn-sm h-11 min-h-11 w-11 min-w-11 rounded-2xl border border-base-300 bg-base-100/80 px-0 text-base-content/70 shadow-sm transition duration-150 ease-out motion-reduce:transform-none motion-reduce:transition-none hover:-translate-y-px hover:bg-base-200/80 hover:text-base-content active:translate-y-px active:scale-[0.98]"
                >
                  <CalendarChevron direction="right" />
                </Calendar.Nav>
              </div>

              <div class="flex flex-wrap items-center gap-2 text-xs text-base-content/70">
                <Show when={props.isLoading}>
                  <span class="rounded-full border border-base-300 bg-base-100 px-2.5 py-1 font-medium">
                    Syncing visible months...
                  </span>
                </Show>
                <Show when={props.error}>
                  <span class="rounded-full border border-error/25 bg-error/10 px-2.5 py-1 text-error">
                    {props.error}
                  </span>
                </Show>
                <Show when={props.error && props.onRetry}>
                  <button type="button" class="btn btn-xs btn-outline" onClick={props.onRetry}>
                    Retry
                  </button>
                </Show>
              </div>

              <div class="min-h-0 overflow-hidden rounded-2xl border border-base-300/80 bg-base-200/25 shadow-inner">
                <Calendar.Table
                  aria-label="Service schedule calendar"
                  class="h-full w-full table-fixed border-collapse"
                >
                  <thead class="bg-base-200/55">
                    <tr class="h-12">
                      <Index each={calendar.weekdays}>
                        {(weekday) => (
                          <Calendar.HeadCell
                            abbr={formatWeekdayLong(weekday())}
                            class="border-b border-base-300/80 px-3 py-3 text-left text-[11px] font-semibold uppercase tracking-[0.18em] text-base-content/60 md:px-4 md:text-xs"
                          >
                            {formatWeekdayShort(weekday())}
                          </Calendar.HeadCell>
                        )}
                      </Index>
                    </tr>
                  </thead>

                  <tbody>
                    <Index each={calendar.weeks}>
                      {(week, weekIndex) => (
                        <tr class="h-[5.75rem] md:h-[6.5rem] lg:h-[7.25rem] xl:h-[7.75rem]">
                          <Index each={week()}>
                            {(day, dayIndex) => (
                              <CalendarDayCell
                                day={day()}
                                month={calendar.month}
                                dayIndex={dayIndex}
                                weekLayout={weekLayouts()[weekIndex]}
                                canEditEvent={props.canEditEvent}
                                onEditEvent={props.onEditEvent}
                              />
                            )}
                          </Index>
                        </tr>
                      )}
                    </Index>
                  </tbody>
                </Calendar.Table>
              </div>
            </div>
          );
        })()
      )}
    </Calendar>
  );
};

export default ServiceScheduleCalendar;
