import {useLocation, useParams} from "@solidjs/router";
import {createMemo, type ParentProps} from "solid-js";
import {LocationDetailShell} from "./location/LocationDetailShell";
import {getLocationViewFromPathname} from "../../../util/location/locationView";

type DashboardLocationDetailPanelProps = ParentProps & {
  locationId?: string;
};

export const DashboardLocationDetailPanel = (props: DashboardLocationDetailPanelProps = {}) => {
  const routeLocation = useLocation();
  const params = useParams<{locationId: string}>();
  const locationId = createMemo(() => props.locationId ?? params.locationId);
  const currentView = createMemo(() =>
    getLocationViewFromPathname(routeLocation.pathname, locationId())
  );

  return (
    <main class="w-full" aria-label="Authenticated dashboard">
      <LocationDetailShell locationId={locationId()} currentView={currentView()}>
        {props.children}
      </LocationDetailShell>
    </main>
  );
};

export default DashboardLocationDetailPanel;
