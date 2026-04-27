import {A} from "@solidjs/router";
import {createSignal, Show} from "solid-js";
import {LocationSummary} from "../../types/Types";
import {getLocationCardArtStyle, getLocationThumbnailUrl} from "../../util/location/locationCardArt";
import LocationOverviewCardOverflowMenu from "./LocationOverviewCardOverflowMenu";

type LocationOverviewCardProps = {
  location: LocationSummary;
  apiHost: string;
  isFavorite: boolean;
  canEditLocations: boolean;
  onFavorite: (locationId: number | null) => void;
  onRename: (locationId: number, nextName: string) => Promise<boolean>;
  onThumbnailUpload: (locationId: number, file: File) => Promise<boolean>;
};

const StarFilledIcon = () => (
  <svg
    aria-hidden="true"
    xmlns="http://www.w3.org/2000/svg"
    width="18"
    height="18"
    viewBox="0 0 24 24"
    fill="currentColor"
    stroke="currentColor"
    stroke-width="2"
    stroke-linecap="round"
    stroke-linejoin="round"
  >
    <path d="m12 2 3.09 6.26L22 9.27l-5 4.87L18.18 22 12 18.56 5.82 22 7 14.14 2 9.27l6.91-1.01L12 2z" />
  </svg>
);

const StarOutlineIcon = () => (
  <svg
    aria-hidden="true"
    xmlns="http://www.w3.org/2000/svg"
    width="18"
    height="18"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="2"
    stroke-linecap="round"
    stroke-linejoin="round"
  >
    <path d="m12 2 3.09 6.26L22 9.27l-5 4.87L18.18 22 12 18.56 5.82 22 7 14.14 2 9.27l6.91-1.01L12 2z" />
  </svg>
);

const formatUpdatedAt = (updatedAt: string): string =>
  new Date(updatedAt).toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric"
  });

export const LocationOverviewCard = (props: LocationOverviewCardProps) => {
  const [thumbnailError, setThumbnailError] = createSignal(false);

  return (
    <article class="group relative overflow-hidden rounded-3xl border border-base-300 bg-base-100 shadow-sm transition duration-150 ease-out hover:-translate-y-1 hover:shadow-xl">
      <div class="absolute right-3 top-3 z-20 flex items-center gap-2">
        <button
          type="button"
          class={
            "btn btn-circle btn-sm border border-base-300 bg-base-100/90 shadow-sm backdrop-blur " +
            (props.isFavorite ? "text-warning" : "text-base-content/70")
          }
          aria-label={props.isFavorite ? "Remove favorite location" : "Set favorite location"}
          title={props.isFavorite ? "Remove favorite location" : "Set favorite location"}
          aria-pressed={props.isFavorite}
          onClick={() => props.onFavorite(props.isFavorite ? null : props.location.id)}
        >
          {props.isFavorite ? <StarFilledIcon /> : <StarOutlineIcon />}
        </button>
        <LocationOverviewCardOverflowMenu
          location={props.location}
          canEditLocations={props.canEditLocations}
          onRename={props.onRename}
          onUploadThumbnail={props.onThumbnailUpload}
        />
      </div>

      <A
        href={`/dashboard/locations/${props.location.id}`}
        preload
        class="relative z-0 flex h-full min-h-[19rem] flex-col overflow-hidden"
        aria-label={`Open location ${props.location.name}`}
      >
        <div class="relative basis-[68%] overflow-hidden bg-base-200">
          <div
            class="absolute inset-0 z-0 transition duration-300 ease-out group-hover:scale-[1.03]"
            style={getLocationCardArtStyle(props.location)}
          />
          <Show when={props.location.thumbnailAvailable && !thumbnailError()}>
            <img
              src={getLocationThumbnailUrl(props.location.id, props.apiHost)}
              alt=""
              aria-hidden="true"
              loading="lazy"
              decoding="async"
              class="absolute inset-0 z-10 h-full w-full object-cover transition duration-300 ease-out group-hover:scale-[1.03]"
              onError={() => setThumbnailError(true)}
            />
          </Show>
          <div class="absolute inset-0 z-20 bg-[radial-gradient(circle_at_top_right,_rgba(255,255,255,0.18),_transparent_35%),radial-gradient(circle_at_bottom_left,_rgba(255,255,255,0.10),_transparent_36%)]" />
          <div class="absolute inset-x-0 bottom-0 z-20 h-20 bg-gradient-to-t from-base-100/25 via-transparent to-transparent" />
        </div>

        <div class="flex basis-[32%] flex-col justify-end gap-1 bg-gradient-to-b from-base-100/95 to-base-100 p-5">
          <h3 class="text-xl font-semibold tracking-tight text-base-content">
            {props.location.name}
          </h3>
          <p class="text-xs text-base-content/60">
            Updated {formatUpdatedAt(props.location.updatedAt)}
          </p>
        </div>
      </A>
    </article>
  );
};
