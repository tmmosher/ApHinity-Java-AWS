import type {
  AccountRole,
  CreateLocationServiceEventRequest,
  LocationServiceEvent
} from "../../../../types/Types";
import {
  createDefaultServiceEventDraft,
  createServiceEventDraftFromEvent
} from "../../../../util/location/serviceEventForm";
import ServiceEventModal from "./ServiceEventModal";

type ServiceEventEditModalProps = {
  isOpen: boolean;
  role: AccountRole | undefined;
  event: LocationServiceEvent | undefined;
  onSave: (request: CreateLocationServiceEventRequest) => Promise<void>;
  onClose: () => void;
};

export const ServiceEventEditModal = (props: ServiceEventEditModalProps) => (
  <ServiceEventModal
    isOpen={props.isOpen}
    heading="Edit Service Event"
    description="Update the service event details for this location."
    role={props.role}
    saveLabel="Save Changes"
    getInitialDraft={() => (
      props.event
        ? createServiceEventDraftFromEvent(props.event)
        : createDefaultServiceEventDraft(props.role)
    )}
    onSave={props.onSave}
    onClose={props.onClose}
  />
);

export default ServiceEventEditModal;
