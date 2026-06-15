// Chat packet (web-only for now; Android will need a mirror if cross-platform
// chat is desired). Type tag chosen to not collide with the position packet's
// tag (1).
//
// Layout:
//   byte 0:    type tag (2 = chat)
//   bytes 1..: UTF-8 encoded JSON: ChatPayload
//
// JSON (instead of packed binary) because chat is low-rate, variable-length,
// and easier to extend (e.g. reactions, attachments) without breaking the
// wire format.
//
// Scopes (receivers enforce filters; wire fan-out is always broadcast):
//   global  — visible to everyone in the room
//   table   — only peers whose tableId matches payload.tableId
//   zone    — only peers currently standing in payload.zoneKey
//   dm      — only the peer whose identity matches payload.to
//   channel — only peers subscribed to payload.channelId

export const PACKET_TYPE_CHAT = 2 as const;

export type ChatScope = "global" | "table" | "zone" | "dm" | "channel";

export interface ChatPayload {
  scope: ChatScope;
  /** Required when scope === "table"; the sender's table_id at send time. */
  tableId?: string;
  /** Required when scope === "zone"; the zone key (see domain/zones.ts). */
  zoneKey?: string;
  /** Required when scope === "channel"; the server-issued channel id. */
  channelId?: string;
  /** Required when scope === "dm"; the recipient LiveKit identity. */
  to?: string;
  text: string;
  /** Sender wall-clock; receivers should treat as advisory. */
  ts: number;
}

const encoder = new TextEncoder();
const decoder = new TextDecoder();

export function encodeChat(payload: ChatPayload): Uint8Array {
  const json = JSON.stringify(payload);
  const body = encoder.encode(json);
  const out = new Uint8Array(1 + body.byteLength);
  out[0] = PACKET_TYPE_CHAT;
  out.set(body, 1);
  return out;
}

export function decodeChat(data: Uint8Array): ChatPayload | null {
  if (data.length < 2) return null;
  if (data[0] !== PACKET_TYPE_CHAT) return null;
  try {
    const body = decoder.decode(data.subarray(1));
    const parsed = JSON.parse(body) as Partial<ChatPayload>;
    if (!isChatScope(parsed.scope)) return null;
    if (typeof parsed.text !== "string" || parsed.text.length === 0) return null;
    if (typeof parsed.ts !== "number") return null;
    if (parsed.scope === "table" && typeof parsed.tableId !== "string") return null;
    if (parsed.scope === "zone" && typeof parsed.zoneKey !== "string") return null;
    if (parsed.scope === "channel" && typeof parsed.channelId !== "string") return null;
    if (parsed.scope === "dm" && typeof parsed.to !== "string") return null;
    return {
      scope: parsed.scope,
      tableId: parsed.tableId,
      zoneKey: parsed.zoneKey,
      channelId: parsed.channelId,
      to: parsed.to,
      text: parsed.text,
      ts: parsed.ts,
    };
  } catch {
    return null;
  }
}

function isChatScope(s: unknown): s is ChatScope {
  return (
    s === "global" ||
    s === "table" ||
    s === "zone" ||
    s === "dm" ||
    s === "channel"
  );
}

export const CHAT_TEXT_MAX_LEN = 500 as const;

export function sanitizeChatText(input: string): string {
  // Collapse newlines to spaces, trim, then clamp.
  return input.replace(/[\r\n]+/g, " ").trim().slice(0, CHAT_TEXT_MAX_LEN);
}

/** Stable conversation key for a DM pair, regardless of which side asks. */
export function dmConversationKey(a: string, b: string): string {
  return a < b ? `${a}|${b}` : `${b}|${a}`;
}

/** True when `text` contains an @-mention of `nickname` (case-insensitive,
 *  word-bounded). Empty nickname returns false. */
export function mentionsNickname(text: string, nickname: string): boolean {
  if (!nickname) return false;
  const escaped = nickname.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const re = new RegExp(`(^|[^A-Za-z0-9_])@${escaped}(?![A-Za-z0-9_])`, "i");
  return re.test(text);
}
