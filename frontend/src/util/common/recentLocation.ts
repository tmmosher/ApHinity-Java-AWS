const RECENT_LOCATION_IDS_KEY = "aphinity.recentLocationIds";
export const MAX_RECENT_LOCATION_IDS = 3;

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

const readStoredRecentLocationIds = (): string[] => {
  if (!canUseLocalStorage()) {
    return [];
  }

  const value = window.localStorage.getItem(RECENT_LOCATION_IDS_KEY);
  if (!value) {
    return [];
  }

  try {
    const parsed = JSON.parse(value) as unknown;
    if (!Array.isArray(parsed)) {
      return [];
    }

    const seen = new Set<string>();
    const ids: string[] = [];
    for (const entry of parsed) {
      const normalized = normalizeLocationId(String(entry));
      if (!normalized || seen.has(normalized)) {
        continue;
      }
      seen.add(normalized);
      ids.push(normalized);
      if (ids.length >= MAX_RECENT_LOCATION_IDS) {
        break;
      }
    }
    return ids;
  } catch {
    return [];
  }
};

const writeStoredRecentLocationIds = (value: string[]) => {
  if (!canUseLocalStorage()) {
    return;
  }
  window.localStorage.setItem(RECENT_LOCATION_IDS_KEY, JSON.stringify(value));
};

export const getRecentLocationIds = (): string[] => readStoredRecentLocationIds();

export const recordRecentLocationId = (value: string | number) => {
  const normalized = normalizeLocationId(String(value));
  if (!normalized || !canUseLocalStorage()) {
    return;
  }

  const nextIds = [
    normalized,
    ...readStoredRecentLocationIds().filter((locationId) => locationId !== normalized)
  ].slice(0, MAX_RECENT_LOCATION_IDS);

  writeStoredRecentLocationIds(nextIds);
};

export const getQuickAccessLocations = <T extends {id: number}>(
  locations: ReadonlyArray<T> | undefined,
  favoriteLocationId: string,
  recentLocationIds: ReadonlyArray<string>
): T[] => {
  const locationById = new Map<string, T>();
  for (const location of locations ?? []) {
    locationById.set(String(location.id), location);
  }

  const selectedLocations: T[] = [];
  const selectedLocationIds = new Set<string>();
  const addLocation = (locationId: string) => {
    const normalized = normalizeLocationId(locationId);
    if (!normalized || selectedLocationIds.has(normalized)) {
      return;
    }

    const location = locationById.get(normalized);
    if (!location) {
      return;
    }

    selectedLocationIds.add(normalized);
    selectedLocations.push(location);
  };

  for (const locationId of recentLocationIds.slice(0, MAX_RECENT_LOCATION_IDS)) {
    addLocation(locationId);
  }

  const normalizedFavoriteLocationId = normalizeLocationId(favoriteLocationId);
  if (normalizedFavoriteLocationId && !selectedLocationIds.has(normalizedFavoriteLocationId)) {
    addLocation(normalizedFavoriteLocationId);
  }

  return selectedLocations;
};
