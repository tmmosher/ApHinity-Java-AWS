import {Component, JSX} from "solid-js";
import Popover from "corvu/popover";

const HELP_POPOVER_PROPS = {
  placement: "bottom-start" as const,
  trapFocus: false,
  restoreFocus: false,
  closeOnOutsideFocus: false,
};

type GetStartedPopoverProps = {
  children: JSX.Element;
}

const introTriggerClass =
  "btn btn-primary h-11 min-h-11 rounded-2xl px-4 text-sm font-medium shadow-sm transition duration-150 ease-out " +
  "motion-reduce:transform-none motion-reduce:transition-none hover:-translate-y-px active:translate-y-px active:scale-[0.98]";

export const GetStartedPopover: Component<GetStartedPopoverProps> = (props) => {
  return (
    <Popover {...HELP_POPOVER_PROPS}>
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
          {props.children}
        </Popover.Content>
      </Popover.Portal>
    </Popover>
  );
}