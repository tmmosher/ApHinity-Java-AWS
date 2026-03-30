import {useParams} from "@solidjs/router";
import {createMemo, createResource, createSignal} from "solid-js";
import {toast} from "solid-toast";
import {useApiHost} from "../../../../context/ApiHostContext";
import {useProfile} from "../../../../context/ProfileContext";
import {createLocationEventById, fetchLocationEventsById} from "../../../../util/location/locationEventApi";
import {formatLocationEventMonth, normalizeMonthStart} from "../../../../util/location/dateUtility";
import type {CreateLocationServiceEventRequest, LocationServiceEvent} from "../../../../types/Types";
import ServiceEventCreateModal from "./ServiceEventCreateModal";
import ServiceScheduleCalendar from "./ServiceScheduleCalendar";

type ServiceEventCalendarResource = {
  locationId: string;
  month: string;
  value: LocationServiceEvent[];
};

export const DashboardLocationServiceCalendarPanel = () => {
  const host = useApiHost();
  const profileContext = useProfile();
  const params = useParams<{ locationId: string }>();
  const [isCreateModalOpen, setIsCreateModalOpen] = createSignal(false);
  const [calendarMonth, setCalendarMonth] = createSignal(normalizeMonthStart(new Date()));

  const viewedMonth = createMemo(() => formatLocationEventMonth(calendarMonth()));
  const serviceEventRequest = createMemo(() => ({
    locationId: params.locationId,
    month: viewedMonth()
  }));

  const [serviceEventResource, {refetch: refetchServiceEventResource}] = createResource(
    serviceEventRequest,
    async (request): Promise<ServiceEventCalendarResource> => ({
      ...request,
      value: await fetchLocationEventsById(host, request.locationId, request.month)
    })
  );

  const serviceEvents = createMemo(() => {
    const resource = serviceEventResource();
    const request = serviceEventRequest();
    if (!resource || resource.locationId !== request.locationId || resource.month !== request.month) {
      return undefined;
    }
    return resource.value;
  });

  const serviceEventErrorMessage = createMemo(() => {
    if (serviceEventResource.loading || !serviceEventResource.error) {
      return undefined;
    }
    return serviceEventResource.error instanceof Error
      ? serviceEventResource.error.message
      : "Unable to load service events.";
  });

  const refetchServiceEvents = async (): Promise<void> => {
    await refetchServiceEventResource();
  };

  const saveServiceEvent = async (request: CreateLocationServiceEventRequest): Promise<void> => {
    await createLocationEventById(host, params.locationId, request);
    await refetchServiceEvents();
    toast.success("Service event created.");
  };

  return (
    <div class="flex min-h-[calc(100vh-16rem)] flex-col gap-4">
      <section class="rounded-2xl border border-base-300 bg-base-100/70 p-6 shadow-sm">
        <div class="flex flex-wrap items-start justify-between gap-4">
          <div class="space-y-2">
            <h2 class="text-xl font-semibold tracking-tight">Service Calendar</h2>
            <p class="text-sm text-base-content/70">
              View previous, current, and upcoming service events. The calendar loads the previous,
              current, and next month relative to the month you are viewing.
            </p>
            <div class="flex flex-wrap items-center gap-2 pt-1 text-xs font-medium">
              <span class="rounded-full border border-[#f59e0b]/35 bg-[#f59e0b]/18 px-2.5 py-1 text-[#9a3412]">
                Client
              </span>
              <span class="rounded-full border border-[#86efac] bg-[#dcfce7] px-2.5 py-1 text-[#166534]">
                Partner
              </span>
            </div>
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
        <ServiceScheduleCalendar
          month={calendarMonth()}
          onMonthChange={(month) => setCalendarMonth(normalizeMonthStart(month))}
          events={serviceEvents()}
          isLoading={serviceEventResource.loading && serviceEvents() === undefined}
          error={serviceEventErrorMessage()}
          onRetry={() => void refetchServiceEvents()}
        />
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
