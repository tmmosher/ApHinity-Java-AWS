import {batch, createSignal, onCleanup} from "solid-js";

export const LOCATION_REACTIVE_SEARCH_DEBOUNCE_MS = 400;

export const createLocationReactiveSearchControl = (
  initialSearchDraft = "",
  debounceMs = LOCATION_REACTIVE_SEARCH_DEBOUNCE_MS
) => {
  const [searchDraft, setSearchDraft] = createSignal(initialSearchDraft);
  const [searchQuery, setSearchQuery] = createSignal(initialSearchDraft.trim());
  let timeoutId: ReturnType<typeof globalThis.setTimeout> | undefined;

  const clearSearchDebounce = (): void => {
    if (timeoutId !== undefined) {
      globalThis.clearTimeout(timeoutId);
      timeoutId = undefined;
    }
  };

  const updateSearchDraft = (nextSearchDraft: string) => {
    setSearchDraft(nextSearchDraft);
    clearSearchDebounce();

    timeoutId = globalThis.setTimeout(() => {
      batch(() => {
        setSearchQuery(nextSearchDraft.trim());
      });
      timeoutId = undefined;
    }, debounceMs);
  };

  onCleanup(() => {
    clearSearchDebounce();
  });

  return {
    searchDraft,
    searchQuery,
    updateSearchDraft,
    setSearchDraft,
    setSearchQuery
  };
};
