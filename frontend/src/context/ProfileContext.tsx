import {useApiHost} from "./ApiHostContext";
import {Accessor, createContext, createEffect, createResource, ParentProps, useContext} from "solid-js";
import {Profile} from "../types/Types";
import {useNavigate} from "@solidjs/router";
import {toast} from "solid-toast";

type ProfileContextValue = {
    profile: Accessor<Profile | undefined>;
    isLoading: Accessor<boolean>;
    refreshProfile: () => void;
    setProfile: (next: Profile) => void;
};

const validateProfileStructure = (toValidate: unknown): Profile => {
    if (!toValidate || typeof toValidate !== "object") {
        throw new Error("Invalid response structure");
    }
    const profileLike = toValidate as Record<string, unknown>;
    if (typeof profileLike.email !== "string"
        || typeof profileLike.name !== "string"
        || typeof profileLike.verified !== "boolean") {
        throw new Error("Invalid profile structure");
    }
    return {
        name: profileLike.name,
        email: profileLike.email,
        verified: profileLike.verified
    };
};

const ProfileContext = createContext<ProfileContextValue>();

export const ProfileProvider = (props: ParentProps) => {
    const host = useApiHost();
    const navigate = useNavigate();

    const profileRequest = async (): Promise<Profile> => {
        const response = await fetch(host + "/api/core/profile", {
            method: "GET",
            credentials: "include"
        });
        if (!response.ok) {
            throw new Error("Unable to fetch profile");
        }
        const body = await response.json();
        return validateProfileStructure(body);
    };

    const [profile, { mutate, refetch }] = createResource(profileRequest);

    createEffect(() => {
        if (!profile.error) {
            return;
        }
        navigate("/login");
        toast.error("Unable to load profile. Please login again.");
    });

    const value: ProfileContextValue = {
        profile: () => profile(),
        isLoading: () => profile.loading,
        refreshProfile: () => {
            void refetch();
        },
        setProfile: (next: Profile) => mutate(next)
    };

    return (
        <ProfileContext.Provider value={value}>
            {props.children}
        </ProfileContext.Provider>
    );
};

export const useProfile = () => {
    const context = useContext(ProfileContext);
    if (!context) {
        throw new Error("Profile context is not available");
    }
    return context;
};
