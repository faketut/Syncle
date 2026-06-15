import type { FastifyInstance } from "fastify";
import { z } from "zod";
import type { Db } from "../db.js";
import { listChannels, upsertChannel } from "../db.js";
import { extractBearer, verifyJoinToken } from "../auth.js";

const CreateBody = z.object({
  // Channel name; lowercase, digits, dash, underscore. 1–32 chars.
  // Displayed in UI with a `#` prefix; the storage value never carries it.
  name: z
    .string()
    .regex(/^[a-z0-9_-]{1,32}$/, "name must match ^[a-z0-9_-]{1,32}$"),
});

export interface ChannelDeps {
  db: Db;
  apiSecret: string;
}

export function registerChannelRoutes(
  app: FastifyInstance,
  deps: ChannelDeps,
): void {
  app.get<{ Params: { room: string } }>(
    "/v1/rooms/:room/channels",
    async (req, reply) => {
      const { room } = req.params;
      if (!room || room.length > 64) {
        return reply.code(400).send({ error: "invalid_room" });
      }
      const rows = listChannels(deps.db, room);
      return reply.send({
        room,
        channels: rows.map((r) => ({ id: r.id, name: r.name })),
      });
    },
  );

  app.post<{ Params: { room: string } }>(
    "/v1/rooms/:room/channels",
    async (req, reply) => {
      const { room } = req.params;
      // JWT-gated: only joined members of the room can mint channels. This
      // prevents anonymous callers from spamming the room with channels.
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

      const parsed = CreateBody.safeParse(req.body);
      if (!parsed.success) {
        return reply
          .code(400)
          .send({ error: "invalid_body", details: parsed.error.issues });
      }
      const row = upsertChannel(deps.db, room, parsed.data.name);
      return reply.send({ channel: { id: row.id, name: row.name } });
    },
  );
}
