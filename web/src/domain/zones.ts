// Zone occupancy: which avatars are currently inside which named `zone`
// MapObject. Zones are non-solid rects authored on the map; we treat an
// avatar as "inside" if its center point falls inside the zone's AABB.
//
// Used by the WhosWherePanel sidebar and the in-world zone label chip.

import type { MapConfig, MapObject } from "../types/mapConfig";

export interface Zone {
  /** Editor uid or generated id, used as React key. */
  key: string;
  label: string;
  rect: { x: number; y: number; width: number; height: number };
}

export interface ZoneOccupant {
  identity: string;
  name: string;
  color: string;
}

export function zonesOf(map: MapConfig): Zone[] {
  const out: Zone[] = [];
  for (let i = 0; i < map.objects.length; i++) {
    const o = map.objects[i];
    if (o.type !== "zone") continue;
    out.push({
      key: o.id ?? `zone-${i}`,
      label: o.label && o.label.length > 0 ? o.label : `Zone ${i + 1}`,
      rect: { x: o.x, y: o.y, width: o.width, height: o.height },
    });
  }
  return out;
}

function pointInRect(
  px: number,
  py: number,
  rect: { x: number; y: number; width: number; height: number },
): boolean {
  return (
    px >= rect.x &&
    px <= rect.x + rect.width &&
    py >= rect.y &&
    py <= rect.y + rect.height
  );
}

/** Locate which zone the given point sits in. Returns the first match (zones
 *  rendered later in the objects array win on overlap, matching draw order). */
export function findZoneAt(
  x: number,
  y: number,
  map: MapConfig,
): Zone | null {
  const zones = zonesOf(map);
  for (let i = zones.length - 1; i >= 0; i--) {
    if (pointInRect(x, y, zones[i].rect)) return zones[i];
  }
  return null;
}

export interface AvatarPoint {
  identity: string;
  name: string;
  color: string;
  x: number;
  y: number;
}

/** Bucket avatars by zone. Avatars not inside any zone are dropped; if you
 *  need the unzoned count, compute `total - sum(map.values().length)`. */
export function bucketByZone(
  map: MapConfig,
  avatars: ReadonlyArray<AvatarPoint>,
): Map<string, ZoneOccupant[]> {
  const zones = zonesOf(map);
  const out = new Map<string, ZoneOccupant[]>();
  for (const z of zones) out.set(z.key, []);
  if (zones.length === 0) return out;
  for (const a of avatars) {
    // Match draw-order (last-wins) so overlapping zones don't double-count.
    for (let i = zones.length - 1; i >= 0; i--) {
      const z = zones[i];
      if (pointInRect(a.x, a.y, z.rect)) {
        out.get(z.key)!.push({
          identity: a.identity,
          name: a.name,
          color: a.color,
        });
        break;
      }
    }
  }
  return out;
}

/** Convenience: convert a zone MapObject back into a Zone (for the editor
 *  preview chip). */
export function zoneFromObject(obj: MapObject, key: string): Zone {
  return {
    key,
    label: obj.label && obj.label.length > 0 ? obj.label : "Zone",
    rect: { x: obj.x, y: obj.y, width: obj.width, height: obj.height },
  };
}
