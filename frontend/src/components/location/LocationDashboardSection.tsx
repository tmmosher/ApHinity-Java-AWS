import {For, Show, Suspense, type Resource} from "solid-js";
import PlotlyChart, {type PlotlyConfig, type PlotlyData, type PlotlyLayout} from "../common/Chart";
import GraphLoadingPlaceholder from "../graph/GraphLoadingPlaceholder";
import TabulatorGraph from "../graph/TabulatorGraph";
import type {LocationGraph, LocationSectionLayout} from "../../types/Types";
import {resolveGraphGridClass, resolveGraphHeight} from "../../util/graph/graphTheme";
import {isTabulatorGraph} from "../../util/graph/tabulatorGraph";

type LocationDashboardSectionProps = {
  section: LocationSectionLayout;
  graphs: LocationGraph[];
  missingGraphIds: number[];
  apiHost: string;
  locationId: string;
  monthRange: number;
  canEditGraphs: boolean;
  isGraphMutationBusy: boolean;
  plotlyModule: Resource<unknown>;
  onOpenGraphEditor: (graphId: number) => void;
};

export const LocationDashboardSection = (props: LocationDashboardSectionProps) => (
  <section class="mb-4 inline-block w-full break-inside-avoid rounded-xl border border-base-300 bg-base-100 p-5 align-top shadow-sm" data-section-id={props.section.section_id}>
    <Show
      when={props.graphs.length > 0}
      fallback={
        <p class="text-sm text-base-content/70">
          No available graph payloads for this section.
        </p>
      }
    >
      <div class="grid gap-4 lg:grid-cols-2">
        <For each={props.graphs}>
          {(graph) => (
            <article class={"rounded-lg border border-base-200 bg-base-200/40 p-3 " + resolveGraphGridClass(graph.layout)}>
              <div class="mb-2 flex items-start justify-between gap-2">
                <h4 class="text-sm font-medium">{graph.name}</h4>
                <Show when={props.canEditGraphs}>
                  <button
                    type="button"
                    class={"btn btn-xs " + (props.isGraphMutationBusy ? "btn-disabled" : "btn-outline")}
                    disabled={props.isGraphMutationBusy}
                    onClick={() => props.onOpenGraphEditor(graph.id)}
                  >
                    Edit
                  </button>
                </Show>
              </div>
              <div class="w-full" style={{height: resolveGraphHeight(graph.style, graph.layout)}}>
                <Show
                  when={!isTabulatorGraph(graph)}
                  fallback={
                    <TabulatorGraph
                      graph={graph}
                      apiHost={props.apiHost}
                      locationId={props.locationId}
                      monthRange={props.monthRange}
                      class="h-full w-full"
                    />
                  }
                >
                  <Show
                    when={!props.plotlyModule.error}
                    fallback={
                      <p class="h-72 w-full rounded-lg border border-error/30 bg-error/10 p-4 text-sm text-error">
                        Unable to load graph renderer.
                      </p>
                    }
                  >
                    <Suspense fallback={
                      <div class="w-full overflow-hidden rounded-lg">
                        <GraphLoadingPlaceholder graphName={graph.name} />
                      </div>
                    }>
                      <Show when={props.plotlyModule()}>
                        <PlotlyChart
                          name={graph.name}
                          version={graph.updatedAt}
                          data={graph.data as PlotlyData[]}
                          layout={(graph.layout ?? undefined) as PlotlyLayout | undefined}
                          config={(graph.config ?? undefined) as PlotlyConfig | undefined}
                          style={graph.style ?? undefined}
                          class="h-full w-full"
                        />
                      </Show>
                    </Suspense>
                  </Show>
                </Show>
              </div>
            </article>
          )}
        </For>
      </div>
    </Show>

    <Show when={props.missingGraphIds.length > 0}>
      <p class="mt-3 text-xs text-warning">
        Missing graph IDs: {props.missingGraphIds.join(", ")}
      </p>
    </Show>
  </section>
);

export default LocationDashboardSection;
