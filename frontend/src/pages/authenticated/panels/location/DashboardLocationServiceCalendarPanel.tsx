import {useParams} from "@solidjs/router";
import {createMemo, createResource, createSignal} from "solid-js";
import {toast} from "solid-toast";
import {useApiHost} from "../../../../context/ApiHostContext";
import {useProfile} from "../../../../context/ProfileContext";
import {
  createLocationEventById,
  fetchLocationEventsById,
  updateLocationEventById
} from "../../../../util/location/locationEventApi";
import {formatLocationEventMonth, normalizeMonthStart} from "../../../../util/location/dateUtility";
import type {CreateLocationServiceEventRequest, LocationServiceEvent} from "../../../../types/Types";
import {
  canCompleteLocationServiceEvent,
  canEditLocationServiceEvent,
  createLocationServiceEventRequestFromEvent
} from "../../../../util/location/serviceEventForm";
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
  const [calendarMonth, setCalendarMonth] = createSignal(normalizeMonthStart(new Date()));
  const role = createMemo(() => profileContext.profile()?.role);

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

  const saveEditedServiceEvent = async (
    event: LocationServiceEvent,
    request: CreateLocationServiceEventRequest
  ): Promise<void> => {
    await updateLocationEventById(host, params.locationId, event.id, request);
    await refetchServiceEvents();
    toast.success("Service event updated.");
  };

  const completeServiceEvent = async (event: LocationServiceEvent): Promise<void> => {
    await updateLocationEventById(host, params.locationId, event.id, {
      ...createLocationServiceEventRequestFromEvent(event),
      status: "completed"
    });
    await refetchServiceEvents();
    toast.success("Service event marked complete.");
  };

  return (
    <div class="flex min-h-[calc(100vh-16rem)] flex-col gap-4">
      <section class="rounded-2xl border border-base-300 bg-base-100/70 p-6 shadow-sm">
        <div class="space-y-2">
          <div class="space-y-2">
            <h2 class="text-xl font-semibold tracking-tight">Service Calendar</h2>
            <p class="text-sm text-base-content/70">
              View previous, current, and upcoming service events. The calendar loads the previous,
              current, and next month relative to the month you are viewing. Click an empty day cell
              to create a service event.
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
          eventEditorRole={role()}
          onCreateEventSave={saveServiceEvent}
          canEditEvent={(event) => canEditLocationServiceEvent(role(), event.responsibility)}
          onEditEventSave={saveEditedServiceEvent}
          canCompleteEvent={(event) => canCompleteLocationServiceEvent(role(), event.responsibility, event.status)}
          onCompleteEvent={completeServiceEvent}
        />
      </section>
    </div>
  );
};
