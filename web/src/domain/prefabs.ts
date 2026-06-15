import type { MapObjectType } from "../types/mapConfig";

/** Categories shown in the editor palette. Order = display order. */
export const PREFAB_CATEGORIES = [
  "structural",
  "decorative",
  "interactive",
  "navigation",
] as const;

export type PrefabCategory = (typeof PREFAB_CATEGORIES)[number];

export interface Prefab {
  /** Map object type produced by this prefab. */
  type: MapObjectType;
  /** Short label shown on the palette button. */
  label: string;
  category: PrefabCategory;
}

/** Single source of truth for the editor palette. Adding a new
 *  MapObjectType means adding an entry here (and the renderer). */
export const PREFABS: readonly Prefab[] = [
  // Structural — block movement.
  { type: "wall", label: "Wall", category: "structural" },
  { type: "table", label: "Table", category: "structural" },
  { type: "desk", label: "Desk", category: "structural" },
  { type: "cabinet", label: "Cabinet", category: "structural" },

  // Decorative — non-solid props (chair is non-solid; plant is solid but
  // visually decorative so we keep it here).
  { type: "chair", label: "Chair", category: "decorative" },
  { type: "plant", label: "Plant", category: "decorative" },
  { type: "rug", label: "Rug", category: "decorative" },
  { type: "door", label: "Door", category: "decorative" },

  // Interactive — non-solid, react to the player.
  { type: "note", label: "Sticky note", category: "interactive" },
  { type: "board", label: "PR board", category: "interactive" },

  // Navigation — non-solid, change scene/awareness.
  { type: "zone", label: "Zone", category: "navigation" },
  { type: "portal", label: "Portal", category: "navigation" },
] as const;

/** Group prefabs by category, preserving PREFAB_CATEGORIES order and the
 *  source order within each category. Pure for unit testing. */
export function groupPrefabs(
  prefabs: readonly Prefab[],
): { category: PrefabCategory; items: Prefab[] }[] {
  const buckets = new Map<PrefabCategory, Prefab[]>();
  for (const c of PREFAB_CATEGORIES) buckets.set(c, []);
  for (const p of prefabs) buckets.get(p.category)!.push(p);
  return PREFAB_CATEGORIES.map((category) => ({
    category,
    items: buckets.get(category)!,
  })).filter((g) => g.items.length > 0);
}

/** Tool palette: types the editor lets you place. Kept as a flat list for
 *  callers that just need the type ids in display order. */
export const PALETTE_TYPES: readonly MapObjectType[] = PREFABS.map(
  (p) => p.type,
);
