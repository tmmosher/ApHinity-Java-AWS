export type ThemePreference = "light" | "dark";

const THEME_PREFERENCE_STORAGE_KEY = "aphinity_theme_preference";
const DEFAULT_THEME_PREFERENCE: ThemePreference = "light";

export const getStoredThemePreference = (): ThemePreference => {
  if (typeof window === "undefined") {
    return DEFAULT_THEME_PREFERENCE;
  }
  const storedPreference = window.localStorage.getItem(THEME_PREFERENCE_STORAGE_KEY);
  if (storedPreference === "dark") {
    return "dark";
  }
  return DEFAULT_THEME_PREFERENCE;
};

export const applyThemePreference = (preference: ThemePreference): void => {
  if (typeof document === "undefined") {
    return;
  }
  document.documentElement.setAttribute("data-theme", preference);
};

export const setStoredThemePreference = (preference: ThemePreference): void => {
  if (typeof window !== "undefined") {
    window.localStorage.setItem(THEME_PREFERENCE_STORAGE_KEY, preference);
  }
  applyThemePreference(preference);
};

export const initializeThemePreference = (): void => {
  applyThemePreference(getStoredThemePreference());
};
