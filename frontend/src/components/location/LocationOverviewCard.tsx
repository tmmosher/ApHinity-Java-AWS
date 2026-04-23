import {A} from "@solidjs/router";
import Popover from "corvu/popover";
import {createSignal, Show} from "solid-js";
import {toast} from "solid-toast";
import {LocationSummary} from "../../types/Types";
import {getLocationCardArtStyle} from "../../util/location/locationCardArt";

type LocationOverviewCardProps = {
  location: LocationSummary;
  isFavorite: boolean;
  canEditLocations: boolean;
  onFavorite: (locationId: number) => void;
  onRename: (locationId: number, nextName: string) => Promise<boolean>;
};

const StarFilledIcon = () => (
  <svg
    aria-hidden="true"
    xmlns="http://www.w3.org/2000/svg"
    width="18"
    height="18"
    viewBox="0 0 24 24"
    fill="currentColor"
    stroke="currentColor"
    stroke-width="2"
    stroke-linecap="round"
    stroke-linejoin="round"
  >
    <path d="m12 2 3.09 6.26L22 9.27l-5 4.87L18.18 22 12 18.56 5.82 22 7 14.14 2 9.27l6.91-1.01L12 2z" />
  </svg>
);

const StarOutlineIcon = () => (
  <svg
    aria-hidden="true"
    xmlns="http://www.w3.org/2000/svg"
    width="18"
    height="18"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="2"
    stroke-linecap="round"
    stroke-linejoin="round"
  >
    <path d="m12 2 3.09 6.26L22 9.27l-5 4.87L18.18 22 12 18.56 5.82 22 7 14.14 2 9.27l6.91-1.01L12 2z" />
  </svg>
);

const DotsIcon = () => (
  <svg
    aria-hidden="true"
    xmlns="http://www.w3.org/2000/svg"
    width="18"
    height="18"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="2"
    stroke-linecap="round"
    stroke-linejoin="round"
  >
    <circle cx="5" cy="12" r="1.5" />
    <circle cx="12" cy="12" r="1.5" />
    <circle cx="19" cy="12" r="1.5" />
  </svg>
);

const formatUpdatedAt = (updatedAt: string): string =>
  new Date(updatedAt).toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric"
  });

export const LocationOverviewCard = (props: LocationOverviewCardProps) => {
  const [renameDraft, setRenameDraft] = createSignal(props.location.name);
  const [renameError, setRenameError] = createSignal("");
  const [isRenaming, setIsRenaming] = createSignal(false);

  const saveRename = async (): Promise<boolean> => {
    if (isRenaming()) {
      return false;
    }

    const nextName = renameDraft().trim();
    if (!nextName || nextName === props.location.name) {
      setRenameError("");
      return true;
    }

    setRenameError("");
    setIsRenaming(true);
    try {
      const didSave = await props.onRename(props.location.id, nextName);
      if (!didSave) {
        setRenameError("Unable to update location name.");
        return false;
      }
      toast.success("Location updated.");
      return true;
    } catch (error) {
      setRenameError(error instanceof Error ? error.message : "Unable to update location name.");
      return false;
    } finally {
      setIsRenaming(false);
    }
  };

  return (
    <article class="group relative overflow-hidden rounded-3xl border border-base-300 bg-base-100 shadow-sm transition duration-150 ease-out hover:-translate-y-1 hover:shadow-xl">
      <div class="absolute right-3 top-3 z-20 flex items-center gap-2">
        <button
          type="button"
          class={
            "btn btn-circle btn-sm border border-base-300 bg-base-100/90 shadow-sm backdrop-blur " +
            (props.isFavorite ? "text-warning" : "text-base-content/70")
          }
          aria-label={props.isFavorite ? "Favorite location" : "Set favorite location"}
          aria-pressed={props.isFavorite}
          onClick={() => props.onFavorite(props.location.id)}
        >
          {props.isFavorite ? <StarFilledIcon /> : <StarOutlineIcon />}
        </button>

        <Show when={props.canEditLocations}>
          <Popover
            placement="bottom-end"
            onOpenChange={(open) => {
              if (open) {
                setRenameDraft(props.location.name);
                setRenameError("");
                return;
              }
              setRenameError("");
              setRenameDraft(props.location.name);
            }}
          >
            {(popover) => (
              <>
                <Popover.Trigger
                  type="button"
                  class="btn btn-circle btn-sm border border-base-300 bg-base-100/90 shadow-sm backdrop-blur text-base-content/70"
                  aria-label="Rename location"
                  disabled={isRenaming()}
                >
                  <DotsIcon />
                </Popover.Trigger>
                <Popover.Portal>
                  <Popover.Content class="w-[min(92vw,18rem)] rounded-2xl border border-base-300 bg-base-100 p-4 shadow-xl">
                    <div class="space-y-3">
                      <div class="space-y-1">
                        <Popover.Label class="text-sm font-semibold">
                          Rename location
                        </Popover.Label>
                        <Popover.Description class="text-xs text-base-content/70">
                          Update the name shown on this card and in the dashboard.
                        </Popover.Description>
                      </div>
                      <label class="form-control w-full">
                        <span class="label-text text-xs">Location name</span>
                        <input
                          type="text"
                          class="input input-bordered input-sm w-full"
                          value={renameDraft()}
                          maxlength={128}
                          disabled={isRenaming()}
                          onInput={(event) => {
                            setRenameDraft(event.currentTarget.value);
                            if (renameError()) {
                              setRenameError("");
                            }
                          }}
                        />
                      </label>
                      <Show when={renameError()}>
                        <p class="text-xs text-error">{renameError()}</p>
                      </Show>
                      <div class="flex items-center justify-end gap-2">
                        <Popover.Close class="btn btn-ghost btn-sm" disabled={isRenaming()}>
                          Cancel
                        </Popover.Close>
                        <button
                          type="button"
                          class={"btn btn-sm " + (isRenaming() ? "btn-disabled" : "btn-primary")}
                          disabled={isRenaming() || !renameDraft().trim()}
                          onClick={() => void saveRename().then((didSave) => {
                            if (didSave) {
                              popover.setOpen(false);
                            }
                          })}
                        >
                          {isRenaming() ? "Saving..." : "Save"}
                        </button>
                      </div>
                    </div>
                  </Popover.Content>
                </Popover.Portal>
              </>
            )}
          </Popover>
        </Show>
      </div>

      <A
        href={`/dashboard/locations/${props.location.id}`}
        preload
        class="relative z-0 flex h-full min-h-[19rem] flex-col overflow-hidden"
        aria-label={`Open location ${props.location.name}`}
      >
        <div class="relative basis-[68%] overflow-hidden">
          <div
            class="absolute inset-0 transition duration-300 ease-out group-hover:scale-[1.03]"
            style={getLocationCardArtStyle(props.location)}
          />
          <div class="absolute inset-0 bg-[radial-gradient(circle_at_top_right,_rgba(255,255,255,0.18),_transparent_35%),radial-gradient(circle_at_bottom_left,_rgba(255,255,255,0.10),_transparent_36%)]" />
          <div class="absolute inset-x-0 bottom-0 h-20 bg-gradient-to-t from-base-100/25 via-transparent to-transparent" />
        </div>

        <div class="flex basis-[32%] flex-col justify-end gap-1 bg-gradient-to-b from-base-100/95 to-base-100 p-5">
          <h3 class="text-xl font-semibold tracking-tight text-base-content">
            {props.location.name}
          </h3>
          <p class="text-xs text-base-content/60">
            Updated {formatUpdatedAt(props.location.updatedAt)}
          </p>
        </div>
      </A>
    </article>
  );
};
