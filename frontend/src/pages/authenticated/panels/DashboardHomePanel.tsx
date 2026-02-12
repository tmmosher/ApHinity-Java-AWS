import {createSignal} from "solid-js";
import {toast} from "solid-toast";
import {useProfile} from "../../../context/ProfileContext";
import {getFavoriteLocationName, setFavoriteLocationName} from "../../../util/favoriteLocation";

const roleLabel: Record<"admin" | "partner" | "client", string> = {
  admin: "Admin",
  partner: "Partner",
  client: "Client"
};

export const DashboardHomePanel = () => {
  const profileContext = useProfile();
  const [favoriteLocation, setFavoriteLocation] = createSignal(getFavoriteLocationName());

  const roleDescription = () => {
    const profile = profileContext.profile();
    if (!profile) {
      return "Loading account...";
    }
    return `Signed in as ${roleLabel[profile.role]}.`;
  };

  const saveFavoriteLocation = (event: SubmitEvent) => {
    event.preventDefault();
    setFavoriteLocationName(favoriteLocation());
    toast.success("Favorite location updated.");
  };

  const clearFavoriteLocation = () => {
    setFavoriteLocation("");
    setFavoriteLocationName("");
    toast.success("Favorite location cleared.");
  };

  return (
    <div class="space-y-6">
      <header class="space-y-1">
        <h1 class="text-3xl font-semibold tracking-tight">Dashboard</h1>
        <p class="text-base-content/70">{roleDescription()}</p>
      </header>

      <section class="rounded-xl border border-base-300 bg-base-100 p-5 shadow-sm">
        <h2 class="text-lg font-semibold">Favorite location</h2>
        <p class="mt-1 text-sm text-base-content/70">
          Save one location name for quick recall in your home dashboard.
        </p>
        <form class="mt-4 grid gap-3 sm:grid-cols-[1fr_auto_auto]" onSubmit={saveFavoriteLocation}>
          <input
            type="text"
            class="input input-bordered w-full"
            value={favoriteLocation()}
            placeholder="Location name"
            maxlength={128}
            onInput={(event) => setFavoriteLocation(event.currentTarget.value)}
          />
          <button type="submit" class="btn btn-primary">
            Save
          </button>
          <button type="button" class="btn btn-outline" onClick={clearFavoriteLocation}>
            Clear
          </button>
        </form>
      </section>
    </div>
  );
};
