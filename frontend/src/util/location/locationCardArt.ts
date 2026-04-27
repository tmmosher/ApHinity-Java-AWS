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
  location: Pick<LocationSummary, "id" | "name" | "thumbnailAvailable">
): Record<string, string> => {
  const seed = hashLocationKey(`${location.id}:${location.name}`);
  const baseHue = seed % 360;
  const accentHue = (baseHue + 38 + (seed % 23)) % 360;
  const glowHue = (baseHue + 168) % 360;
  const hasThumbnail = Boolean(location.thumbnailAvailable);

  const layers = [
    `radial-gradient(circle at 18% 18%, hsla(${glowHue}, 92%, 82%, ${hasThumbnail ? 0.24 : 0.38}), transparent 28%)`,
    `radial-gradient(circle at 82% 14%, hsla(${accentHue}, 88%, 72%, ${hasThumbnail ? 0.16 : 0.22}), transparent 24%)`,
    `radial-gradient(circle at 50% 100%, hsla(${baseHue}, 92%, 74%, ${hasThumbnail ? 0.10 : 0.16}), transparent 32%)`,
    hasThumbnail
      ? `linear-gradient(135deg, hsla(${baseHue}, 82%, 54%, 0.62), hsla(${accentHue}, 74%, 44%, 0.50) 58%, hsla(${glowHue}, 76%, 32%, 0.42))`
      : `linear-gradient(135deg, ${toHueColor(baseHue, 82, 54)}, ${toHueColor(accentHue, 74, 44)} 58%, ${toHueColor(glowHue, 76, 32)})`
  ];

  return {
    backgroundImage: layers.join(", "),
    backgroundPosition: "center",
    backgroundSize: "cover"
  };
};

export const getLocationThumbnailUrl = (locationId: number, apiHost: string): string =>
  new URL("/api/core/locations/" + locationId + "/thumbnail", apiHost).toString();
