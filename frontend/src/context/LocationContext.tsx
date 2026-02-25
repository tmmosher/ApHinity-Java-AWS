import {createContext, createResource, ParentProps, Resource, Setter, useContext} from "solid-js";
import {useApiHost} from "./ApiHostContext";
import {apiFetch} from "../util/apiFetch";
import {parseLocationList} from "../util/coreApi";
import {useProfile} from "./ProfileContext";
import {LocationSummary} from "../types/Types";

const host = useApiHost();
const profileContext = useProfile();
const canAccessLocations = () => Boolean(profileContext.profile()?.verified);

interface LocationContext {
    locations: Resource<LocationSummary[]>,
    mutate:  Setter<LocationSummary[] | undefined>
    refetch: (info?: unknown) => (LocationSummary[] | Promise<LocationSummary[] | undefined> | null | undefined)
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
    () => (canAccessLocations() ? "verified" : null),
    () => fetchLocations()
);

const locationInformation = {locations, mutate, refetch} as LocationContext;
const LocationContext = createContext<LocationContext>();

export const LocationProvider = (props: ParentProps) => (
    <LocationContext.Provider value={locationInformation}>
        {props.children}
    </LocationContext.Provider>
)