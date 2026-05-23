import { jwtVerify } from "jose";

export interface JoinTokenPayload {
  sub: string; // user id (identity)
  video: { room?: string; roomJoin?: boolean };
  exp: number;
}

export async function verifyJoinToken(
  token: string,
  apiSecret: string,
): Promise<JoinTokenPayload> {
  const key = new TextEncoder().encode(apiSecret);
  const { payload } = await jwtVerify(token, key, { algorithms: ["HS256"] });
  const sub = payload.sub;
  const video = (payload as Record<string, unknown>).video as
    | JoinTokenPayload["video"]
    | undefined;
  if (typeof sub !== "string" || !video || typeof video !== "object") {
    throw new Error("token missing sub or video grants");
  }
  return { sub, video, exp: payload.exp ?? 0 };
}

export function extractBearer(authHeader: string | undefined): string | null {
  if (!authHeader) return null;
  const m = /^Bearer\s+(.+)$/i.exec(authHeader);
  return m ? m[1].trim() : null;
}
