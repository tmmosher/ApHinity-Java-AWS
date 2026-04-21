import {batch, createSignal, onCleanup} from "solid-js";
import {ManagedUserPage} from "../../types/Types";

export const MANAGED_USER_SEARCH_DEBOUNCE_MS = 400;

export const createManagedUserSearchControl = (
  initialPage = 0,
  initialSearchDraft = "",
  debounceMs = MANAGED_USER_SEARCH_DEBOUNCE_MS
) => {
  const [page, setPage] = createSignal(initialPage);
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
        setPage(0);
        setSearchQuery(nextSearchDraft.trim());
      });
      timeoutId = undefined;
    }, debounceMs);
  };

  onCleanup(() => {
    clearSearchDebounce();
  });

  return {
    page,
    setPage,
    searchDraft,
    searchQuery,
    updateSearchDraft
  };
};

export const getManagedUserPageRangeLabel = (
  currentPage: ManagedUserPage | undefined,
  searchQuery: string
) => {
  if (!currentPage || currentPage.totalElements === 0) {
    return searchQuery ? "No matching users" : "No users";
  }

  const start = currentPage.page * currentPage.size + 1;
  const end = Math.min(currentPage.totalElements, start + currentPage.users.length - 1);
  const suffix = searchQuery ? " matching users" : "";
  return "Showing " + start + "-" + end + " of " + currentPage.totalElements + suffix;
};

export const getManagedUserEmptyStateMessage = (searchQuery: string) =>
  searchQuery
    ? "No users matched that email search."
    : "No users are available.";
