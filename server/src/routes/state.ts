import type { FastifyInstance } from "fastify";
import { z } from "zod";
import type { Db } from "../db.js";
import { upsertRoomState } from "../db.js";
import { extractBearer, verifyJoinToken } from "../auth.js";

const Body = z.object({
  userId: z.string().min(8).max(64),
  tableId: z.string().min(1).max(64).nullable().optional(),
  x: z.number().finite(),
  y: z.number().finite(),
});

export interface StateDeps {
  db: Db;
  apiSecret: string;
}

export function registerStateRoutes(app: FastifyInstance, deps: StateDeps): void {
  app.post<{ Params: { room: string } }>(
    "/v1/rooms/:room/state",
    async (req, reply) => {
      const { room } = req.params;
      const token = extractBearer(req.headers.authorization);
      if (!token) return reply.code(401).send({ error: "missing_bearer" });

      let payload;
      try {
        payload = await verifyJoinToken(token, deps.apiSecret);
      } catch {
        return reply.code(401).send({ error: "invalid_token" });
      }
      if (payload.video.room && payload.video.room !== room) {
        return reply.code(403).send({ error: "room_mismatch" });
      }

      const parsed = Body.safeParse(req.body);
      if (!parsed.success) {
        return reply
          .code(400)
          .send({ error: "invalid_body", details: parsed.error.issues });
      }
      const { userId, tableId, x, y } = parsed.data;
      if (userId !== payload.sub) {
        return reply.code(403).send({ error: "identity_mismatch" });
      }

      upsertRoomState(deps.db, room, userId, tableId ?? null, x, y);
      return reply.send({ ok: true });
    },
  );
}
