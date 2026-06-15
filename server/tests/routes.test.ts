import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { buildApp } from "../src/server.js";
import type { FastifyInstance } from "fastify";

const ENV = {
  LIVEKIT_URL: "ws://test",
  LIVEKIT_API_KEY: "devkey",
  LIVEKIT_API_SECRET: "secret-secret-secret-secret",
  DB_PATH: ":memory:",
  TOKEN_TTL_SECONDS: "60",
  LOG_LEVEL: "silent",
  PORT: "8787",
  // Existing tests share an app; keep the limit out of the way.
  SESSION_RATE_LIMIT_MAX: "10000",
  SESSION_RATE_LIMIT_WINDOW_MS: "60000",
} as unknown as NodeJS.ProcessEnv;

let app: FastifyInstance;

beforeAll(async () => {
  ({ app } = await buildApp(ENV));
  await app.ready();
});
afterAll(async () => {
  await app.close();
});

describe("POST /v1/sessions", () => {
  it("returns token + persists user, idempotent on deviceId", async () => {
    const r1 = await app.inject({
      method: "POST",
      url: "/v1/sessions",
      payload: { deviceId: "device-abc-123", nickname: "Alice", room: "room1" },
    });
    expect(r1.statusCode).toBe(200);
    const j1 = r1.json();
    expect(j1.token).toMatch(/\..+\./);
    expect(j1.serverUrl).toBe("ws://test");
    expect(j1.userId).toMatch(/^[0-9a-f-]{36}$/);

    const r2 = await app.inject({
      method: "POST",
      url: "/v1/sessions",
      payload: { deviceId: "device-abc-123", nickname: "Alice2", room: "room1" },
    });
    const j2 = r2.json();
    expect(j2.userId).toBe(j1.userId);
    expect(j2.nickname).toBe("Alice2");
  });

  it("rejects invalid body", async () => {
    const r = await app.inject({
      method: "POST",
      url: "/v1/sessions",
      payload: { deviceId: "x", nickname: "", room: "" },
    });
    expect(r.statusCode).toBe(400);
  });

  // #40: room name must satisfy ^[a-z0-9-]{3,64}$ so it round-trips into the
  // JWT video.room claim without surprises.
  it.each([
    ["ab", "too short"],
    ["UPPER", "uppercase letters not allowed"],
    ["bad room", "spaces not allowed"],
    ["bad/slash", "slash not allowed"],
    ["a".repeat(65), "longer than 64 chars"],
  ])("rejects invalid room %p (%s)", async (room) => {
    const r = await app.inject({
      method: "POST",
      url: "/v1/sessions",
      payload: { deviceId: "device-room-validation-1", nickname: "X", room },
    });
    expect(r.statusCode).toBe(400);
  });

  it("accepts well-formed custom room names", async () => {
    const r = await app.inject({
      method: "POST",
      url: "/v1/sessions",
      payload: { deviceId: "device-room-ok-1", nickname: "X", room: "team-alpha-42" },
    });
    expect(r.statusCode).toBe(200);
  });
});

describe("snapshot + state", () => {
  it("rejects state without bearer", async () => {
    const r = await app.inject({
      method: "POST",
      url: "/v1/rooms/room1/state",
      payload: { userId: "x", x: 0, y: 0 },
    });
    expect(r.statusCode).toBe(401);
  });

  it("accepts state with valid bearer and surfaces in snapshot", async () => {
    const session = await app.inject({
      method: "POST",
      url: "/v1/sessions",
      payload: { deviceId: "device-snap-1", nickname: "Bob", room: "room2" },
    });
    const { userId, token } = session.json();

    const state = await app.inject({
      method: "POST",
      url: "/v1/rooms/room2/state",
      headers: { authorization: `Bearer ${token}` },
      payload: { userId, tableId: "t1", x: 300, y: 400 },
    });
    expect(state.statusCode).toBe(200);

    const snap = await app.inject({ method: "GET", url: "/v1/rooms/room2/snapshot" });
    const peers = snap.json().peers;
    expect(peers).toHaveLength(1);
    expect(peers[0]).toMatchObject({ userId, nickname: "Bob", tableId: "t1", x: 300, y: 400 });
  });

  it("rejects state when identity mismatches token", async () => {
    const session = await app.inject({
      method: "POST",
      url: "/v1/sessions",
      payload: { deviceId: "device-mismatch", nickname: "C", room: "room3" },
    });
    const { token } = session.json();
    const r = await app.inject({
      method: "POST",
      url: "/v1/rooms/room3/state",
      headers: { authorization: `Bearer ${token}` },
      payload: { userId: "someone-else-1234", x: 0, y: 0 },
    });
    expect(r.statusCode).toBe(403);
  });

  it("rejects state when room mismatches token", async () => {
    const session = await app.inject({
      method: "POST",
      url: "/v1/sessions",
      payload: { deviceId: "device-room", nickname: "D", room: "room4" },
    });
    const { userId, token } = session.json();
    const r = await app.inject({
      method: "POST",
      url: "/v1/rooms/room4-wrong/state",
      headers: { authorization: `Bearer ${token}` },
      payload: { userId, x: 0, y: 0 },
    });
    expect(r.statusCode).toBe(403);
  });
});

describe("GET /healthz", () => {
  it("returns ok", async () => {
    const r = await app.inject({ method: "GET", url: "/healthz" });
    expect(r.statusCode).toBe(200);
    expect(r.json().ok).toBe(true);
  });
});

describe("channels (M3)", () => {
  it("starts with no channels for a room", async () => {
    const r = await app.inject({ method: "GET", url: "/v1/rooms/room-chan-1/channels" });
    expect(r.statusCode).toBe(200);
    expect(r.json().channels).toEqual([]);
  });

  it("creates a channel with auth and returns it from list", async () => {
    const session = await app.inject({
      method: "POST",
      url: "/v1/sessions",
      payload: { deviceId: "device-chan-1", nickname: "Chan", room: "room-chan-2" },
    });
    const { token } = session.json();

    const created = await app.inject({
      method: "POST",
      url: "/v1/rooms/room-chan-2/channels",
      headers: { authorization: `Bearer ${token}` },
      payload: { name: "general" },
    });
    expect(created.statusCode).toBe(200);
    const { channel } = created.json();
    expect(channel.name).toBe("general");
    expect(channel.id).toMatch(/^[0-9a-f-]{36}$/);

    const list = await app.inject({ method: "GET", url: "/v1/rooms/room-chan-2/channels" });
    expect(list.json().channels).toHaveLength(1);
    expect(list.json().channels[0]).toMatchObject({ name: "general" });
  });

  it("is idempotent on (room, name)", async () => {
    const session = await app.inject({
      method: "POST",
      url: "/v1/sessions",
      payload: { deviceId: "device-chan-2", nickname: "Chan2", room: "room-chan-3" },
    });
    const { token } = session.json();

    const a = await app.inject({
      method: "POST",
      url: "/v1/rooms/room-chan-3/channels",
      headers: { authorization: `Bearer ${token}` },
      payload: { name: "design" },
    });
    const b = await app.inject({
      method: "POST",
      url: "/v1/rooms/room-chan-3/channels",
      headers: { authorization: `Bearer ${token}` },
      payload: { name: "design" },
    });
    expect(a.statusCode).toBe(200);
    expect(b.statusCode).toBe(200);
    expect(a.json().channel.id).toBe(b.json().channel.id);
  });

  it("rejects channel creation without bearer", async () => {
    const r = await app.inject({
      method: "POST",
      url: "/v1/rooms/room-chan-4/channels",
      payload: { name: "secret" },
    });
    expect(r.statusCode).toBe(401);
  });

  it("rejects channel creation when token's room does not match", async () => {
    const session = await app.inject({
      method: "POST",
      url: "/v1/sessions",
      payload: { deviceId: "device-chan-3", nickname: "Chan3", room: "room-chan-5" },
    });
    const { token } = session.json();
    const r = await app.inject({
      method: "POST",
      url: "/v1/rooms/room-chan-6/channels",
      headers: { authorization: `Bearer ${token}` },
      payload: { name: "wrong" },
    });
    expect(r.statusCode).toBe(403);
  });

  it("rejects invalid channel names", async () => {
    const session = await app.inject({
      method: "POST",
      url: "/v1/sessions",
      payload: { deviceId: "device-chan-4", nickname: "Chan4", room: "room-chan-7" },
    });
    const { token } = session.json();
    for (const name of ["", "UPPER", "has space", "a".repeat(33)]) {
      const r = await app.inject({
        method: "POST",
        url: "/v1/rooms/room-chan-7/channels",
        headers: { authorization: `Bearer ${token}` },
        payload: { name },
      });
      expect(r.statusCode).toBe(400);
    }
  });
});
