import type { MapObjectType, RawMapConfig, RawMapObject } from "../types/mapConfig";

/** localStorage key used by both the editor (writer) and MAP_CHOICES (reader)
 *  to ship a custom map between screens without a backend round-trip. */
export const CUSTOM_MAP_STORAGE_KEY = "syncle.customMap";

/** Sentinel URL recognized by loadMapConfig to pull the JSON from
 *  localStorage instead of fetching. */
export const CUSTOM_MAP_URL = "localstorage:custom";

export interface AuthoredObject extends RawMapObject {
  /** Editor-only stable id used as React key + selection target. Distinct
   *  from `id` (the semantic id used by the table-join system). */
  uid: string;
}

export interface AuthoredMap {
  name: string;
  width: number;
  height: number;
  backgroundColor: string;
  objects: AuthoredObject[];
}

const DEFAULT_NAME = "Custom Office";
const DEFAULT_SIZE = { width: 800, height: 600 };
const DEFAULT_BG = "#2a313c";

export function emptyMap(): AuthoredMap {
  return {
    name: DEFAULT_NAME,
    width: DEFAULT_SIZE.width,
    height: DEFAULT_SIZE.height,
    backgroundColor: DEFAULT_BG,
    objects: [],
  };
}

/** Serialize for shipping to MapConfig / localStorage. Drops the editor-only
 *  `uid`, auto-generates table ids/labels if missing so the sit feature works
 *  immediately without extra clicks. */
export function toRawConfig(map: AuthoredMap): RawMapConfig {
  let nextTableNum = 1;
  const objects: RawMapObject[] = map.objects.map((o) => {
    const { uid: _uid, ...rest } = o;
    if (rest.type === "table" && !rest.id) {
      const id = `table-${nextTableNum++}`;
      return { ...rest, id, label: rest.label ?? `Table ${id.slice(6)}` };
    }
    return rest;
  });
  return {
    map_name: map.name,
    background_color: map.backgroundColor,
    width: map.width,
    height: map.height,
    objects,
  };
}

export function loadCustomMap(): AuthoredMap | null {
  try {
    const s = localStorage.getItem(CUSTOM_MAP_STORAGE_KEY);
    if (!s) return null;
    const raw = JSON.parse(s) as RawMapConfig;
    return fromRawConfig(raw);
  } catch {
    return null;
  }
}

export function saveCustomMap(map: AuthoredMap): void {
  const raw = toRawConfig(map);
  localStorage.setItem(CUSTOM_MAP_STORAGE_KEY, JSON.stringify(raw));
}

export function hasCustomMap(): boolean {
  return localStorage.getItem(CUSTOM_MAP_STORAGE_KEY) != null;
}

function fromRawConfig(raw: RawMapConfig): AuthoredMap {
  return {
    name: raw.map_name ?? DEFAULT_NAME,
    width: raw.width ?? DEFAULT_SIZE.width,
    height: raw.height ?? DEFAULT_SIZE.height,
    backgroundColor: raw.background_color ?? DEFAULT_BG,
    objects: (raw.objects ?? []).map((o, i) => ({ ...o, uid: `o${i}-${Math.random().toString(36).slice(2, 7)}` })),
  };
}

/** Tool palette: types the editor lets you place. Order is the rendered
 *  button order, so put structural pieces (wall, table) first. `zone` last
 *  because it's an authoring helper, not a piece of furniture. */
export const TOOL_TYPES: readonly MapObjectType[] = [
  "wall",
  "table",
  "desk",
  "chair",
  "plant",
  "cabinet",
  "rug",
  "door",
  "note",
  "zone",
] as const;

export function newUid(): string {
  return `u${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
}
