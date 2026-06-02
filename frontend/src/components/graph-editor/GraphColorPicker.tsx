import {DefaultColorPicker} from "@thednp/solid-color-picker";
import "@thednp/solid-color-picker/style.css";
import {Show} from "solid-js";

type GraphColorPickerProps = {
  value: string;
  colorOptions: Record<string, string>;
  disabled?: boolean;
  onChange: (colorHex: string) => void;
};

const GraphColorPicker = (props: GraphColorPickerProps) => {
  const colorKeywords = () =>
    Object.entries(props.colorOptions).map(([label, color]) => ({[label]: color}));
  const pickerValue = () =>
    props.value || Object.values(props.colorOptions)[0] || "#1f77b4";

  return (
    <fieldset disabled={props.disabled} class="min-w-0" data-graph-color-picker="">
      <Show when={pickerValue()} keyed>
        {(value) => (
          <DefaultColorPicker
            value={value}
            format="hex"
            theme="light"
            colorKeywords={colorKeywords()}
            class="w-full"
            onChange={(colorHex) => {
              if (!props.disabled && colorHex !== props.value) {
                props.onChange(colorHex);
              }
            }}
          />
        )}
      </Show>
    </fieldset>
  );
};

export default GraphColorPicker;
