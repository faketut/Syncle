import { describe, it, expect } from "vitest";
import {
  PALETTE_TYPES,
  PREFAB_CATEGORIES,
  PREFABS,
  groupPrefabs,
} from "../prefabs";

describe("prefabs catalog", () => {
  it("includes every MapObjectType used in the renderer", () => {
    const types = new Set(PREFABS.map((p) => p.type));
    for (const t of [
      "wall",
      "table",
      "desk",
      "cabinet",
      "chair",
      "plant",
      "rug",
      "door",
      "note",
      "zone",
      "portal",
      "board",
    ] as const) {
      expect(types.has(t)).toBe(true);
    }
  });

  it("PALETTE_TYPES preserves PREFABS order", () => {
    expect(PALETTE_TYPES).toEqual(PREFABS.map((p) => p.type));
  });

  it("every prefab belongs to a known category", () => {
    for (const p of PREFABS) {
      expect(PREFAB_CATEGORIES).toContain(p.category);
    }
  });
});

describe("groupPrefabs", () => {
  it("returns categories in PREFAB_CATEGORIES order", () => {
    const groups = groupPrefabs(PREFABS);
    expect(groups.map((g) => g.category)).toEqual(
      PREFAB_CATEGORIES.filter((c) =>
        PREFABS.some((p) => p.category === c),
      ),
    );
  });

  it("groups every prefab exactly once", () => {
    const groups = groupPrefabs(PREFABS);
    const flat = groups.flatMap((g) => g.items);
    expect(flat).toHaveLength(PREFABS.length);
    expect(new Set(flat).size).toBe(PREFABS.length);
  });

  it("drops empty categories from the result", () => {
    const groups = groupPrefabs([
      { type: "wall", label: "Wall", category: "structural" },
    ]);
    expect(groups).toHaveLength(1);
    expect(groups[0].category).toBe("structural");
  });

  it("includes portal in navigation category", () => {
    const groups = groupPrefabs(PREFABS);
    const nav = groups.find((g) => g.category === "navigation");
    expect(nav).toBeDefined();
    expect(nav!.items.map((p) => p.type)).toContain("portal");
  });
});
