import {useParams} from "@solidjs/router";
import {createSignal} from "solid-js";
import {toast} from "solid-toast";
import {useApiHost} from "../../../../context/ApiHostContext";
import {useProfile} from "../../../../context/ProfileContext";
import {createLocationEventById} from "../../../../util/location/locationEventApi";
import type {CreateLocationServiceEventRequest} from "../../../../types/Types";
import ServiceEventCreateModal from "./ServiceEventCreateModal";

export const DashboardLocationServiceSchedulePanel = () => {
  const host = useApiHost();
  const profileContext = useProfile();
  const params = useParams<{ locationId: string }>();
  const [isCreateModalOpen, setIsCreateModalOpen] = createSignal(false);

  const saveServiceEvent = async (request: CreateLocationServiceEventRequest): Promise<void> => {
    await createLocationEventById(host, params.locationId, request);
    toast.success("Service event created.");
  };

  return (
    <div class="space-y-4">
      <section class="rounded-2xl border border-base-300 bg-base-100/70 p-6 shadow-sm">
        <div class="flex flex-wrap items-start justify-between gap-4">
          <div class="space-y-2">
            <h2 class="text-xl font-semibold tracking-tight">Service Schedule</h2>
            <p class="text-sm text-base-content/70">
              This page is currently under development. Calendar rendering is next, but you can
              already create service events from here.
            </p>
          </div>

          <button
            type="button"
            class="btn btn-primary btn-sm"
            onClick={() => setIsCreateModalOpen(true)}
          >
            New Service Event
          </button>
        </div>
      </section>

      <ServiceEventCreateModal
        isOpen={isCreateModalOpen()}
        role={profileContext.profile()?.role}
        onSave={saveServiceEvent}
        onClose={() => setIsCreateModalOpen(false)}
      />
    </div>
  );
};
