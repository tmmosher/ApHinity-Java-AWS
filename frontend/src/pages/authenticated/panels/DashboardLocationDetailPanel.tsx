import {A, useParams} from "@solidjs/router";
import {Show, createResource} from "solid-js";
import {useApiHost} from "../../../context/ApiHostContext";
import {LocationSummary, parseLocationSummary} from "../../../types/coreApi";
import {apiFetch} from "../../../util/apiFetch";

export const DashboardLocationDetailPanel = () => {
  const host = useApiHost();
  const params = useParams<{ locationId: string }>();

  const fetchLocation = async (locationId: string): Promise<LocationSummary> => {
    const parsedId = Number(locationId);
    if (!Number.isFinite(parsedId) || parsedId <= 0) {
      throw new Error("Invalid location id");
    }

    const response = await apiFetch(host + "/api/core/locations/" + parsedId, {
      method: "GET"
    });
    if (!response.ok) {
      throw new Error("Unable to load location");
    }
    return parseLocationSummary(await response.json());
  };

  const [location, {refetch}] = createResource(() => params.locationId, fetchLocation);

  const updatedAtLabel = () => {
    const current = location();
    if (!current) {
      return "-";
    }
    return new Date(current.updatedAt).toLocaleString();
  };

  return (
    <div class="space-y-6">
      <header class="space-y-1">
        <h1 class="text-3xl font-semibold tracking-tight">Location</h1>
        <p class="text-base-content/70">
          This page is wired for backend fetches and ready for graph/content panels.
        </p>
      </header>

      <Show when={!location.loading} fallback={<p class="text-base-content/70">Loading location...</p>}>
        <Show when={!location.error} fallback={
          <div class="space-y-3">
            <p class="text-error">Unable to load location details.</p>
            <div class="flex gap-2">
              <button type="button" class="btn btn-outline" onClick={() => void refetch()}>
                Retry
              </button>
              <A href="/dashboard/locations" class="btn btn-ghost">
                Back to locations
              </A>
            </div>
          </div>
        }>
          <section class="rounded-xl border border-base-300 bg-base-100 p-5 shadow-sm">
            <h2 class="text-xl font-semibold">{location()?.name}</h2>
            <p class="mt-2 text-sm text-base-content/70">
              Location-level analytics, graph rendering, and related data panels will be added here.
            </p>
            <p class="mt-2 text-xs text-base-content/60">
              Last updated {updatedAtLabel()}
            </p>
          </section>
        </Show>
      </Show>
    </div>
  );
};
