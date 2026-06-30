import {createContext, useContext, type Accessor, type ParentProps} from "solid-js";
import type {LocationGraph, LocationGraphTimeRange, LocationSummary} from "../types/Types";
import type {LocationDashboardEditController} from "../util/location/createLocationDashboardEditController";
import type {ServiceCalendarStagingController} from "../util/location/createServiceCalendarStagingController";
import type {GanttTaskImportController} from "../util/location/createGanttTaskImportController";

export type LocationDetailContextValue = {
  location: Accessor<LocationSummary | undefined>;
  graphs: Accessor<LocationGraph[] | undefined>;
  graphsLoading: Accessor<boolean>;
  graphsError: Accessor<unknown>;
  graphTimeRange: Accessor<LocationGraphTimeRange>;
  setGraphTimeRange: (timeRange: LocationGraphTimeRange) => void;
  dashboardEdit: LocationDashboardEditController;
  serviceCalendarStaging: ServiceCalendarStagingController;
  ganttTaskImport: GanttTaskImportController;
  setGanttTaskRefetcher: (refetcher: (() => Promise<void>) | undefined) => void;
  refetchLocation: () => Promise<void>;
  refetchGraphs: () => Promise<void>;
};

const LocationDetailContext = createContext<LocationDetailContextValue>();

type LocationDetailProviderProps = ParentProps & LocationDetailContextValue;

export const LocationDetailProvider = (props: LocationDetailProviderProps) => (
  <LocationDetailContext.Provider value={{
    location: props.location,
    graphs: props.graphs,
    graphsLoading: props.graphsLoading,
    graphsError: props.graphsError,
    graphTimeRange: props.graphTimeRange,
    setGraphTimeRange: props.setGraphTimeRange,
    dashboardEdit: props.dashboardEdit,
    serviceCalendarStaging: props.serviceCalendarStaging,
    ganttTaskImport: props.ganttTaskImport,
    setGanttTaskRefetcher: props.setGanttTaskRefetcher,
    refetchLocation: props.refetchLocation,
    refetchGraphs: props.refetchGraphs
  }}>
    {props.children}
  </LocationDetailContext.Provider>
);

export const useLocationDetail = (): LocationDetailContextValue => {
  const context = useContext(LocationDetailContext);
  if (!context) {
    throw new Error("Location details are unavailable.");
  }
  return context;
};
