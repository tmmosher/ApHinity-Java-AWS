import {createContext, useContext, type Accessor, type ParentProps} from "solid-js";
import type {LocationGraph, LocationSummary} from "../../../../types/Types";

export type LocationDetailContextValue = {
  location: Accessor<LocationSummary | undefined>;
  graphs: Accessor<LocationGraph[] | undefined>;
  graphsLoading: Accessor<boolean>;
  graphsError: Accessor<unknown>;
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
