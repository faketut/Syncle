import { describe, expect, it } from "vitest";
import {
  POSITION_PACKET_SIZE,
  decodePosition,
  encodePosition,
  nextSeq,
  resetSeq,
} from "../positionPacket";

describe("position packet (wire contract with Android)", () => {
  it("encodes to exactly 17 bytes with the correct type tag", () => {
    const pkt = encodePosition(1.5, -2.25, 7n);
    expect(pkt.length).toBe(POSITION_PACKET_SIZE);
    expect(pkt[0]).toBe(1); // type tag = position
  });

  it("round-trips x/y/seq through encode → decode", () => {
    const original = { x: 123.5, y: -456.75, seq: 9999n };
    const decoded = decodePosition(encodePosition(original.x, original.y, original.seq));
    expect(decoded).not.toBeNull();
    expect(decoded!.x).toBeCloseTo(original.x, 3);
    expect(decoded!.y).toBeCloseTo(original.y, 3);
    expect(decoded!.seq).toBe(original.seq);
  });

  it("returns null for too-short payloads", () => {
    expect(decodePosition(new Uint8Array(5))).toBeNull();
  });

  it("returns null for an unknown type tag", () => {
    const bad = new Uint8Array(POSITION_PACKET_SIZE);
    bad[0] = 99;
    expect(decodePosition(bad)).toBeNull();
  });

  it("uses little-endian byte order (matches Android)", () => {
    // x=1.0 in IEEE 754 little-endian = 00 00 80 3F
    const pkt = encodePosition(1.0, 0.0, 0n);
    expect(pkt[1]).toBe(0x00);
    expect(pkt[2]).toBe(0x00);
    expect(pkt[3]).toBe(0x80);
    expect(pkt[4]).toBe(0x3f);
  });
});

describe("nextSeq", () => {
  it("starts at 0 after reset and increments monotonically", () => {
    resetSeq();
    expect(nextSeq()).toBe(0n);
    expect(nextSeq()).toBe(1n);
    expect(nextSeq()).toBe(2n);
  });
});
