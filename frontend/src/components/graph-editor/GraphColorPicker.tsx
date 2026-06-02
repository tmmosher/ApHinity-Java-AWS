import {DefaultColorPicker} from "@thednp/solid-color-picker";
import "@thednp/solid-color-picker/style.css";
import Popover from "corvu/popover";
import {createSignal} from "solid-js";
import {getDocumentThemePreference} from "../../util/common/themePreference";

type GraphColorPickerProps = {
  value: string;
  colorOptions: Record<string, string>;
  disabled?: boolean;
  onChange: (colorHex: string) => void;
};

const GraphColorPicker = (props: GraphColorPickerProps) => {
  const [isOpen, setIsOpen] = createSignal(false);
  let popoverContent: HTMLDivElement | undefined;
  const colorKeywords =
    Object.entries(props.colorOptions).map(([label, color]) => (
      {[label]: color} as Record<string, string>
    ));
  const pickerValue = () =>
    props.value || Object.values(props.colorOptions)[0] || "#1f77b4";
  const pickerTheme = getDocumentThemePreference();

  return (
    <fieldset disabled={props.disabled} class="min-w-0" data-graph-color-picker="">
      <Popover
        placement="bottom-start"
        trapFocus={false}
        restoreFocus={false}
        closeOnOutsideFocus={false}
        open={isOpen()}
        onOpenChange={(open) => {
          setIsOpen(open);
          if (open) {
            setTimeout(() => {
              popoverContent?.querySelector<HTMLButtonElement>(".picker-toggle")?.click();
            });
          }
        }}
      >
        <Popover.Trigger
          type="button"
          class="input input-bordered input-sm mt-1 flex w-full min-w-0 items-center gap-2 text-left"
        >
          <span
            class="h-4 w-4 shrink-0 rounded-sm border border-base-content/25"
            style={{"background-color": pickerValue()}}
          />
          <span class="min-w-0 truncate">{pickerValue()}</span>
        </Popover.Trigger>

        <Popover.Portal>
          <Popover.Content
            ref={popoverContent}
            class="graph-color-picker-popover-content z-[80] w-[min(92vw,22rem)] rounded-lg border border-base-300 bg-base-100 p-2 shadow-xl"
          >
            <DefaultColorPicker
              value={pickerValue()}
              format="hex"
              theme={pickerTheme}
              colorKeywords={colorKeywords}
              class="graph-color-picker"
              onChange={(colorHex) => {
                if (!props.disabled && colorHex !== props.value) {
                  props.onChange(colorHex);
                }
              }}
            />
          </Popover.Content>
        </Popover.Portal>
      </Popover>
    </fieldset>
  );
};

export default GraphColorPicker;
