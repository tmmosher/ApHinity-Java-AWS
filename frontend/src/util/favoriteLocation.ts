const FAVORITE_LOCATION_KEY = "aphinity.favoriteLocationId";

// some privacy extensions may break favorite finding by blocking localstorage. Should probably add this
// elsewhere actually or extract to more general api.
const canUseLocalStorage = () =>
  typeof window !== "undefined" && typeof window.localStorage !== "undefined";

const normalizeLocationId = (value: string): string => {
  const trimmed = value.trim();
  if (!trimmed) {
    return "";
  }
  const parsed = Number(trimmed);
  if (!Number.isInteger(parsed) || parsed <= 0) {
    return "";
  }
  return String(parsed);
};

const toLocationIdMap = (locations: ReadonlyArray<{id: number}> | undefined): Map<string, number> => {
  const map = new Map<string, number>();
  if (!locations) {
    return map;
  }
  for (const location of locations) {
    map.set(String(location.id), location.id);
  }
  return map;
};

export const hasSelectableFavoriteLocation = (
  selectedId: string,
  locations: ReadonlyArray<{id: number}> | undefined
): boolean => {
  const normalizedSelectedId = normalizeLocationId(selectedId);
  if (!normalizedSelectedId) {
    return false;
  }
  return toLocationIdMap(locations).has(normalizedSelectedId);
};

export const getFavoriteLocationIdForSave = (
  selectedId: string,
  locations: ReadonlyArray<{id: number}> | undefined
): number | null => {
  const normalizedSelectedId = normalizeLocationId(selectedId);
  if (!normalizedSelectedId) {
    return null;
  }
  return toLocationIdMap(locations).get(normalizedSelectedId) ?? null;
};

export const getFavoriteLocationId = (): string => {
  if (!canUseLocalStorage()) {
    return "";
  }
  const value = window.localStorage.getItem(FAVORITE_LOCATION_KEY);
  if (!value) {
    return "";
  }
  return normalizeLocationId(value);
};

export const setFavoriteLocationId = (value: string | number) => {
  if (!canUseLocalStorage()) {
    return;
  }
  const normalized = normalizeLocationId(String(value));
  if (!normalized) {
    window.localStorage.removeItem(FAVORITE_LOCATION_KEY);
    return;
  }
  window.localStorage.setItem(FAVORITE_LOCATION_KEY, normalized);
};
