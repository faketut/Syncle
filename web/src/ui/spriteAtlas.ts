/** Sprite atlas: maps semantic asset names to (sheet, sx, sy, sw, sh)
 *  pixel rects on the shared sheets in `web/public/sprites/`.
 *
 *  Source pack (16×16 pixel art):
 *  - `walls-floor-doors.png` (128×80) — interior wall/floor/door brick.
 *    8×5 grid. The pack treats brick as the universal interior surface;
 *    "floor" is just a clean wall-middle cell tiled across.
 *  - `furniture.png` (128×64) — 8×4 grid of single-cell furniture
 *    (chairs, beds, plants, chests, drawers, barrels).
 *  - `carpets.png` (128×288) — 18 rows of orange / blue / pink carpets,
 *    each carpet is roughly 4×6 cells. Centers are solid color so any
 *    single cell tiles cleanly.
 *  - `bigset.png` (128×432) — composite of carpets + walls + furniture.
 *    Kept for editor preview; runtime uses the split sheets above.
 *  - `chars/char_NN.png` (16×16 each, NN = 01..50) — 50 single-frame
 *    character portraits, all facing south. NO walk cycle in this
 *    pack — see `charUrlForIdentity()` below for how identities map
 *    to a stable picture.
 *  - `showcase-reference.png` (1088×1088) — not used at runtime; a
 *    rendered demo room shipped for art reference.
 *
 *  Tile picker: open `/tile-picker.html` while `npm run dev` is up to
 *  click cells and copy ready-to-paste SpriteRect entries. */
import type { MapObjectType } from "../types/mapConfig";

export interface SpriteRect {
  sheet: SpriteSheetKey;
  sx: number;
  sy: number;
  sw: number;
  sh: number;
}

export type SpriteSheetKey = "walls" | "furniture" | "carpets";

export const SHEET_URLS: Record<SpriteSheetKey, string> = {
  walls:     "/sprites/walls-floor-doors.png",
  furniture: "/sprites/furniture.png",
  carpets:   "/sprites/carpets.png",
};

/** Tile used to repeat-fill the floor. The walls sheet uses a brick
 *  texture for both walls and floor; pick a clean center cell so seams
 *  don't show. (16, 16) is the wall-middle brick in row 1 col 1 —
 *  warm interior look. To preview alternatives, open `/tile-picker.html`
 *  and click cells in walls-floor-doors.png. */
export const FLOOR_TILE: SpriteRect = {
  sheet: "walls",
  sx: 16, sy: 16, sw: 16, sh: 16,
};

/** Character portraits: 50 individual 16×16 PNGs under
 *  `/sprites/chars/char_NN.png` (NN zero-padded 01..50). Each identity
 *  hashes to a stable file. There is no walk cycle in this pack — the
 *  same single frame is drawn every tick. */
export const CHAR_COUNT = 50;
export const CHAR_TILE_SIZE = 16;

/** Stable FNV-1a hash → char_01..char_50 URL for an identity. Same id
 *  always renders as the same character. */
export function charUrlForIdentity(identity: string): string {
  let h = 2166136261;
  for (let i = 0; i < identity.length; i++) {
    h = (h ^ identity.charCodeAt(i)) * 16777619;
    h = h >>> 0;
  }
  const n = (h % CHAR_COUNT) + 1; // 1..50
  return `/sprites/chars/char_${String(n).padStart(2, "0")}.png`;
}

/** Furniture / decor sprite picks per MapObjectType. Best-guess from the
 *  furniture sheet's 8×4 grid of 16×16 cells; verify visually with
 *  `/tile-picker.html` and adjust. When an entry exists, the renderer
 *  draws the sprite centered in the object's rect (nearest-neighbor
 *  scaled) instead of the procedural fake-3D path. Walls / doors /
 *  zones / notes / portals / boards stay procedural. */
export const FURNITURE: Partial<Record<MapObjectType, SpriteRect>> = {
  // Row 2 col 1 — blue chair, 16×16.
  chair:   { sheet: "furniture", sx: 16, sy: 32, sw: 16, sh: 16 },
  // Row 0 col 6 — small side table.
  table:   { sheet: "furniture", sx: 96, sy: 0,  sw: 16, sh: 16 },
  // Same side-table sprite used for desk.
  desk:    { sheet: "furniture", sx: 96, sy: 0,  sw: 16, sh: 16 },
  // Row 0 col 3 — bookshelf (tall furniture).
  cabinet: { sheet: "furniture", sx: 48, sy: 0,  sw: 16, sh: 16 },
  // Row 1 col 4 — small green plant in pot.
  plant:   { sheet: "furniture", sx: 64, sy: 16, sw: 16, sh: 16 },
};

/** Carpet pick used by the `rug` MapObjectType. The carpets sheet's
 *  cells tile in their center; rugs in maps are typically 2–3 tiles
 *  wide so we only need one 16×16 to repeat. */
export const RUG_TILE: SpriteRect = {
  sheet: "carpets",
  sx: 16, sy: 16, sw: 16, sh: 16,
};
