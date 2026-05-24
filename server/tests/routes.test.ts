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
      payload: { deviceId: "device-abc-123", nickname: "Alice", room: "r1" },
    });
    expect(r1.statusCode).toBe(200);
    const j1 = r1.json();
    expect(j1.token).toMatch(/\..+\./);
    expect(j1.serverUrl).toBe("ws://test");
    expect(j1.userId).toMatch(/^[0-9a-f-]{36}$/);

    const r2 = await app.inject({
      method: "POST",
      url: "/v1/sessions",
      payload: { deviceId: "device-abc-123", nickname: "Alice2", room: "r1" },
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
});

describe("snapshot + state", () => {
  it("rejects state without bearer", async () => {
    const r = await app.inject({
      method: "POST",
      url: "/v1/rooms/r1/state",
      payload: { userId: "x", x: 0, y: 0 },
    });
    expect(r.statusCode).toBe(401);
  });

  it("accepts state with valid bearer and surfaces in snapshot", async () => {
    const session = await app.inject({
      method: "POST",
      url: "/v1/sessions",
      payload: { deviceId: "device-snap-1", nickname: "Bob", room: "r2" },
    });
    const { userId, token } = session.json();

    const state = await app.inject({
      method: "POST",
      url: "/v1/rooms/r2/state",
      headers: { authorization: `Bearer ${token}` },
      payload: { userId, tableId: "t1", x: 300, y: 400 },
    });
    expect(state.statusCode).toBe(200);

    const snap = await app.inject({ method: "GET", url: "/v1/rooms/r2/snapshot" });
    const peers = snap.json().peers;
    expect(peers).toHaveLength(1);
    expect(peers[0]).toMatchObject({ userId, nickname: "Bob", tableId: "t1", x: 300, y: 400 });
  });

  it("rejects state when identity mismatches token", async () => {
    const session = await app.inject({
      method: "POST",
      url: "/v1/sessions",
      payload: { deviceId: "device-mismatch", nickname: "C", room: "r3" },
    });
    const { token } = session.json();
    const r = await app.inject({
      method: "POST",
      url: "/v1/rooms/r3/state",
      headers: { authorization: `Bearer ${token}` },
      payload: { userId: "someone-else-1234", x: 0, y: 0 },
    });
    expect(r.statusCode).toBe(403);
  });

  it("rejects state when room mismatches token", async () => {
    const session = await app.inject({
      method: "POST",
      url: "/v1/sessions",
      payload: { deviceId: "device-room", nickname: "D", room: "r4" },
    });
    const { userId, token } = session.json();
    const r = await app.inject({
      method: "POST",
      url: "/v1/rooms/r4-wrong/state",
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
