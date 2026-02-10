import {useApiHost} from "./ApiHostContext";
import {createContext, ParentProps, useContext} from "solid-js";
import {Profile} from "../types/Types";

const HOST = useApiHost();

const PROFILE: Profile = {
    name: "",
    email: "",
    verified: false
};

const profile = async () => {
    const response = await fetch(HOST + "/core/profile", {
       method: "GET",
    });
}

const ProfileContext = createContext<Profile>(PROFILE);

export const ProfileProvider = (props: ParentProps) => (
    <ProfileContext.Provider value={PROFILE}>
        {props.children}
    </ProfileContext.Provider>
);

export const useProfile = () => useContext(ProfileContext);