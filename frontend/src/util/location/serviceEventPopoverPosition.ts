export const SERVICE_EVENT_POPOVER_POSITION_PROPS = {
  placement: "bottom-start" as const,
  strategy: "fixed" as const,
  floatingOptions: {
    flip: true,
    shift: {
      mainAxis: false,
      crossAxis: true
    }
  }
};
