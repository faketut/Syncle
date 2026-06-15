import { describe, expect, it } from "vitest";
import {
  decodeReaction,
  encodeReaction,
  PACKET_TYPE_REACTION,
  REACTION_PACKET_SIZE,
  REACTIONS,
} from "../reactionPacket";

describe("reactionPacket", () => {
  it("encodes a 2-byte packet with the correct tag and index", () => {
    const buf = encodeReaction(2);
    expect(buf.length).toBe(REACTION_PACKET_SIZE);
    expect(buf[0]).toBe(PACKET_TYPE_REACTION);
    expect(buf[1]).toBe(2);
  });

  it("round-trips every catalog entry", () => {
    for (let i = 0; i < REACTIONS.length; i++) {
      const decoded = decodeReaction(encodeReaction(i));
      expect(decoded).toEqual({ index: i });
    }
  });

  it("rejects packets with the wrong type tag", () => {
    expect(decodeReaction(new Uint8Array([1, 0]))).toBeNull();
    expect(decodeReaction(new Uint8Array([2, 0]))).toBeNull();
  });

  it("rejects truncated packets", () => {
    expect(decodeReaction(new Uint8Array([]))).toBeNull();
    expect(decodeReaction(new Uint8Array([PACKET_TYPE_REACTION]))).toBeNull();
  });

  it("clamps unknown indices to 0 (forward-compat)", () => {
    const buf = new Uint8Array([PACKET_TYPE_REACTION, 255]);
    expect(decodeReaction(buf)).toEqual({ index: 0 });
  });

  it("rejects encode for out-of-range indices", () => {
    expect(() => encodeReaction(-1)).toThrow(RangeError);
    expect(() => encodeReaction(REACTIONS.length)).toThrow(RangeError);
    expect(() => encodeReaction(1.5)).toThrow(RangeError);
  });
});
