export type ThemePreference = "light" | "dark";

const THEME_PREFERENCE_STORAGE_KEY = "aphinity_theme_preference";
const DEFAULT_THEME_PREFERENCE: ThemePreference = "light";
const DAISY_THEME_BY_PREFERENCE: Record<ThemePreference, string> = {
  light: "corporate",
  dark: "forest-corporate"
};
const THEME_PREFERENCE_BY_DAISY_THEME: Record<string, ThemePreference> = {
  corporate: "light",
  "forest-corporate": "dark",
  light: "light",
  dark: "dark"
};

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

export const getDaisyThemeForPreference = (preference: ThemePreference): string =>
  DAISY_THEME_BY_PREFERENCE[preference];

export const getThemePreferenceFromDaisyTheme = (
  daisyTheme: string | null | undefined
): ThemePreference | null => {
  if (!daisyTheme) {
    return null;
  }
  return THEME_PREFERENCE_BY_DAISY_THEME[daisyTheme.toLowerCase()] ?? null;
};

export const getDocumentThemePreference = (): ThemePreference => {
  if (typeof document === "undefined") {
    return getStoredThemePreference();
  }
  return (
    getThemePreferenceFromDaisyTheme(document.documentElement.getAttribute("data-theme")) ??
    getStoredThemePreference()
  );
};

export const applyThemePreference = (preference: ThemePreference): void => {
  if (typeof document === "undefined") {
    return;
  }
  document.documentElement.setAttribute("data-theme", getDaisyThemeForPreference(preference));
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
