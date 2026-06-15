// Wave / quick-reaction packet. Type tag = 3 (1=position, 2=chat, 3=reaction).
//
// Layout (2 bytes, fixed):
//   byte 0: type tag (3)
//   byte 1: reaction index (0..REACTIONS.length-1)
//
// Reactions are rare and the catalog is small + fixed, so a 1-byte index is
// far cheaper than the JSON shape used for chat. Receivers look up the glyph
// from REACTIONS at render time.
//
// Any new reaction must be APPENDED (never reordered/removed) so older clients
// can still decode by mapping unknown indices to the first entry.

export const PACKET_TYPE_REACTION = 3 as const;
export const REACTION_PACKET_SIZE = 2 as const;

/** Display catalog. Index is the wire value. Order is the wire contract — do
 *  not reorder. The label drives the accessible name for the reaction picker. */
export const REACTIONS: ReadonlyArray<{ glyph: string; label: string }> = [
  { glyph: "👋", label: "Wave" },
  { glyph: "👍", label: "Thumbs up" },
  { glyph: "❤️", label: "Heart" },
  { glyph: "🎉", label: "Celebrate" },
  { glyph: "😂", label: "Laugh" },
  { glyph: "❓", label: "Question" },
];

/** Default reaction sent when the user presses R without picking. */
export const DEFAULT_REACTION_INDEX = 0;

export interface ReactionPacket {
  index: number;
}

export function encodeReaction(index: number): Uint8Array {
  if (!Number.isInteger(index) || index < 0 || index >= REACTIONS.length) {
    throw new RangeError(`reaction index out of range: ${index}`);
  }
  return new Uint8Array([PACKET_TYPE_REACTION, index]);
}

export function decodeReaction(data: Uint8Array): ReactionPacket | null {
  if (data.length < REACTION_PACKET_SIZE) return null;
  if (data[0] !== PACKET_TYPE_REACTION) return null;
  const raw = data[1];
  // Forward-compat: clamp unknown indices to 0 (wave) so older clients still
  // render something instead of dropping the packet.
  const index = raw < REACTIONS.length ? raw : 0;
  return { index };
}
