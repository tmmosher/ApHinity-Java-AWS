import {createEffect, createSignal, Show} from "solid-js";
import {toast} from "solid-toast";
import {useApiHost} from "../context/ApiHostContext";
import {useProfile} from "../context/ProfileContext";
import {
    getStoredThemePreference,
    setStoredThemePreference,
    ThemePreference
} from "../util/themePreference";
import {apiFetch} from "../util/apiFetch";

const Profile = () => {
    const host = useApiHost();
    const profileContext = useProfile();

    const [name, setName] = createSignal("");
    const [email, setEmail] = createSignal("");
    const [currentPassword, setCurrentPassword] = createSignal("");
    const [newPassword, setNewPassword] = createSignal("");
    const [isSavingProfile, setIsSavingProfile] = createSignal(false);
    const [isSavingPassword, setIsSavingPassword] = createSignal(false);
    const [themePreference, setThemePreference] = createSignal<ThemePreference>(getStoredThemePreference());

    createEffect(() => {
        const profile = profileContext.profile();
        if (!profile) {
            return;
        }
        setName(profile.name);
        setEmail(profile.email);
    });

    const updateThemePreference = (next: ThemePreference) => {
        setThemePreference(next);
        setStoredThemePreference(next);
        toast.success(`Theme changed to ${next} mode.`);
    };

    const updateProfile = async (event: SubmitEvent) => {
        event.preventDefault();
        if (isSavingProfile()) {
            return;
        }
        setIsSavingProfile(true);
        try {
            const response = await apiFetch(host + "/api/core/profile", {
                method: "PUT",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    name: name().trim(),
                    email: email().trim()
                })
            });

            if (!response.ok) {
                const errorBody = await response.json().catch(() => null);
                toast.error(errorBody?.message ?? "Unable to update profile.");
                return;
            }
            profileContext.refreshProfile();
            toast.success("Profile updated.");
        } catch {
            toast.error("Unable to update profile.");
        } finally {
            setIsSavingProfile(false);
        }
    };

    const updatePassword = async (event: SubmitEvent) => {
        event.preventDefault();
        if (isSavingPassword()) {
            return;
        }
        setIsSavingPassword(true);
        try {
            const response = await apiFetch(host + "/api/core/profile/password", {
                method: "PUT",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    currentPassword: currentPassword().trim(),
                    newPassword: newPassword().trim()
                })
            });
            if (!response.ok) {
                const errorBody = await response.json().catch(() => null);
                toast.error(errorBody?.message ?? "Unable to update password.");
                return;
            }
            setCurrentPassword("");
            setNewPassword("");
            toast.success("Password updated.");
        } catch {
            toast.error("Unable to update password.");
        } finally {
            setIsSavingPassword(false);
        }
    };

    return (
        <div class="space-y-6">
            <header class="space-y-1">
                <h1 class="text-3xl font-semibold tracking-tight">Profile</h1>
                <p class="text-base-content/70">Manage your account details and preferences.</p>
            </header>

            <Show when={!profileContext.isLoading()} fallback={<p class="text-base-content/70">Loading profile...</p>}>
                <div class="grid grid-cols-1 gap-5">
                    <section class="rounded-xl border border-base-300 bg-base-100 p-5 shadow-sm">
                        <h2 class="text-lg font-semibold">Theme preference</h2>
                        <p class="mt-1 text-sm text-base-content/70">Choose the appearance mode for your dashboard.</p>
                        <div class="mt-4 join">
                            <button
                                type="button"
                                class="btn join-item"
                                classList={{"btn-primary": themePreference() === "light", "btn-outline": themePreference() !== "light"}}
                                onClick={() => updateThemePreference("light")}
                            >
                                Light
                            </button>
                            <button
                                type="button"
                                class="btn join-item"
                                classList={{"btn-primary": themePreference() === "dark", "btn-outline": themePreference() !== "dark"}}
                                onClick={() => updateThemePreference("dark")}
                            >
                                Dark
                            </button>
                        </div>
                    </section>

                    <section class="rounded-xl border border-base-300 bg-base-100 p-5 shadow-sm">
                        <h2 class="text-lg font-semibold">Account details</h2>
                        <p class="mt-1 text-sm text-base-content/70">Update your display name and account email.</p>
                        <p class="mt-1 text-xs text-base-content/60">
                            Role: {profileContext.profile()?.role ?? "client"}
                        </p>
                        <form class="mt-4 grid gap-4" onSubmit={updateProfile}>
                            <label class="form-control">
                                <span class="label-text">Name</span>
                                <input
                                    type="text"
                                    class="input input-bordered mt-1"
                                    value={name()}
                                    onInput={(event) => setName(event.currentTarget.value)}
                                />
                            </label>
                            <label class="form-control">
                                <span class="label-text">Email</span>
                                <input
                                    type="email"
                                    class="input input-bordered mt-1"
                                    value={email()}
                                    onInput={(event) => setEmail(event.currentTarget.value)}
                                />
                            </label>
                            <button type="submit" class="btn btn-primary w-fit" disabled={isSavingProfile()}>
                                {isSavingProfile() ? "Saving..." : "Save profile"}
                            </button>
                        </form>
                    </section>

                    <section class="rounded-xl border border-base-300 bg-base-100 p-5 shadow-sm">
                        <h2 class="text-lg font-semibold">Password</h2>
                        <p class="mt-1 text-sm text-base-content/70">Set a new password for this account.</p>
                        <form class="mt-4 grid gap-4" onSubmit={updatePassword}>
                            <label class="form-control">
                                <span class="label-text">Current password</span>
                                <input
                                    type="password"
                                    class="input input-bordered mt-1"
                                    value={currentPassword()}
                                    onInput={(event) => setCurrentPassword(event.currentTarget.value)}
                                />
                            </label>
                            <label class="form-control">
                                <span class="label-text">New password</span>
                                <input
                                    type="password"
                                    class="input input-bordered mt-1"
                                    value={newPassword()}
                                    onInput={(event) => setNewPassword(event.currentTarget.value)}
                                />
                            </label>
                            <button type="submit" class="btn btn-primary w-fit" disabled={isSavingPassword()}>
                                {isSavingPassword() ? "Saving..." : "Update password"}
                            </button>
                        </form>
                    </section>
                </div>
            </Show>
        </div>
    );
};

export default Profile;
