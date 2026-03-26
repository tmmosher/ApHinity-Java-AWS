import Calendar from "corvu/calendar";
import {Index, type VoidComponent} from "solid-js";

const monthFormatter = new Intl.DateTimeFormat("en-US", {
  month: "long"
});

const weekdayLongFormatter = new Intl.DateTimeFormat("en-US", {
  weekday: "long"
});

const weekdayShortFormatter = new Intl.DateTimeFormat("en-US", {
  weekday: "short"
});

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

const isSameCalendarMonth = (left: Date, right: Date): boolean =>
  left.getFullYear() === right.getFullYear() &&
  left.getMonth() === right.getMonth();

export const ServiceScheduleCalendar: VoidComponent = () => (
  <Calendar mode="single" fixedWeeks disableOutsideDays={false} startOfWeek={0}>
    {(props) => (
      <div class="grid h-full min-h-[42rem] grid-rows-[auto_1fr] gap-5">
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
              {monthFormatter.format(props.month)} {props.month.getFullYear()}
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

        <div class="min-h-0 overflow-hidden rounded-2xl border border-base-300/80 bg-base-200/25 shadow-inner">
          <Calendar.Table
            aria-label="Service schedule calendar"
            class="h-full w-full table-fixed border-collapse"
          >
            <thead class="bg-base-200/55">
              <tr class="h-12">
                <Index each={props.weekdays}>
                  {(weekday) => (
                    <Calendar.HeadCell
                      abbr={weekdayLongFormatter.format(weekday())}
                      class="border-b border-base-300/80 px-3 py-3 text-left text-[11px] font-semibold uppercase tracking-[0.18em] text-base-content/60 md:px-4 md:text-xs"
                    >
                      {weekdayShortFormatter.format(weekday())}
                    </Calendar.HeadCell>
                  )}
                </Index>
              </tr>
            </thead>

            <tbody>
              <Index each={props.weeks}>
                {(week) => (
                  <tr class="h-[6.25rem] md:h-[7.25rem] lg:h-[8rem] xl:h-[8.75rem]">
                    <Index each={week()}>
                      {(day) => {
                        const outsideMonth = () => !isSameCalendarMonth(day(), props.month);

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

export default ServiceScheduleCalendar;
