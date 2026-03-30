import {useParams} from "@solidjs/router";
import {createSignal} from "solid-js";
import {toast} from "solid-toast";
import {useApiHost} from "../../../../context/ApiHostContext";
import {useProfile} from "../../../../context/ProfileContext";
import {createLocationEventById} from "../../../../util/location/locationEventApi";
import type {CreateLocationServiceEventRequest} from "../../../../types/Types";
import ServiceEventCreateModal from "./ServiceEventCreateModal";
import ServiceScheduleCalendar from "./ServiceScheduleCalendar";

export const DashboardLocationServiceCalendarPanel = () => {
  const host = useApiHost();
  const profileContext = useProfile();
  const params = useParams<{ locationId: string }>();
  const [isCreateModalOpen, setIsCreateModalOpen] = createSignal(false);

  const saveServiceEvent = async (request: CreateLocationServiceEventRequest): Promise<void> => {
    await createLocationEventById(host, params.locationId, request);
    toast.success("Service event created.");
  };

  return (
    <div class="flex min-h-[calc(100vh-16rem)] flex-col gap-4">
      <section class="rounded-2xl border border-base-300 bg-base-100/70 p-6 shadow-sm">
        <div class="flex flex-wrap items-start justify-between gap-4">
          <div class="space-y-2">
            <h2 class="text-xl font-semibold tracking-tight">Service Calendar</h2>
            <p class="text-sm text-base-content/70">
              The calendar shell is in place. Event bars, detail popovers, and live server data are
              still under development.
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

      <section class="flex min-h-[44rem] flex-1 flex-col rounded-2xl border border-base-300 bg-base-100/70 p-4 shadow-sm md:p-6">
        <ServiceScheduleCalendar />
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
