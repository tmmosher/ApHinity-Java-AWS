import {batch, createSignal, onCleanup} from "solid-js";

export const LOCATION_REACTIVE_SEARCH_DEBOUNCE_MS = 400;

export const createLocationReactiveSearchControl = (
  initialSearchDraft = "",
  debounceMs = LOCATION_REACTIVE_SEARCH_DEBOUNCE_MS
) => {
  const [searchDraft, setSearchDraft] = createSignal(initialSearchDraft);
  const [searchQuery, setSearchQuery] = createSignal(initialSearchDraft.trim());
  let timeoutId: ReturnType<typeof globalThis.setTimeout> | undefined;

  const updateSearchDraft = (nextSearchDraft: string) => {
    setSearchDraft(nextSearchDraft);
    if (timeoutId !== undefined) {
      globalThis.clearTimeout(timeoutId);
    }

    timeoutId = globalThis.setTimeout(() => {
      batch(() => {
        setSearchQuery(nextSearchDraft.trim());
      });
      timeoutId = undefined;
    }, debounceMs);
  };

  onCleanup(() => {
    if (timeoutId !== undefined) {
      globalThis.clearTimeout(timeoutId);
    }
  });

  return {
    searchDraft,
    searchQuery,
    updateSearchDraft,
    setSearchDraft,
    setSearchQuery
  };
};
