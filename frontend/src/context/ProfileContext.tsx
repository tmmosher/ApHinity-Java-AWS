import {useApiHost} from "./ApiHostContext";
import {Accessor, createContext, createEffect, createResource, ParentProps, useContext} from "solid-js";
import {AccountRole, Profile} from "../types/Types";
import {useNavigate} from "@solidjs/router";
import {toast} from "solid-toast";
import {apiFetch} from "../util/apiFetch";

type ProfileContextValue = {
    profile: Accessor<Profile | undefined>;
    isLoading: Accessor<boolean>;
    refreshProfile: () => void;
    setProfile: (next: Profile) => void;
};

/**
 * Validates and normalizes the profile payload returned by the API.
 *
 * @param toValidate Unknown response body from `/api/core/profile`.
 * @returns Parsed `Profile` value.
 * @throws {Error} When required fields are missing or invalid.
 */
const validateProfileStructure = (toValidate: unknown): Profile => {
    if (!toValidate || typeof toValidate !== "object") {
        throw new Error("Invalid response structure");
    }
    const profileLike = toValidate as Record<string, unknown>;
    const rawRole = profileLike.role;
    if (rawRole !== "admin" && rawRole !== "partner" && rawRole !== "client") {
        throw new Error("Invalid profile role");
    }
    if (typeof profileLike.email !== "string"
        || typeof profileLike.name !== "string"
        || typeof profileLike.verified !== "boolean"
    ) {
        throw new Error("Invalid profile structure");
    }
    return {
        name: profileLike.name,
        email: profileLike.email,
        verified: profileLike.verified,
        role: rawRole as AccountRole
    };
};

const ProfileContext = createContext<ProfileContextValue>();

export const ProfileProvider = (props: ParentProps) => {
    const host = useApiHost();
    const navigate = useNavigate();

    /**
     * Loads the current authenticated user profile.
     *
     * Endpoint: `GET /api/core/profile`
     *
     * @returns The validated profile payload.
     * @throws {Error} When the request fails or response payload is invalid.
     */
    const profileRequest = async (): Promise<Profile> => {
        const response = await apiFetch(host + "/api/core/profile", {
            method: "GET"
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
