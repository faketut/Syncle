import { describe, it, expect } from "vitest";
import { openDb, upsertUser, upsertRoomState, getRoomSnapshot } from "../src/db.js";

describe("db", () => {
  it("upsertUser is idempotent on deviceId", () => {
    const db = openDb(":memory:");
    const a = upsertUser(db, "device-1", "Alice", "#fff", 1_000);
    const b = upsertUser(db, "device-1", "Alice2", "#000", 2_000);
    expect(a.id).toBe(b.id);
    expect(b.nickname).toBe("Alice2");
    expect(b.color).toBe("#000");
    expect(b.last_seen).toBe(2_000);
  });

  it("getRoomSnapshot filters by fresh window and joins user profile", () => {
    const db = openDb(":memory:");
    const u = upsertUser(db, "d1", "Bob", "#111", 0);
    upsertRoomState(db, "r1", u.id, "table-7", 10, 20, 1_000);
    const stale = upsertUser(db, "d2", "Old", "#222", 0);
    upsertRoomState(db, "r1", stale.id, null, 0, 0, 100);

    const snap = getRoomSnapshot(db, "r1", 5_000, 6_000);
    expect(snap).toHaveLength(1);
    expect(snap[0]).toMatchObject({
      userId: u.id,
      nickname: "Bob",
      tableId: "table-7",
      x: 10,
      y: 20,
    });
  });
});
