import {A, useLocation, useParams} from "@solidjs/router";
import {For, Show, createMemo, createResource, type ParentProps} from "solid-js";
import {useApiHost} from "../../../context/ApiHostContext";
import type {LocationSummary} from "../../../types/Types";
import {fetchLocationById} from "../../../util/graph/locationDetailApi";
import {LocationDetailProvider} from "./location/LocationDetailContext";
import {
  dashboardLocationViews,
  getFreshLocationScopedValue,
  getLocationViewFromPathname,
  getLocationViewHref,
  type LocationScopedResource
} from "./location/locationView";

const locationViewButtonClass = (active: boolean): string =>
  "flex min-h-11 items-center justify-center px-3 py-2 text-center text-xs font-semibold tracking-tight transition-colors duration-150 sm:text-sm " +
  (active
    ? "bg-primary text-primary-content shadow-sm"
    : "bg-transparent text-base-content/70 hover:bg-base-100/70");

export const DashboardLocationDetailPanel = (props: ParentProps) => {
  const host = useApiHost();
  const routeLocation = useLocation();
  const params = useParams<{ locationId: string }>();
  const currentView = createMemo(() =>
    getLocationViewFromPathname(routeLocation.pathname, params.locationId)
  );

  const [locationResource, {refetch: refetchLocation}] = createResource(
    () => params.locationId,
    async (locationId): Promise<LocationScopedResource<LocationSummary>> => ({
      locationId,
      value: await fetchLocationById(host, locationId)
    })
  );
  const location = createMemo(() => getFreshLocationScopedValue(params.locationId, locationResource()));
  const refetchLocationDetail = async (): Promise<void> => {
    await refetchLocation();
  };

  const retryLocation = () => {
    void refetchLocationDetail();
  };

  return (
    <main class="w-full" aria-label="Authenticated dashboard">
      <div class="w-full max-w-6xl mx-auto">
        <div class="space-y-6">
          <Show
            when={!locationResource.error}
            fallback={
              <div class="space-y-3" role="alert" aria-live="assertive">
                <p class="text-error">Unable to load location dashboard.</p>
                <div class="flex gap-2">
                  <button type="button" class="btn btn-outline" onClick={retryLocation}>
                    Retry
                  </button>
                  <A href="/dashboard/locations" class="btn btn-ghost">
                    Back to locations
                  </A>
                </div>
              </div>
            }
          >
            <Show
              when={location()}
              fallback={<p class="text-base-content/70">Loading location...</p>}
            >
              <LocationDetailProvider location={location} refetchLocation={refetchLocationDetail}>
                <header class="flex flex-col gap-3 sm:flex-row sm:items-center sm:gap-4">
                  <div class="min-w-0 space-y-1">
                    <p class="text-xs font-semibold uppercase tracking-wide text-base-content/60">
                      Location
                    </p>
                    <h1 class="text-3xl font-semibold tracking-tight">
                      {location()?.name}
                    </h1>
                  </div>

                  <nav
                    class="w-fit max-w-full overflow-hidden rounded-2xl border border-base-300 bg-base-200/40 shadow-sm"
                    aria-label="Location view selector"
                  >
                    <div class="inline-grid grid-cols-3 divide-x divide-base-300">
                      <For each={dashboardLocationViews}>
                        {(view) => {
                          const active = currentView() === view.view;
                          return (
                            <A
                              href={getLocationViewHref(params.locationId, view.view)}
                              preload
                              class={locationViewButtonClass(active)}
                              classList={{
                                "cursor-default": active
                              }}
                              aria-current={active ? "page" : undefined}
                            >
                              {view.label}
                            </A>
                          );
                        }}
                      </For>
                    </div>
                  </nav>
                </header>

                <div class="pt-6">
                  {props.children}
                </div>
              </LocationDetailProvider>
            </Show>
          </Show>
        </div>
      </div>
    </main>
  );
};
