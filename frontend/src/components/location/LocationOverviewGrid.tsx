import {For, Show, createMemo, type Resource} from "solid-js";
import {LocationSummary} from "../../types/Types";
import {LocationOverviewCard} from "./LocationOverviewCard";

type LocationOverviewGridProps = {
  title: string;
  description: string;
  locations: Resource<LocationSummary[] | undefined>;
  favoriteLocationId: string;
  canEditLocations: boolean;
  emptyMessage: string;
  displayLocations?: LocationSummary[];
  onFavorite: (locationId: number | null) => void;
  onRename: (locationId: number, nextName: string) => Promise<boolean>;
  onRetry: () => void;
};

const sortLocationsForOverview = (
  locations: LocationSummary[],
  favoriteLocationId: string
): LocationSummary[] => {
  const normalizedFavoriteId = favoriteLocationId.trim();
  return [...locations].sort((left, right) => {
    const leftFavorite = String(left.id) === normalizedFavoriteId;
    const rightFavorite = String(right.id) === normalizedFavoriteId;
    if (leftFavorite !== rightFavorite) {
      return leftFavorite ? -1 : 1;
    }
    return left.name.localeCompare(right.name, undefined, {
      sensitivity: "base"
    });
  });
};

export const LocationOverviewGrid = (props: LocationOverviewGridProps) => {
  const orderedLocations = createMemo(() =>
    props.displayLocations ?? sortLocationsForOverview(props.locations() ?? [], props.favoriteLocationId)
  );

  return (
    <section class="space-y-4">
      <div class="space-y-1">
        <h2 class="text-2xl font-semibold tracking-tight">{props.title}</h2>
        <p class="text-sm text-base-content/70">{props.description}</p>
      </div>

      <Show when={!props.locations.loading} fallback={<p class="text-base-content/70">Loading locations...</p>}>
        <Show
          when={!props.locations.error}
          fallback={
            <div class="space-y-3">
              <p class="text-error">Unable to load locations.</p>
              <button type="button" class="btn btn-outline" onClick={() => props.onRetry()}>
                Retry
              </button>
            </div>
          }
        >
          <Show
            when={orderedLocations().length > 0}
            fallback={
              <div class="space-y-3">
                <p class="text-base-content/70">{props.emptyMessage}</p>
                <button type="button" class="btn btn-outline" onClick={() => props.onRetry()}>
                  Retry
                </button>
              </div>
            }
          >
            <div class="grid gap-4 md:grid-cols-2">
              <For each={orderedLocations()}>
                {(location) => (
                  <LocationOverviewCard
                    location={location}
                    isFavorite={String(location.id) === props.favoriteLocationId}
                    canEditLocations={props.canEditLocations}
                    onFavorite={props.onFavorite}
                    onRename={props.onRename}
                  />
                )}
              </For>
            </div>
          </Show>
        </Show>
      </Show>
    </section>
  );
};
