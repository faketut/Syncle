import { describe, it, expect } from "vitest";
import {
  gridLayout,
  overflowCount,
  MAX_VISIBLE_TILES,
} from "../meetingLayout";

describe("gridLayout", () => {
  it.each([
    [0, 1, 1],
    [1, 1, 1],
    [2, 2, 1],
    [3, 2, 2],
    [4, 2, 2],
    [5, 3, 2],
    [6, 3, 2],
    [7, 3, 3],
    [8, 3, 3],
    [9, 3, 3],
  ])("n=%i → %ix%i", (n, cols, rows) => {
    expect(gridLayout(n)).toEqual({ cols, rows });
  });

  it("clamps n above the cap", () => {
    expect(gridLayout(20)).toEqual({ cols: 3, rows: 3 });
  });

  it("clamps negative input", () => {
    expect(gridLayout(-5)).toEqual({ cols: 1, rows: 1 });
  });

  it("rounds down fractional input", () => {
    expect(gridLayout(2.9)).toEqual({ cols: 2, rows: 1 });
  });
});

describe("overflowCount", () => {
  it("is zero when within cap", () => {
    expect(overflowCount(MAX_VISIBLE_TILES)).toBe(0);
    expect(overflowCount(0)).toBe(0);
  });

  it("counts what was hidden", () => {
    expect(overflowCount(MAX_VISIBLE_TILES + 3)).toBe(3);
  });
});
