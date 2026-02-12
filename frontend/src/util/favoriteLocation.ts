const FAVORITE_LOCATION_KEY = "aphinity.favoriteLocationName";
const MAX_FAVORITE_LENGTH = 128;

const canUseLocalStorage = () =>
  typeof window !== "undefined" && typeof window.localStorage !== "undefined";

const normalizeLocationName = (value: string): string =>
  value.trim().slice(0, MAX_FAVORITE_LENGTH);

export const getFavoriteLocationName = (): string => {
  if (!canUseLocalStorage()) {
    return "";
  }
  const value = window.localStorage.getItem(FAVORITE_LOCATION_KEY);
  if (!value) {
    return "";
  }
  return normalizeLocationName(value);
};

export const setFavoriteLocationName = (value: string) => {
  if (!canUseLocalStorage()) {
    return;
  }
  const normalized = normalizeLocationName(value);
  if (!normalized) {
    window.localStorage.removeItem(FAVORITE_LOCATION_KEY);
    return;
  }
  window.localStorage.setItem(FAVORITE_LOCATION_KEY, normalized);
};
