/** Sprite atlas: maps semantic asset names to (sheet, sx, sy, sw, sh)
 *  pixel rects on the shared sheets in `web/public/sprites/`.
 *
 *  Sheets (synced from `app/src/main/assets/` via `scripts/sync-assets.mjs`):
 *  - `room-builder-tile.png` (272×368, 16×16 cells): walls on the left,
 *    floor pattern swatches on the right (cols 12–13).
 *  - `interior-tile.png` (256×1424): freeform furniture/decor props
 *    (NOT a strict 16×16 grid — each item must be box-selected).
 *  - `16x16-walk-sheet.png` (144×120, 24×24 cells): a 6×5 grid of 30
 *    static character designs. Most face south; this sheet does NOT
 *    contain a 4-directional walk cycle. For real left/right/up/down
 *    walking, swap in a sheet that ships per-direction frames (e.g.
 *    Sprout Lands characters on itch.io). Current behavior: each
 *    identity gets a stable cell via `charCellForIdentity()` and the
 *    character is drawn statically.
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

export type SpriteSheetKey = "rooms" | "interior" | "chars";

export const SHEET_URLS: Record<SpriteSheetKey, string> = {
  rooms:    "/sprites/room-builder-tile.png",
  interior: "/sprites/interior-tile.png",
  chars:    "/sprites/16x16-walk-sheet.png",
};

/** Tile used to repeat-fill the floor. Pick any 16×16 cell from
 *  `room-builder-tile.png`. Floor pattern swatches live on the right side
 *  of the sheet (cols 12–13, x=192–223), each as a 32×32 block:
 *  - (192,  80) red brick
 *  - (192, 112) yellow checker  ← current default (warm, Gather-like)
 *  - (192, 144) cyan diamond
 *  - (192, 176) light gray plain
 *  - (192, 208) red chevron
 *  Any 16×16 sub-cell of those blocks should tile seamlessly. */
export const FLOOR_TILE: SpriteRect = {
  sheet: "rooms",
  sx: 192, sy: 112, sw: 16, sh: 16,
};

/** Character sheet geometry. 144 / 6 = 24 wide, 120 / 5 = 24 tall. */
export const CHAR_CELL_W = 24;
export const CHAR_CELL_H = 24;
export const CHAR_GRID_COLS = 6;
export const CHAR_GRID_ROWS = 5;
export const CHAR_GRID_COUNT = CHAR_GRID_COLS * CHAR_GRID_ROWS; // 30

/** Furniture / decor sprite picks per MapObjectType. Add entries here as
 *  you identify exact cell coordinates on `interior-tile.png`. When an
 *  entry exists, the renderer draws the sprite centered in the object's
 *  rect (nearest-neighbor scaled) instead of running the procedural
 *  fake-3D path. Walls are intentionally NOT here — they're freeform
 *  rectangles that don't tile well without a full autotile system. */
export const FURNITURE: Partial<Record<MapObjectType, SpriteRect>> = {
  // Examples to populate later (coords are placeholders, verify visually):
  // table:   { sheet: "interior", sx: 0,   sy: 752, sw: 32, sh: 32 },
  // chair:   { sheet: "interior", sx: 0,   sy: 560, sw: 16, sh: 24 },
  // cabinet: { sheet: "interior", sx: 80,  sy: 144, sw: 32, sh: 48 },
  // plant:   { sheet: "interior", sx: 96,  sy: 928, sw: 16, sh: 32 },
  // desk:    { sheet: "interior", sx: 0,   sy: 656, sw: 32, sh: 32 },
};

/** Pick a deterministic character cell for an identity. Same identity →
 *  same skin across sessions. */
export function charCellForIdentity(identity: string): { col: number; row: number } {
  let h = 2166136261;
  for (let i = 0; i < identity.length; i++) {
    h = (h ^ identity.charCodeAt(i)) * 16777619;
    h = h >>> 0;
  }
  const idx = h % CHAR_GRID_COUNT;
  return { col: idx % CHAR_GRID_COLS, row: Math.floor(idx / CHAR_GRID_COLS) };
}
