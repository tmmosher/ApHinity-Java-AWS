import Calendar from "corvu/calendar";
import {For, Index, Show, createMemo, type Component} from "solid-js";
import type {LocationServiceEvent} from "../../../../types/Types";
import {
  formatDateInputValue,
  formatMonthLabel,
  formatWeekdayLong,
  formatWeekdayShort,
  isSameCalendarMonth,
  listDateRangeInclusive,
  parseDateInputValue
} from "../../../../util/location/dateUtility";

type ServiceScheduleCalendarProps = {
  month?: Date;
  onMonthChange?: (month: Date) => void;
  events?: LocationServiceEvent[];
  isLoading?: boolean;
  error?: string;
  onRetry?: () => void;
};

type DayEventBar = {
  id: number;
  title: string;
  responsibility: LocationServiceEvent["responsibility"];
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

const createDayEventBar = (event: LocationServiceEvent): DayEventBar => ({
  id: event.id,
  title: event.title,
  responsibility: event.responsibility
});

const listEventDays = (event: LocationServiceEvent): Date[] => {
  const startDay = parseDateInputValue(event.date);
  const endDay = parseDateInputValue(event.endDate);
  return listDateRangeInclusive(startDay, endDay);
};

const appendDayEventBar = (
  eventsByDay: Map<string, DayEventBar[]>,
  day: Date,
  eventBar: DayEventBar
): void => {
  const dayKey = formatDateInputValue(day);
  const dayEvents = eventsByDay.get(dayKey) ?? [];
  dayEvents.push(eventBar);
  eventsByDay.set(dayKey, dayEvents);
};

const buildDayEventMap = (events: readonly LocationServiceEvent[]): Map<string, DayEventBar[]> => {
  const eventsByDay = new Map<string, DayEventBar[]>();

  for (const event of events) {
    const eventBar = createDayEventBar(event);
    for (const day of listEventDays(event)) {
      appendDayEventBar(eventsByDay, day, eventBar);
    }
  }

  return eventsByDay;
};

const eventBarClass = (responsibility: LocationServiceEvent["responsibility"]): string =>
  "w-full truncate rounded-lg border px-2 py-1 text-[10px] font-semibold leading-4 shadow-sm md:text-[11px] " +
  (responsibility === "client"
    ? "border-[#f59e0b]/40 bg-[#f59e0b]/18 text-[#9a3412]"
    : "border-[#86efac] bg-[#dcfce7] text-[#166534]");

export const ServiceScheduleCalendar: Component<ServiceScheduleCalendarProps> = (props) => {
  const eventsByDay = createMemo(() => buildDayEventMap(props.events ?? []));

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
                  {(week) => (
                    <tr class="h-[6.25rem] md:h-[7.25rem] lg:h-[8rem] xl:h-[8.75rem]">
                      <Index each={week()}>
                        {(day) => {
                          const outsideMonth = () => !isSameCalendarMonth(day(), calendar.month);
                          const dayEvents = createMemo(() => eventsByDay().get(formatDateInputValue(day())) ?? []);
                          const visibleDayEvents = createMemo(() => dayEvents().slice(0, 3));
                          const hiddenEventCount = createMemo(() => Math.max(dayEvents().length - visibleDayEvents().length, 0));

                          return (
                            <Calendar.Cell class="w-[14.2857%] p-0 align-top">
                              <Calendar.CellTrigger
                                day={day()}
                                class="relative -ml-px -mt-px flex h-full min-h-[6.25rem] w-full transform-gpu flex-col items-start justify-start overflow-hidden border border-base-300/70 px-3 py-2 text-left shadow-[0_0_0_0_rgba(0,0,0,0)] transition duration-150 ease-out motion-reduce:transform-none motion-reduce:transition-none focus-visible:z-10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/40 hover:-translate-y-px hover:bg-base-100/95 hover:shadow-sm active:translate-y-px active:scale-[0.99] data-selected:border-primary/35 data-selected:bg-primary/12 data-selected:shadow-sm data-today:ring-1 data-today:ring-accent/45 md:px-4 md:py-3"
                                classList={{
                                  "bg-base-100/85 text-base-content": !outsideMonth(),
                                  "bg-base-200/40 text-base-content/40": outsideMonth()
                                }}
                              >
                                <span class="text-sm font-semibold tabular-nums md:text-base">
                                  {day().getDate()}
                                </span>

                                <div class="mt-2 flex w-full flex-1 flex-col gap-1 overflow-hidden">
                                  <For each={visibleDayEvents()}>
                                    {(event) => (
                                      <span
                                        class={eventBarClass(event.responsibility)}
                                        title={event.title}
                                        data-service-event-bar=""
                                      >
                                        {event.title}
                                      </span>
                                    )}
                                  </For>

                                  <Show when={hiddenEventCount() > 0}>
                                    <span class="rounded-md bg-base-200/80 px-2 py-1 text-[10px] font-semibold text-base-content/65">
                                      +{hiddenEventCount()} more
                                    </span>
                                  </Show>
                                </div>
                              </Calendar.CellTrigger>
                            </Calendar.Cell>
                          );
                        }}
                      </Index>
                    </tr>
                  )}
                </Index>
              </tbody>
            </Calendar.Table>
          </div>
        </div>
      )}
    </Calendar>
  );
};

export default ServiceScheduleCalendar;
