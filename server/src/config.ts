import { z } from "zod";

const Schema = z.object({
  LIVEKIT_URL: z.string().min(1),
  LIVEKIT_API_KEY: z.string().min(1),
  LIVEKIT_API_SECRET: z.string().min(1),
  PORT: z.coerce.number().int().positive().default(8787),
  DB_PATH: z.string().min(1).default("./data/syncle.db"),
  TOKEN_TTL_SECONDS: z.coerce.number().int().positive().default(3600),
  LOG_LEVEL: z.string().default("info"),
  // Per-IP rate limit for POST /v1/sessions. Override in production.
  SESSION_RATE_LIMIT_MAX: z.coerce.number().int().positive().default(30),
  SESSION_RATE_LIMIT_WINDOW_MS: z.coerce.number().int().positive().default(60_000),
});

export type Config = z.infer<typeof Schema>;

export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
  return Schema.parse(env);
}
