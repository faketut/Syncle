import type { FastifyInstance } from "fastify";
import rateLimit from "@fastify/rate-limit";
import { z } from "zod";
import type { Db } from "../db.js";
import { upsertUser } from "../db.js";
import type { TokenSigner } from "../livekit.js";

const Body = z.object({
  deviceId: z.string().min(8).max(128),
  nickname: z.string().min(1).max(40),
  color: z
    .string()
    .regex(/^#[0-9a-fA-F]{6}$/)
    .default("#4F8EF7"),
  // #40: keep the JWT `video.room` claim sane. Lowercase + digits + dash, 3-64.
  room: z.string().regex(/^[a-z0-9-]{3,64}$/, "room must match ^[a-z0-9-]{3,64}$"),
});

export interface SessionsDeps {
  db: Db;
  signer: TokenSigner;
  livekitUrl: string;
  rateLimit?: {
    max: number;
    timeWindowMs: number;
  };
}

export async function registerSessionRoutes(
  app: FastifyInstance,
  deps: SessionsDeps,
): Promise<void> {
  // Per-IP throttle so a single client can't burn through JWTs (each call
  // also performs a SQLite upsert + JWT sign). Scoped to this plugin via
  // encapsulation so other routes (e.g. /healthz, /v1/snapshot) are unaffected.
  await app.register(async (scope) => {
    await scope.register(rateLimit, {
      max: deps.rateLimit?.max ?? 30,
      timeWindow: deps.rateLimit?.timeWindowMs ?? 60_000,
    });

    scope.post("/v1/sessions", async (req, reply) => {
      const parsed = Body.safeParse(req.body);
      if (!parsed.success) {
        return reply.code(400).send({ error: "invalid_body", details: parsed.error.issues });
      }
      const { deviceId, nickname, color, room } = parsed.data;
      const user = upsertUser(deps.db, deviceId, nickname, color);
      const signed = await deps.signer.sign(user.id, room, user.nickname);
      return reply.send({
        userId: user.id,
        nickname: user.nickname,
        color: user.color,
        serverUrl: deps.livekitUrl,
        token: signed.token,
        expiresAt: signed.expiresAt,
      });
    });
  });
}
