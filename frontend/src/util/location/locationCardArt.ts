import {LocationSummary} from "../../types/Types";

const hashLocationKey = (value: string): number => {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) {
    hash = ((hash << 5) - hash + value.charCodeAt(index)) | 0;
  }
  return Math.abs(hash);
};

const toHueColor = (hue: number, saturation: number, lightness: number): string =>
  `hsl(${hue} ${saturation}% ${lightness}%)`;

export const getLocationCardArtStyle = (
  location: Pick<LocationSummary, "id" | "name">
): Record<string, string> => {
  const seed = hashLocationKey(`${location.id}:${location.name}`);
  const baseHue = seed % 360;
  const accentHue = (baseHue + 38 + (seed % 23)) % 360;
  const glowHue = (baseHue + 168) % 360;

  return {
    backgroundImage: [
      `radial-gradient(circle at 18% 18%, hsla(${glowHue}, 92%, 82%, 0.38), transparent 28%)`,
      `radial-gradient(circle at 82% 14%, hsla(${accentHue}, 88%, 72%, 0.22), transparent 24%)`,
      `radial-gradient(circle at 50% 100%, hsla(${baseHue}, 92%, 74%, 0.16), transparent 32%)`,
      `linear-gradient(135deg, ${toHueColor(baseHue, 82, 54)}, ${toHueColor(accentHue, 74, 44)} 58%, ${toHueColor(glowHue, 76, 32)})`
    ].join(", "),
    backgroundPosition: "center",
    backgroundSize: "cover"
  };
};
