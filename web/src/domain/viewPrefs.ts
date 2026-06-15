/** View preferences persisted to localStorage. Kept in `domain/` so it can
 *  be unit-tested with a mocked storage. */

export type ThemeMode = "light" | "dark";

export const THEME_KEY = "syncle.theme";
export const MINI_MODE_KEY = "syncle.miniMode";

/** Parse a stored theme value. Defaults to "dark" (the original Syncle
 *  visual identity); only an explicit "light" returns light. */
export function parseTheme(raw: string | null): ThemeMode {
  return raw === "light" ? "light" : "dark";
}

/** Parse a stored mini-mode value. Treats "1" as true; anything else false. */
export function parseMiniMode(raw: string | null): boolean {
  return raw === "1";
}

/** Toggle helper used by the HUD button. Pure for unit testing. */
export function nextTheme(current: ThemeMode): ThemeMode {
  return current === "dark" ? "light" : "dark";
}

/** Storage shim. Browser code uses `localStorage`; tests pass a fake. */
export interface PrefStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
}

export function readTheme(storage: PrefStorage): ThemeMode {
  try {
    return parseTheme(storage.getItem(THEME_KEY));
  } catch {
    return "dark";
  }
}

export function writeTheme(storage: PrefStorage, theme: ThemeMode): void {
  try {
    storage.setItem(THEME_KEY, theme);
  } catch {
    /* ignore quota/private-mode errors */
  }
}

export function readMiniMode(storage: PrefStorage): boolean {
  try {
    return parseMiniMode(storage.getItem(MINI_MODE_KEY));
  } catch {
    return false;
  }
}

export function writeMiniMode(storage: PrefStorage, on: boolean): void {
  try {
    storage.setItem(MINI_MODE_KEY, on ? "1" : "0");
  } catch {
    /* ignore */
  }
}
