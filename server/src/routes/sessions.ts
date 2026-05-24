import type { FastifyInstance } from "fastify";
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
  room: z.string().min(1).max(64),
});

export interface SessionsDeps {
  db: Db;
  signer: TokenSigner;
  livekitUrl: string;
}

export function registerSessionRoutes(
  app: FastifyInstance,
  deps: SessionsDeps,
): void {
  app.post("/v1/sessions", async (req, reply) => {
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
}
