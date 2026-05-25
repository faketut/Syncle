import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { buildApp } from "../src/server.js";
import type { FastifyInstance } from "fastify";

// Dedicated app instance with a tight per-IP throttle so we can observe 429
// without leaking state into the main routes.test.ts suite.
const ENV = {
  LIVEKIT_URL: "ws://test",
  LIVEKIT_API_KEY: "devkey",
  LIVEKIT_API_SECRET: "secret-secret-secret-secret",
  DB_PATH: ":memory:",
  TOKEN_TTL_SECONDS: "60",
  LOG_LEVEL: "silent",
  PORT: "8787",
  SESSION_RATE_LIMIT_MAX: "3",
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

describe("POST /v1/sessions rate limit", () => {
  it("returns 429 once the per-IP cap is exceeded", async () => {
    const payload = { deviceId: "device-ratelimit-1", nickname: "RL", room: "ratelimit" };
    const max = 3;
    for (let i = 0; i < max; i++) {
      const ok = await app.inject({ method: "POST", url: "/v1/sessions", payload });
      expect(ok.statusCode, `request ${i + 1} should succeed`).toBe(200);
    }
    const over = await app.inject({ method: "POST", url: "/v1/sessions", payload });
    expect(over.statusCode).toBe(429);
  });

  it("does not throttle /healthz", async () => {
    for (let i = 0; i < 10; i++) {
      const r = await app.inject({ method: "GET", url: "/healthz" });
      expect(r.statusCode).toBe(200);
    }
  });
});
