import {A} from "@solidjs/router";
import {For, Show, createEffect, createMemo, createResource, createSignal, on, type ParentProps} from "solid-js";
import {useApiHost} from "../../../../context/ApiHostContext";
import {LocationDetailProvider} from "../../../../context/LocationDetailContext";
import {useProfile} from "../../../../context/ProfileContext";
import type {LocationGraph, LocationGraphTimeRange, LocationSummary} from "../../../../types/Types";
import {canEditLocationGraphs} from "../../../../util/common/profileAccess";
import {createGanttTaskImportController} from "../../../../util/location/createGanttTaskImportController";
import {createLocationDashboardEditController} from "../../../../util/location/createLocationDashboardEditController";
import {createServiceCalendarStagingController} from "../../../../util/location/createServiceCalendarStagingController";
import {fetchLocationById, fetchLocationGraphsById} from "../../../../util/graph/locationDetailApi";
import {monthRangeForDashboardTimeRange} from "../../../../util/location/dashboardTimeRange";
import {
  createDashboardLocationResetGuard,
  createLocationViewActive,
  dashboardLocationViews,
  getFreshLocationScopedValue,
  getNextLocationGraphRequestId,
  getLocationViewHref,
  type DashboardLocationView,
  type LocationScopedResource
} from "../../../../util/location/locationView";
import {recordRecentLocationIdIfLoaded} from "../../../../util/common/recentLocation";

type LocationDetailShellProps = ParentProps & {
  locationId: string;
  currentView: DashboardLocationView;
};

export const LocationDetailShell = (props: LocationDetailShellProps) => {
  const host = useApiHost();
  const profileContext = useProfile();
  const canEditGraphs = createMemo(() => canEditLocationGraphs(profileContext.profile()?.role));
  const shouldResetDashboardState = createDashboardLocationResetGuard(props.locationId);
  const shouldResetServiceCalendarState = createDashboardLocationResetGuard(props.locationId);
  const shouldResetGanttTaskState = createDashboardLocationResetGuard(props.locationId);
  const serviceCalendarStaging = createServiceCalendarStagingController();
  const [ganttTaskRefetcher, setGanttTaskRefetcher] = createSignal<(() => Promise<void>) | undefined>();
  const [ganttLocationSessionToken, setGanttLocationSessionToken] = createSignal(0);
  const ganttTaskImport = createGanttTaskImportController({
    host,
    locationId: () => props.locationId,
    locationSessionToken: ganttLocationSessionToken,
    clearUploadInput: () => undefined,
    refetchTasks: async () => {
      await ganttTaskRefetcher()?.();
    }
  });

  const [locationResource, {refetch: refetchLocation}] = createResource(
    () => props.locationId,
    async (locationId): Promise<LocationScopedResource<LocationSummary>> => ({
      locationId,
      value: await fetchLocationById(host, locationId)
    })
  );

  const [requestedGraphLocationId, setRequestedGraphLocationId] = createSignal<string | undefined>();
  const [graphTimeRange, setGraphTimeRange] = createSignal<LocationGraphTimeRange>("threeMonths");
  const [graphCacheVersion, setGraphCacheVersion] = createSignal(0);
  const graphCache = new Map<string, LocationScopedResource<LocationGraph[]>>();

  const graphCacheKey = (locationId: string, monthRange: number): string => `${locationId}:${monthRange}`;
  const clearGraphCache = () => {
    graphCache.clear();
    setGraphCacheVersion((version) => version + 1);
  };

  const [graphResource, {refetch: refetchGraphResource}] = createResource(
    () => {
      const locationId = requestedGraphLocationId();
      if (!locationId) {
        return undefined;
      }
      return {
        locationId,
        monthRange: monthRangeForDashboardTimeRange(graphTimeRange()),
        cacheVersion: graphCacheVersion()
      };
    },
    async ({locationId, monthRange}): Promise<LocationScopedResource<LocationGraph[]>> => {
      const cacheKey = graphCacheKey(locationId, monthRange);
      const cachedGraphs = graphCache.get(cacheKey);
      if (cachedGraphs) {
        return cachedGraphs;
      }
      const nextGraphs = {
        locationId,
        value: await fetchLocationGraphsById(host, locationId, monthRange)
      };
      graphCache.set(cacheKey, nextGraphs);
      return nextGraphs;
    }
  );

  const location = createMemo(() => getFreshLocationScopedValue(props.locationId, locationResource()));
  const graphs = createMemo(() => getFreshLocationScopedValue(props.locationId, graphResource()));
  const graphsError = createMemo(() =>
    requestedGraphLocationId() === props.locationId && !graphResource.loading
      ? graphResource.error
      : undefined
  );

  const refetchLocationDetail = async (): Promise<void> => {
    await refetchLocation();
  };

  const refetchLocationGraphs = async (): Promise<void> => {
    if (requestedGraphLocationId() !== props.locationId) {
      return;
    }
    clearGraphCache();
    await refetchGraphResource();
  };

  const retryLocation = () => {
    void refetchLocationDetail();
  };

  const currentView = createMemo(() => props.currentView);
  const dashboardEdit = createLocationDashboardEditController({
    host,
    locationId: () => props.locationId,
    location,
    graphs,
    refetchLocation: refetchLocationDetail,
    refetchGraphs: refetchLocationGraphs,
    canEditGraphs,
    graphTimeRange,
    shouldResetDashboardState
  });

  createEffect(on(location, (currentLocation) => {
    recordRecentLocationIdIfLoaded(currentLocation);
  }));

  createEffect(on(
    () => [props.locationId, props.currentView] as const,
    ([locationId, view]) => {
      setRequestedGraphLocationId((currentRequestedLocationId) =>
        getNextLocationGraphRequestId(currentRequestedLocationId, locationId, view)
      );
      if (view !== "dashboard") {
        setGraphTimeRange("threeMonths");
      }
      if (shouldResetServiceCalendarState(locationId)) {
        serviceCalendarStaging.reset();
      }
      if (shouldResetGanttTaskState(locationId)) {
        ganttTaskImport.reset();
        setGanttLocationSessionToken((token) => token + 1);
        setGanttTaskRefetcher(undefined);
      }
    }
  ));

  return (
    <div class="mx-auto w-full max-w-[96rem]">
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
            <LocationDetailProvider
              location={location}
              graphs={graphs}
              graphsLoading={() => requestedGraphLocationId() === props.locationId && graphResource.loading}
              graphsError={graphsError}
              graphTimeRange={graphTimeRange}
              setGraphTimeRange={setGraphTimeRange}
              dashboardEdit={dashboardEdit}
              serviceCalendarStaging={serviceCalendarStaging}
              ganttTaskImport={ganttTaskImport}
              setGanttTaskRefetcher={setGanttTaskRefetcher}
              refetchLocation={refetchLocationDetail}
              refetchGraphs={refetchLocationGraphs}
            >
              <header class="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between sm:gap-4">
                <div class="min-w-0 space-y-1">
                  <p class="text-xs font-semibold uppercase tracking-3wide text-base-content/60">
                    Location
                  </p>
                  <h1 class="text-3xl font-semibold tracking-tight">
                    {location()?.name}
                  </h1>
                </div>

                <div class="flex flex-col gap-3 sm:items-end">
                  <nav
                    class="w-fit max-w-full overflow-hidden rounded-2xl border border-base-300 bg-base-200/40 shadow-sm"
                    aria-label="Location view selector"
                  >
                    <div class="inline-grid grid-cols-3 divide-x divide-base-300">
                      <For each={dashboardLocationViews}>
                        {(view) => {
                          const active = createLocationViewActive(currentView, view.view);
                          return (
                            <A
                              href={getLocationViewHref(props.locationId, view.view)}
                              preload
                              class={
                                "flex min-h-11 items-center justify-center px-3 py-2 text-center text-xs font-semibold tracking-tight transform-gpu transition duration-150 ease-out motion-reduce:transform-none motion-reduce:transition-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/40 sm:text-sm " +
                                (active()
                                  ? "bg-primary text-primary-content shadow-sm"
                                  : "bg-transparent text-base-content/70 hover:-translate-y-px hover:bg-base-100/70 hover:shadow-sm active:translate-y-px active:scale-[0.98]")
                              }
                              classList={{
                                "cursor-default": active()
                              }}
                              aria-current={active() ? "page" : undefined}
                              aria-label={view.name}
                              title={view.name}
                              data-view={view.view}
                            >
                              <span aria-hidden="true">{view.label}</span>
                              <span class="sr-only">{view.name}</span>
                            </A>
                          );
                        }}
                      </For>
                    </div>
                  </nav>
                </div>
              </header>
              <div class="pt-6">
                {props.children}
              </div>
            </LocationDetailProvider>
          </Show>
        </Show>
      </div>
    </div>
  );
};

export default LocationDetailShell;
