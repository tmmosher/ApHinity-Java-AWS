import Popover from "corvu/popover";
import type {Component} from "solid-js";

type ServiceCalendarIntroPopoverProps = {
  templateHref: string;
};

type ServiceCalendarIntroPopoverContentProps = {
  templateHref: string;
};

const INTRO_POPOVER_PROPS = {
  placement: "bottom-start" as const,
  trapFocus: false,
  restoreFocus: false,
  closeOnOutsideFocus: false
};

const introTriggerClass =
  "btn btn-primary h-11 min-h-11 rounded-2xl px-4 text-sm font-medium shadow-sm transition duration-150 ease-out " +
  "motion-reduce:transform-none motion-reduce:transition-none hover:-translate-y-px active:translate-y-px active:scale-[0.98]";

export const ServiceCalendarIntroPopoverContent: Component<ServiceCalendarIntroPopoverContentProps> = (props) => (
  <div class="space-y-4 p-4" data-service-calendar-intro-content="">
    <div class="space-y-1">
      <h3 class="text-sm font-semibold">How Service Calendar Works</h3>
      <p class="text-sm leading-6 text-base-content/70">
        View previous, current, and upcoming service events. The calendar loads the previous,
        current, and next month relative to the month you are viewing. Click an empty day cell to
        create a service event.
      </p>
    </div>

    <div class="space-y-2 rounded-xl border border-base-300/80 bg-base-100 px-3 py-3">
      <p class="text-xs font-medium uppercase tracking-[0.16em] text-base-content/55">Template</p>
      <p class="text-sm leading-6 text-base-content/75">
        Use the Excel template to draft or share service calendar items before entering them here.
      </p>
      <a
        class="btn btn-outline btn-sm rounded-xl"
        href={props.templateHref}
        download="service_calendar_template.xlsx"
      >
        Get a copy of the Excel template
      </a>
    </div>

    <div class="space-y-2 rounded-xl border border-base-300/80 bg-base-100 px-3 py-3">
      <p class="text-xs font-medium uppercase tracking-[0.16em] text-base-content/55">Legend</p>
      <div class="flex flex-wrap items-center gap-2 text-xs font-medium">
        <span class="rounded-full border border-[#f59e0b]/35 bg-[#f59e0b]/18 px-2.5 py-1 text-[#9a3412]">
          Client
        </span>
        <span class="rounded-full border border-[#86efac] bg-[#dcfce7] px-2.5 py-1 text-[#166534]">
          Partner
        </span>
      </div>
    </div>
  </div>
);

export const ServiceCalendarIntroPopover: Component<ServiceCalendarIntroPopoverProps> = (props) => (
  <Popover {...INTRO_POPOVER_PROPS}>
    <Popover.Trigger
      type="button"
      class={introTriggerClass}
      aria-label="Open service calendar guide"
      data-service-calendar-intro-trigger=""
    >
      Get started
    </Popover.Trigger>

    <Popover.Portal>
      <Popover.Content class="z-50 w-[min(92vw,28rem)] rounded-2xl border border-base-300 bg-base-100 shadow-xl">
        <ServiceCalendarIntroPopoverContent templateHref={props.templateHref} />
      </Popover.Content>
    </Popover.Portal>
  </Popover>
);

export default ServiceCalendarIntroPopover;
