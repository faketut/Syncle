// Pure layout helpers for the meeting view (M4). Kept in `domain/` so the
// React layer can stay declarative and the math is unit-tested.

export interface GridLayout {
  /** Number of tiles per row. */
  cols: number;
  /** Number of rows. */
  rows: number;
}

/** Maximum tiles we ever render in the meeting grid. Anything beyond is
 *  surfaced as a "+N more" pill so layouts stay readable. Mirrors Gather's
 *  cap of 9 visible tiles. */
export const MAX_VISIBLE_TILES = 9 as const;

/**
 * Returns the grid dimensions for `n` tiles.
 *
 *   n=0  → 1×1 (caller decides to render an empty state)
 *   n=1  → 1×1
 *   n=2  → 2×1
 *   n=3-4 → 2×2
 *   n=5-6 → 3×2
 *   n=7-9 → 3×3
 *
 * `n` is clamped to [0, MAX_VISIBLE_TILES]; over that, callers should show a
 * "+N more" indicator and still pass MAX_VISIBLE_TILES here.
 */
export function gridLayout(n: number): GridLayout {
  const clamped = Math.max(0, Math.min(MAX_VISIBLE_TILES, Math.floor(n)));
  if (clamped <= 1) return { cols: 1, rows: 1 };
  if (clamped === 2) return { cols: 2, rows: 1 };
  if (clamped <= 4) return { cols: 2, rows: 2 };
  if (clamped <= 6) return { cols: 3, rows: 2 };
  return { cols: 3, rows: 3 };
}

/** How many of the supplied tiles are NOT rendered because we capped at
 *  MAX_VISIBLE_TILES. Returns 0 when nothing is hidden. */
export function overflowCount(n: number): number {
  return Math.max(0, n - MAX_VISIBLE_TILES);
}
