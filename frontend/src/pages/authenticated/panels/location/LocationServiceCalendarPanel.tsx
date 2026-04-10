import {useParams} from "@solidjs/router";
import {createMemo, createResource, createSignal} from "solid-js";
import {toast} from "solid-toast";
import {useApiHost} from "../../../../context/ApiHostContext";
import {useProfile} from "../../../../context/ProfileContext";
import {
  createLocationEventById,
  fetchLocationEventsById,
  getLocationEventTemplateDownloadUrl,
  updateLocationEventById
} from "../../../../util/location/locationEventApi";
import {formatLocationEventMonth, normalizeMonthStart} from "../../../../util/location/dateUtility";
import type {CreateLocationServiceEventRequest, LocationServiceEvent} from "../../../../types/Types";
import {
  canCompleteLocationServiceEvent,
  canEditLocationServiceEvent,
  createLocationServiceEventRequestFromEvent
} from "../../../../util/location/serviceEventForm";
import ServiceCalendarIntroPopover from "./ServiceCalendarIntroPopover";
import ServiceScheduleCalendar from "./ServiceScheduleCalendar";

type ServiceEventCalendarResource = {
  locationId: string;
  month: string;
  value: LocationServiceEvent[];
};

export const LocationServiceCalendarPanel = () => {
  const host = useApiHost();
  const profileContext = useProfile();
  const params = useParams<{ locationId: string }>();
  const [calendarMonth, setCalendarMonth] = createSignal(normalizeMonthStart(new Date()));
  const role = createMemo(() => profileContext.profile()?.role);

  const viewedMonth = createMemo(() => formatLocationEventMonth(calendarMonth()));
  const serviceCalendarTemplateHref = createMemo(() =>
    getLocationEventTemplateDownloadUrl(host, params.locationId)
  );
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
        <div class="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
          <div class="space-y-1">
            <h2 class="text-xl font-semibold tracking-tight">Service Calendar</h2>
          </div>
          <ServiceCalendarIntroPopover templateHref={serviceCalendarTemplateHref()} />
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
