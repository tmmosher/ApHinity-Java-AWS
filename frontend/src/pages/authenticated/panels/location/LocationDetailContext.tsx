import {createContext, useContext, type Accessor, type ParentProps} from "solid-js";
import type {LocationSummary} from "../../../../types/Types";

export type LocationDetailContextValue = {
  location: Accessor<LocationSummary | undefined>;
  refetchLocation: () => Promise<void>;
};

const LocationDetailContext = createContext<LocationDetailContextValue>();

type LocationDetailProviderProps = ParentProps & LocationDetailContextValue;

export const LocationDetailProvider = (props: LocationDetailProviderProps) => (
  <LocationDetailContext.Provider value={{
    location: props.location,
    refetchLocation: props.refetchLocation
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
