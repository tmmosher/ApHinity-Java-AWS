import {ColorPicker, parseColor, type Color} from "@ark-ui/solid";
import Popover from "corvu/popover";
import {For, createMemo, createSignal} from "solid-js";

type GraphColorPickerProps = {
  value: string;
  colorOptions: Record<string, string>;
  disabled?: boolean;
  onChange: (colorHex: string) => void;
};

const FALLBACK_COLOR = "#1f77b4";

const parsePickerColor = (color: string): Color => {
  try {
    return parseColor(color);
  } catch {
    return parseColor(FALLBACK_COLOR);
  }
};

const toGraphColor = (color: Color): string => {
  const rgbColor = color.toFormat("rgba");
  return rgbColor.getChannelValue("alpha") >= 1 ? rgbColor.toString("hex") : rgbColor.toString("hexa");
};

const GraphColorPicker = (props: GraphColorPickerProps) => {
  const [isOpen, setIsOpen] = createSignal(false);
  const colorOptionEntries = () => Object.entries(props.colorOptions);
  const pickerValue = () =>
    props.value || Object.values(props.colorOptions)[0] || FALLBACK_COLOR;
  const pickerColor = createMemo(() => parsePickerColor(pickerValue()));
  const selectedPresetValue = () => (
    colorOptionEntries().some(([, color]) => color === pickerValue()) ? pickerValue() : ""
  );

  const applyPresetColor = (colorHex: string) => {
    if (!colorHex || props.disabled) {
      return;
    }
    if (colorHex !== props.value) {
      props.onChange(colorHex);
    }
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
            onOpenChange={setIsOpen}
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
              <Popover.Content class="graph-color-picker-popover-content z-[80] w-[min(92vw,22rem)] rounded-lg border border-base-300 bg-base-100 p-3 shadow-xl">
                <ColorPicker.Root
                  class="graph-color-picker"
                  value={pickerColor()}
                  format="rgba"
                  inline
                  disabled={props.disabled}
                  onValueChange={({value}) => {
                    const nextColor = toGraphColor(value);
                    if (!props.disabled && nextColor !== props.value) {
                      props.onChange(nextColor);
                    }
                  }}
                >
                  <ColorPicker.HiddenInput />
                  <ColorPicker.Content class="space-y-3">
                    <ColorPicker.Area class="graph-color-picker-area">
                      <ColorPicker.AreaBackground class="graph-color-picker-area-background" />
                      <ColorPicker.AreaThumb class="graph-color-picker-thumb" />
                    </ColorPicker.Area>

                    <ColorPicker.ChannelSlider class="graph-color-picker-slider" channel="hue">
                      <ColorPicker.ChannelSliderTrack class="graph-color-picker-slider-track" />
                      <ColorPicker.ChannelSliderThumb class="graph-color-picker-thumb" />
                    </ColorPicker.ChannelSlider>

                    <ColorPicker.ChannelSlider class="graph-color-picker-slider" channel="alpha">
                      <ColorPicker.TransparencyGrid />
                      <ColorPicker.ChannelSliderTrack class="graph-color-picker-slider-track" />
                      <ColorPicker.ChannelSliderThumb class="graph-color-picker-thumb" />
                    </ColorPicker.ChannelSlider>

                    <ColorPicker.ChannelInput
                      class="input input-bordered input-sm w-full"
                      channel="hex"
                      aria-label="Custom color"
                    />
                  </ColorPicker.Content>
                </ColorPicker.Root>
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
