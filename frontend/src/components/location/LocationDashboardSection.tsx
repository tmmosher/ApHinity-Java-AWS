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
  flowItem?: boolean;
  onOpenGraphEditor: (graphId: number) => void;
};

const GraphDescriptionPopover = (props: {description: string}) => (
  <span class="group relative inline-flex shrink-0">
    <button
      type="button"
      class="btn btn-circle btn-ghost btn-xs min-h-0 h-5 w-5 text-xs"
      aria-label="Show graph information"
      data-graph-description-trigger=""
    >
      ?
    </button>
    <span
      role="tooltip"
      class="pointer-events-none absolute bottom-full left-1/2 z-[70] mb-2 hidden w-72 -translate-x-1/2 whitespace-pre-line break-words rounded-lg border border-base-300 bg-base-100 p-3 text-left text-xs font-normal leading-5 text-base-content/80 shadow-xl group-focus-within:block group-hover:block"
      data-graph-description-tooltip=""
    >
      {props.description}
    </span>
  </span>
);

export const LocationDashboardSection = (props: LocationDashboardSectionProps) => (
  <section
    class={"w-full rounded-xl border border-base-300 bg-base-100 p-5 shadow-sm "
      + (props.flowItem ? "break-inside-avoid" : "")}
    data-section-id={props.section.section_id}
  >
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
            <article class={"min-w-0 rounded-lg border border-base-200 bg-base-200/40 p-3 " + resolveGraphGridClass(graph.layout)}>
              <div class="mb-2 flex items-start justify-between gap-2">
                <div class="flex min-w-0 items-center gap-1.5">
                  <h4 class="min-w-0 truncate text-sm font-medium">{graph.name}</h4>
                  <Show when={graph.description}>
                    {(description) => <GraphDescriptionPopover description={description()} />}
                  </Show>
                </div>
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
                        <GraphLoadingPlaceholder
                          graphName={graph.name}
                          height={resolveGraphHeight(graph.style, graph.layout)}
                        />
                      </div>
                    }>
                      <Show when={props.plotlyModule()}>
                        <PlotlyChart
                          name={graph.name}
                          version={graph.updatedAt}
                          synchronizeDateDisplayRange
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
