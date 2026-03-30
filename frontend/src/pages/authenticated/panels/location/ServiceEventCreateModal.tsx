import type {AccountRole, CreateLocationServiceEventRequest} from "../../../../types/Types";
import {createDefaultServiceEventDraft} from "../../../../util/location/serviceEventForm";
import ServiceEventModal from "./ServiceEventModal";

type ServiceEventCreateModalProps = {
  isOpen: boolean;
  role: AccountRole | undefined;
  onSave: (request: CreateLocationServiceEventRequest) => Promise<void>;
  onClose: () => void;
};

export const ServiceEventCreateModal = (props: ServiceEventCreateModalProps) => (
  <ServiceEventModal
    isOpen={props.isOpen}
    heading="New Service Event"
    description="Create a service event for this location."
    role={props.role}
    saveLabel="Save"
    getInitialDraft={() => createDefaultServiceEventDraft(props.role)}
    onSave={props.onSave}
    onClose={props.onClose}
  />
);

export default ServiceEventCreateModal;
