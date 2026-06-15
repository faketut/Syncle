import { describe, expect, it } from "vitest";
import { bucketByZone, findZoneAt, zonesOf } from "../zones";
import type { MapConfig, MapObject } from "../../types/mapConfig";

function makeMap(objects: MapObject[]): MapConfig {
  return {
    name: "test",
    backgroundImage: null,
    backgroundColor: "#000",
    walkable: [],
    tables: [],
    objects,
    bounds: { x: 0, y: 0, width: 1000, height: 1000 },
  };
}

const lounge: MapObject = {
  type: "zone",
  id: "lounge",
  label: "Lounge",
  x: 0,
  y: 0,
  width: 100,
  height: 100,
};

const eng: MapObject = {
  type: "zone",
  id: "eng",
  label: "Engineering",
  x: 200,
  y: 0,
  width: 100,
  height: 100,
};

const overlap: MapObject = {
  type: "zone",
  id: "overlap",
  label: "Overlap",
  x: 50,
  y: 50,
  width: 100,
  height: 100,
};

describe("zonesOf", () => {
  it("extracts only zone-typed objects with labels and ids", () => {
    const map = makeMap([
      { type: "wall", x: 0, y: 0, width: 10, height: 10 },
      lounge,
      eng,
    ]);
    const zones = zonesOf(map);
    expect(zones.map((z) => z.key)).toEqual(["lounge", "eng"]);
    expect(zones[0].label).toBe("Lounge");
  });

  it("falls back to a synthetic label when none is provided", () => {
    const map = makeMap([{ type: "zone", x: 0, y: 0, width: 1, height: 1 }]);
    expect(zonesOf(map)[0].label).toBe("Zone 1");
  });
});

describe("findZoneAt", () => {
  it("returns the zone containing the point", () => {
    const map = makeMap([lounge, eng]);
    expect(findZoneAt(50, 50, map)?.key).toBe("lounge");
    expect(findZoneAt(250, 50, map)?.key).toBe("eng");
    expect(findZoneAt(150, 150, map)).toBeNull();
  });

  it("last-defined zone wins on overlap (matches draw order)", () => {
    const map = makeMap([lounge, overlap]);
    expect(findZoneAt(75, 75, map)?.key).toBe("overlap");
  });
});

describe("bucketByZone", () => {
  const avatars = [
    { identity: "a", name: "Alice", color: "#f00", x: 10, y: 10 }, // lounge
    { identity: "b", name: "Bob", color: "#0f0", x: 250, y: 50 }, // eng
    { identity: "c", name: "Cara", color: "#00f", x: 500, y: 500 }, // nowhere
    { identity: "d", name: "Dan", color: "#ff0", x: 99, y: 99 }, // lounge edge
  ];

  it("buckets avatars by their containing zone", () => {
    const map = makeMap([lounge, eng]);
    const buckets = bucketByZone(map, avatars);
    expect(buckets.get("lounge")!.map((o) => o.identity)).toEqual(["a", "d"]);
    expect(buckets.get("eng")!.map((o) => o.identity)).toEqual(["b"]);
  });

  it("returns an empty Map when there are no zones", () => {
    const map = makeMap([{ type: "wall", x: 0, y: 0, width: 10, height: 10 }]);
    const buckets = bucketByZone(map, avatars);
    expect(buckets.size).toBe(0);
  });

  it("each avatar lands in at most one bucket even on overlap", () => {
    const map = makeMap([lounge, overlap]);
    const buckets = bucketByZone(map, [
      { identity: "x", name: "X", color: "#fff", x: 75, y: 75 },
    ]);
    expect(buckets.get("overlap")!.length).toBe(1);
    expect(buckets.get("lounge")!.length).toBe(0);
  });
});
