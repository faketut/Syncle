import { describe, expect, it } from "vitest";
import {
  MAX_ATTEMPTS,
  MAX_MS,
  delayMsForAttempt,
  shouldGiveUp,
} from "../reconnectPolicy";

describe("delayMsForAttempt", () => {
  it("grows roughly exponentially for early attempts (random=0.5 → no jitter)", () => {
    // random=0.5 produces a zero jitter term, so we hit the exact base.
    expect(delayMsForAttempt(1, 0.5)).toBe(1_000);
    expect(delayMsForAttempt(2, 0.5)).toBe(2_000);
    expect(delayMsForAttempt(3, 0.5)).toBe(4_000);
    expect(delayMsForAttempt(4, 0.5)).toBe(8_000);
    expect(delayMsForAttempt(5, 0.5)).toBe(16_000);
  });

  it("caps at MAX_MS regardless of attempt", () => {
    expect(delayMsForAttempt(6, 0.5)).toBe(MAX_MS);
    expect(delayMsForAttempt(20, 0.5)).toBe(MAX_MS);
  });

  it("jitter stays within ±25% of the base", () => {
    // random=0   → -25% → 6000ms for attempt 4 (base=8000ms).
    expect(delayMsForAttempt(4, 0)).toBe(6_000);
    // random→1 → +25% → 10000ms (just under).
    const high = delayMsForAttempt(4, 0.9999);
    expect(high).toBeGreaterThanOrEqual(9_990);
    expect(high).toBeLessThanOrEqual(10_000);
    // Random samples must stay inside the band.
    for (let i = 0; i < 50; i++) {
      const d = delayMsForAttempt(4);
      expect(d).toBeGreaterThanOrEqual(6_000);
      expect(d).toBeLessThanOrEqual(10_000);
    }
  });

  it("never returns a negative delay", () => {
    for (let i = 0; i < 100; i++) {
      expect(delayMsForAttempt(1)).toBeGreaterThanOrEqual(0);
    }
  });

  it("rejects attempts below 1", () => {
    expect(() => delayMsForAttempt(0)).toThrow();
    expect(() => delayMsForAttempt(-1)).toThrow();
    expect(() => delayMsForAttempt(1.5)).toThrow();
  });
});

describe("shouldGiveUp", () => {
  it("respects MAX_ATTEMPTS", () => {
    expect(shouldGiveUp(1)).toBe(false);
    expect(shouldGiveUp(MAX_ATTEMPTS)).toBe(false);
    expect(shouldGiveUp(MAX_ATTEMPTS + 1)).toBe(true);
  });
});
