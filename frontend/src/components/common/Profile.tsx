import {createEffect, createSignal, Show} from "solid-js";
import {toast} from "solid-toast";
import {useApiHost} from "../../context/ApiHostContext";
import {useProfile} from "../../context/ProfileContext";
import {
    getDocumentThemePreference,
    setStoredThemePreference,
    ThemePreference
} from "../../util/common/themePreference";
import {apiFetch} from "../../util/common/apiFetch";
import {action, useNavigate, useSubmission} from "@solidjs/router";
import {canEditProfileEmail} from "../../util/common/profileAccess";
import {FieldError, parseVerifyFormData} from "../../util/common/landingSchemas";
import {ActionResult} from "../../types/Types";

const extractApiErrorMessage = async (response: Response, fallback: string): Promise<string> => {
    const errorBody = await response.json().catch(() => null) as {message?: unknown} | null;
    return typeof errorBody?.message === "string" && errorBody.message.trim()
        ? errorBody.message
        : fallback;
};

const Profile = () => {
    const host = useApiHost();
    const profileContext = useProfile();
    const navigate = useNavigate();

    const [name, setName] = createSignal("");
    const [email, setEmail] = createSignal("");
    const [currentPassword, setCurrentPassword] = createSignal("");
    const [newPassword, setNewPassword] = createSignal("");
    const [verificationCode, setVerificationCode] = createSignal("");
    const [themePreference, setThemePreference] = createSignal<ThemePreference>(getDocumentThemePreference());
    const canEditEmail = () => canEditProfileEmail(profileContext.profile()?.role);
    const isUnverified = () => profileContext.profile()?.verified === false;

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

    const updateProfileAction = action(async (formData: FormData): Promise<ActionResult> => {
        try {
            const nextEmail = (formData.get("email")?.toString() ?? "").trim();
            const response = await apiFetch(host + "/api/core/profile", {
                method: "PUT",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    name: (formData.get("name")?.toString() ?? "").trim(),
                    email: canEditEmail()
                        ? nextEmail
                        : (profileContext.profile()?.email ?? nextEmail).trim()
                })
            });

            if (!response.ok) {
                return {
                    ok: false,
                    message: await extractApiErrorMessage(response, "Unable to update profile.")
                };
            }
            return {
                ok: true
            };
        } catch {
            return {
                ok: false,
                message: "Unable to update profile."
            };
        }
    }, "updateProfile");

    const updatePasswordAction = action(async (formData: FormData): Promise<ActionResult> => {
        try {
            const response = await apiFetch(host + "/api/core/profile/password", {
                method: "PUT",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    currentPassword: (formData.get("currentPassword")?.toString() ?? "").trim(),
                    newPassword: (formData.get("newPassword")?.toString() ?? "").trim()
                })
            });
            if (!response.ok) {
                return {
                    ok: false,
                    message: await extractApiErrorMessage(response, "Unable to update password.")
                };
            }
            return {
                ok: true
            };
        } catch {
            return {
                ok: false,
                message: "Unable to update password."
            };
        }
    }, "updatePassword");

    const verifyEmailAction = action(async (formData: FormData): Promise<ActionResult> => {
        const profile = profileContext.profile();
        if (!profile) {
            return {
                ok: false,
                message: "Unable to verify email right now."
            };
        }

        try {
            const payload = parseVerifyFormData(formData);
            const response = await apiFetch(host + "/api/auth/verify", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(payload)
            });

            if (!response.ok) {
                return {
                    ok: false,
                    message: await extractApiErrorMessage(response, "Verification failed.")
                };
            }
            return {
                ok: true
            };
        } catch (error) {
            if (error instanceof FieldError) {
                return {
                    ok: false,
                    code: "validation_failed",
                    message: error.message
                };
            }
            return {
                ok: false,
                message: "Verification failed."
            };
        }
    }, "verifyProfileEmail");

    const logoutAction = action(async (): Promise<ActionResult> => {
        try {
            const response = await apiFetch(host + "/api/auth/logout", {method: "POST"});
            if (!response.ok) {
                return {
                    ok: false,
                    message: await extractApiErrorMessage(response, "Unable to logout.")
                };
            }
            return {
                ok: true
            };
        } catch {
            return {
                ok: false,
                message: "Unable to logout."
            };
        }
    }, "logout");

    const profileUpdateSubmission = useSubmission(updateProfileAction);
    const passwordUpdateSubmission = useSubmission(updatePasswordAction);
    const verifyEmailSubmission = useSubmission(verifyEmailAction);
    const logoutSubmission = useSubmission(logoutAction);

    createEffect(() => {
        const result = profileUpdateSubmission.result;
        if (!result) {
            return;
        }
        if (result.ok) {
            profileContext.refreshProfile();
            toast.success("Profile updated. If you changed emails, please verify your new email address.");
        } else {
            toast.error(result.message ?? "Unable to update profile.");
        }
        profileUpdateSubmission.clear();
    });

    createEffect(() => {
        const result = passwordUpdateSubmission.result;
        if (!result) {
            return;
        }
        if (result.ok) {
            setCurrentPassword("");
            setNewPassword("");
            toast.success("Password updated.");
        } else {
            toast.error(result.message ?? "Unable to update password.");
        }
        passwordUpdateSubmission.clear();
    });

    createEffect(() => {
        const result = verifyEmailSubmission.result;
        if (!result) {
            return;
        }
        if (result.ok) {
            setVerificationCode("");
            profileContext.refreshProfile();
            toast.success("Email verified.");
        } else {
            toast.error(result.message ?? "Verification failed.");
        }
        verifyEmailSubmission.clear();
    });

    createEffect(() => {
        const result = logoutSubmission.result;
        if (!result) {
            return;
        }
        if (result.ok) {
            navigate("/login");
        } else {
            toast.error(result.message ?? "Unable to logout.");
        }
        logoutSubmission.clear();
    });

    const isSavingProfile = () => profileUpdateSubmission.pending;
    const isSavingPassword = () => passwordUpdateSubmission.pending;
    const isVerifyingEmail = () => verifyEmailSubmission.pending;
    const isLoggingOut = () => logoutSubmission.pending;

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

                    <Show when={isUnverified()}>
                        <section class="rounded-xl border border-warning/40 bg-base-100 p-5 shadow-sm">
                            <h2 class="text-lg font-semibold">Email verification</h2>
                            <p class="mt-1 text-sm text-base-content/70">
                                Enter the 6-digit verification code sent to {profileContext.profile()?.email}.
                            </p>
                            <form class="mt-4 grid gap-4 sm:grid-cols-[1fr_auto]" method="post" action={verifyEmailAction}>
                                <label class="form-control">
                                    <span class="label-text">Verification code</span>
                                    <input
                                        id="verifyValue"
                                        name="verifyValue"
                                        type="text"
                                        inputMode="numeric"
                                        class="input input-bordered mt-1"
                                        placeholder="123456"
                                        value={verificationCode()}
                                        onInput={(event) => setVerificationCode(event.currentTarget.value)}
                                    />
                                </label>
                                <input
                                    type="hidden"
                                    name="email"
                                    value={profileContext.profile()?.email ?? ""}
                                />
                                <button type="submit" class="btn btn-primary self-end" disabled={isVerifyingEmail()}>
                                    {isVerifyingEmail() ? "Verifying..." : "Verify email"}
                                </button>
                            </form>
                        </section>
                    </Show>

                    <section class="rounded-xl border border-base-300 bg-base-100 p-5 shadow-sm">
                        <h2 class="text-lg font-semibold">Account details</h2>
                        <p class="mt-1 text-sm text-base-content/70">
                            {canEditEmail()
                                ? "Update your display name and account email."
                                : "Update your display name. Partner account email changes are managed by administrators."}
                        </p>
                        <p class="mt-1 text-xs text-base-content/60">
                            Role: {profileContext.profile()?.role ?? "client"}
                        </p>
                        <form class="mt-4 grid gap-4" method="post" action={updateProfileAction}>
                            <label class="form-control">
                                <span class="label-text">Name</span>
                                <input
                                    type="text"
                                    name="name"
                                    class="input input-bordered mt-1"
                                    value={name()}
                                    onInput={(event) => setName(event.currentTarget.value)}
                                />
                            </label>
                            <Show when={canEditEmail()}>
                                <label class="form-control">
                                    <span class="label-text">Email</span>
                                    <input
                                        type="email"
                                        name="email"
                                        class="input input-bordered mt-1"
                                        value={email()}
                                        onInput={(event) => setEmail(event.currentTarget.value)}
                                    />
                                </label>
                            </Show>
                            <Show when={!canEditEmail()}>
                                <input type="hidden" name="email" value={profileContext.profile()?.email ?? email()} />
                            </Show>
                            <button type="submit" class="btn btn-primary w-fit" disabled={isSavingProfile()}>
                                {isSavingProfile() ? "Saving..." : "Save profile"}
                            </button>
                        </form>
                    </section>

                    <section class="rounded-xl border border-base-300 bg-base-100 p-5 shadow-sm">
                        <h2 class="text-lg font-semibold">Password</h2>
                        <p class="mt-1 text-sm text-base-content/70">Set a new password for this account.</p>
                        <form class="mt-4 grid gap-4" method="post" action={updatePasswordAction}>
                            <label class="form-control">
                                <span class="label-text">Current password</span>
                                <input
                                    type="password"
                                    name="currentPassword"
                                    class="input input-bordered mt-1"
                                    value={currentPassword()}
                                    onInput={(event) => setCurrentPassword(event.currentTarget.value)}
                                />
                            </label>
                            <label class="form-control">
                                <span class="label-text">New password</span>
                                <input
                                    type="password"
                                    name="newPassword"
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
                    <section>
                        <form method="post" action={logoutAction}>
                            <button type="submit" class="btn btn-outline" disabled={isLoggingOut()}>
                                {isLoggingOut() ? "Logging out..." : "Logout"}
                            </button>
                        </form>
                    </section>
                </div>
            </Show>
        </div>
    );
};

export default Profile;
