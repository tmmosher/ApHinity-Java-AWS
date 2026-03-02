import {createContext, createResource, ParentProps, Resource, Setter, useContext} from "solid-js";
import {useApiHost} from "./ApiHostContext";
import {apiFetch} from "../util/apiFetch";
import {parseLocationList} from "../util/coreApi";
import {useProfile} from "./ProfileContext";
import {LocationSummary} from "../types/Types";

const host = useApiHost();

interface LocationContext {
    locations: Resource<LocationSummary[]>,
    mutate:  Setter<LocationSummary[] | undefined>,
    refetch: (info?: unknown) => (LocationSummary[] | Promise<LocationSummary[] | undefined> | null | undefined),
}

/**
 * Moved here from individual files because this is frequently used.
 */
const fetchLocations = async (): Promise<LocationSummary[]> => {
    const response = await apiFetch(host + "/api/core/locations", {
        method: "GET"
    });
    if (!response.ok) {
        throw new Error("Unable to load locations.");
    }
    return parseLocationList(await response.json());
};

const [locations, {mutate, refetch}] = createResource(
    () => fetchLocations()
);

const locationInformation = {locations, mutate, refetch} as LocationContext;
const LocationContext = createContext<LocationContext>(locationInformation);

export const LocationProvider = (props: ParentProps) => (
    <LocationContext.Provider value={locationInformation}>
        {props.children}
    </LocationContext.Provider>
)

export const useLocations = () => {
    const ctx = useContext(LocationContext);
    if (!ctx) {
        throw new Error(
            "Unable to load locations. Please login again."
        )
    }
    return ctx;
}