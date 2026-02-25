import {A, useParams} from "@solidjs/router";
import PlotlyChart, {loadPlotlyModule} from "../../../components/Chart";
import type {PlotlyConfig, PlotlyData, PlotlyLayout} from "../../../components/Chart";
import {For, Show, Suspense, createMemo, createResource, createEffect} from "solid-js";
import {useApiHost} from "../../../context/ApiHostContext";
import {LocationGraph, LocationSectionLayout} from "../../../types/Types";
import {fetchLocationById, fetchLocationGraphsById} from "./locationDetailApi";

export const DashboardLocationDetailPanel = () => {
  const host = useApiHost();
  const params = useParams<{ locationId: string }>();

  const [location, {refetch: refetchLocation}] = createResource(
    () => params.locationId,
    (locationId) => fetchLocationById(host, locationId)
  );
  const [graphs, {refetch: refetchGraphs}] = createResource(
    () => params.locationId,
    (locationId) => fetchLocationGraphsById(host, locationId)
  );

  const retryAll = () => {
    void refetchLocation();
    void refetchGraphs();
  };

  const graphById = createMemo(() => {
    const byId = new Map<number, LocationGraph>();
    for (const graph of graphs() ?? []) {
      byId.set(graph.id, graph);
    }
    return byId;
  });

  const orderedSections = createMemo(() => {
    const currentLocation = location();
    if (!currentLocation) {
      return [] as LocationSectionLayout[];
    }

    return [...currentLocation.sectionLayout.sections].sort(
      (left, right) => left.section_id - right.section_id
    );
  });

  const sectionGraphs = (section: LocationSectionLayout): LocationGraph[] =>
    section.graph_ids
      .map((graphId) => graphById().get(graphId))
      .filter((graph): graph is LocationGraph => graph !== undefined);

    createEffect(() => {
        console.log(sectionGraphs)
    })

  const missingGraphIds = (section: LocationSectionLayout): number[] =>
    section.graph_ids.filter((graphId) => !graphById().has(graphId));

  const [plotlyModule] = createResource(
    () => {
      const sections = orderedSections();
      if (sections.length === 0) {
        return false;
      }
      const byId = graphById();
      return sections.some((section) => section.graph_ids.some((graphId) => byId.has(graphId)));
    },
    async (shouldLoad) => (shouldLoad ? loadPlotlyModule() : null)
  );

  const updatedAtLabel = () => {
    const current = location();
    if (!current) {
      return "-";
    }
    return new Date(current.updatedAt).toLocaleString();
  };

  const graphLoadingFallback = (graphName: string) => (
    <div class="graph-loading-placeholder h-72 w-full" role="status" aria-live="polite" aria-label={`Loading graph ${graphName}`}>
      <div class="graph-loading-line graph-loading-line-wide" />
      <div class="graph-loading-line graph-loading-line-medium" />
      <div class="graph-loading-bars">
        <span class="graph-loading-bar graph-loading-bar-1" />
        <span class="graph-loading-bar graph-loading-bar-2" />
        <span class="graph-loading-bar graph-loading-bar-3" />
        <span class="graph-loading-bar graph-loading-bar-4" />
      </div>
    </div>
  );

  return (
    <div class="space-y-6">
      <header class="space-y-1">
        <h1 class="text-3xl font-semibold tracking-tight">Location</h1>
      </header>

      <Show when={!location.loading && !graphs.loading} fallback={<p class="text-base-content/70">Loading location dashboard...</p>}>
        <Show when={!location.error && !graphs.error} fallback={
          <div class="space-y-3">
            <p class="text-error">Unable to load location dashboard.</p>
            <div class="flex gap-2">
              <button type="button" class="btn btn-outline" onClick={retryAll}>
                Retry
              </button>
              <A href="/dashboard/locations" class="btn btn-ghost">
                Back to locations
              </A>
            </div>
          </div>
        }>
          <div class="space-y-4">
            <section class="rounded-xl border border-base-300 bg-base-100 p-5 shadow-sm">
              <h2 class="text-xl font-semibold">{location()?.name}</h2>
              <p class="mt-2 text-sm text-base-content/70">
                Last updated {updatedAtLabel()}
              </p>
            </section>

            <Show when={orderedSections().length > 0} fallback={
              <section class="rounded-xl border border-base-300 bg-base-100 p-5 shadow-sm">
                <p class="text-base-content/70">No dashboard sections are configured for this location.</p>
              </section>
            }>
              <div class="space-y-4">
                <For each={orderedSections()}>
                  {(section) => (
                    <section class="rounded-xl border border-base-300 bg-base-100 p-5 shadow-sm">
                      <div class="mb-4 flex items-center justify-between gap-2">
                        <span class="text-xs text-base-content/60">
                          {section.graph_ids.length} graph{section.graph_ids.length === 1 ? "" : "s"}
                        </span>
                      </div>

                      <Show when={sectionGraphs(section).length > 0} fallback={
                        <p class="text-sm text-base-content/70">
                          No available graph payloads for this section.
                        </p>
                      }>
                        <div class="grid gap-4 lg:grid-cols-2">
                          <For each={sectionGraphs(section)}>
                            {(graph) => (
                              <article class="rounded-lg border border-base-200 bg-base-200/40 p-3">
                                <h4 class="mb-2 text-sm font-medium">{graph.name}</h4>
                                <Show
                                  when={!plotlyModule.error}
                                  fallback={<p class="h-72 w-full rounded-lg border border-error/30 bg-error/10 p-4 text-sm text-error">Unable to load graph renderer.</p>}
                                >
                                  <Suspense fallback={graphLoadingFallback(graph.name)}>
                                    <Show when={plotlyModule()}>
                                      <PlotlyChart
                                        name={graph.name}
                                        data={graph.data as PlotlyData[]}
                                        layout={(graph.layout ?? undefined) as PlotlyLayout | undefined}
                                        config={(graph.config ?? undefined) as PlotlyConfig | undefined}
                                        class="h-72 w-full"
                                      />
                                    </Show>
                                  </Suspense>
                                </Show>
                              </article>
                            )}
                          </For>
                        </div>
                      </Show>

                      <Show when={missingGraphIds(section).length > 0}>
                        <p class="mt-3 text-xs text-warning">
                          Missing graph IDs: {missingGraphIds(section).join(", ")}
                        </p>
                      </Show>
                    </section>
                  )}
                </For>
              </div>
            </Show>
          </div>
        </Show>
      </Show>
    </div>
  );
};
