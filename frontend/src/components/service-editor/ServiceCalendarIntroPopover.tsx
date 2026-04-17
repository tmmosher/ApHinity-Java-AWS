import type {Component} from "solid-js";
import {GetStartedPopover} from "../help/GetStartedPopover";

type ServiceCalendarIntroPopoverProps = {
  templateHref: string;
};

type ServiceCalendarIntroPopoverContentProps = {
  templateHref: string;
};

export const ServiceCalendarIntroPopoverContent: Component<ServiceCalendarIntroPopoverContentProps> = (props) => (
  <div class="space-y-4 p-4" data-service-calendar-intro-content="">
    <div class="space-y-1">
      <h3 class="text-sm font-semibold">How to use the service calendar</h3>
      <p class="text-sm leading-6 text-base-content/70">
        Users can view previous, current, and upcoming events on the service calendar.
        To add new events, fill out the Excel file below and upload. Alternatively, click an empty day cell to create a service event.
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
   <GetStartedPopover>
     <ServiceCalendarIntroPopoverContent templateHref={props.templateHref} />
   </GetStartedPopover>
);

export default ServiceCalendarIntroPopover;
