import {DefaultColorPicker} from "@thednp/solid-color-picker";
import "@thednp/solid-color-picker/style.css";
import Popover from "corvu/popover";
import {For, Show, createSignal} from "solid-js";
import {getDocumentThemePreference} from "../../util/common/themePreference";

type GraphColorPickerProps = {
  value: string;
  colorOptions: Record<string, string>;
  disabled?: boolean;
  onChange: (colorHex: string) => void;
};

const GraphColorPicker = (props: GraphColorPickerProps) => {
  const [isOpen, setIsOpen] = createSignal(false);
  const [pickerResetKey, setPickerResetKey] = createSignal(0);
  let popoverContent: HTMLDivElement | undefined;
  const colorOptionEntries = () => Object.entries(props.colorOptions);
  const pickerValue = () =>
    props.value || Object.values(props.colorOptions)[0] || "#1f77b4";
  const selectedPresetValue = () => (
    colorOptionEntries().some(([, color]) => color === pickerValue()) ? pickerValue() : ""
  );
  const pickerTheme = getDocumentThemePreference();

  const applyPresetColor = (colorHex: string) => {
    if (!colorHex || props.disabled) {
      return;
    }
    if (colorHex !== props.value) {
      props.onChange(colorHex);
    }
    setPickerResetKey((key) => key + 1);
  };

  return (
    <fieldset disabled={props.disabled} class="min-w-0" data-graph-color-picker="">
      <div class="mt-1 flex min-w-0 gap-2">
        <div class="min-w-0 flex-1">
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
              class="input input-bordered input-sm flex w-full min-w-0 items-center gap-2 text-left"
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
                <Show when={pickerValue()} keyed>
                  {(value) => (
                    <DefaultColorPicker
                      value={value}
                      format="hex"
                      theme={pickerTheme}
                      colorKeywords={[]}
                      class="graph-color-picker"
                      onChange={(colorHex) => {
                        if (!props.disabled && colorHex !== props.value) {
                          props.onChange(colorHex);
                        }
                      }}
                    />
                  )}
                </Show>
              </Popover.Content>
            </Popover.Portal>
          </Popover>
        </div>

        <select
          class="select select-bordered select-sm min-w-0 flex-1"
          value={selectedPresetValue()}
          onChange={(event) => applyPresetColor(event.currentTarget.value)}
          aria-label="Preset color"
        >
          <option value="">Custom</option>
          <For each={colorOptionEntries()}>
            {([label, color]) => (
              <option value={color}>{label}</option>
            )}
          </For>
        </select>
      </div>
    </fieldset>
  );
};

export default GraphColorPicker;
