import {For, Show, Suspense, type Resource} from "solid-js";
import PlotlyChart, {type PlotlyConfig, type PlotlyData, type PlotlyLayout} from "../common/Chart";
import GraphLoadingPlaceholder from "../graph/GraphLoadingPlaceholder";
import type {LocationGraph, LocationSectionLayout} from "../../types/Types";
import {resolveGraphHeight} from "../../util/graph/graphTheme";

type LocationDashboardSectionProps = {
  section: LocationSectionLayout;
  graphs: LocationGraph[];
  missingGraphIds: number[];
  canEditGraphs: boolean;
  isGraphMutationBusy: boolean;
  plotlyModule: Resource<unknown>;
  onOpenGraphEditor: (graphId: number) => void;
};

export const LocationDashboardSection = (props: LocationDashboardSectionProps) => (
  <section class="rounded-xl border border-base-300 bg-base-100 p-5 shadow-sm" data-section-id={props.section.section_id}>
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
            <article class="rounded-lg border border-base-200 bg-base-200/40 p-3">
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
                    <div class="w-full" style={{height: resolveGraphHeight(graph.style)}}>
                      <PlotlyChart
                        name={graph.name}
                        data={graph.data as PlotlyData[]}
                        layout={(graph.layout ?? undefined) as PlotlyLayout | undefined}
                        config={(graph.config ?? undefined) as PlotlyConfig | undefined}
                        style={graph.style ?? undefined}
                        class="h-full w-full"
                      />
                    </div>
                  </Show>
                </Suspense>
              </Show>
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
