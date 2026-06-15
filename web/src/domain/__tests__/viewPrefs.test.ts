import { describe, it, expect } from "vitest";
import {
  nextTheme,
  parseMiniMode,
  parseTheme,
  readMiniMode,
  readTheme,
  writeMiniMode,
  writeTheme,
  type PrefStorage,
} from "../viewPrefs";

function makeStorage(initial: Record<string, string> = {}): PrefStorage & {
  store: Record<string, string>;
} {
  const store: Record<string, string> = { ...initial };
  return {
    store,
    getItem: (k) => (k in store ? store[k] : null),
    setItem: (k, v) => {
      store[k] = v;
    },
  };
}

describe("viewPrefs.parseTheme", () => {
  it.each([
    ["dark", "dark"],
    ["light", "light"],
    [null, "dark"],
    ["", "dark"],
    ["nonsense", "dark"],
  ] as const)("parseTheme(%j) -> %s", (raw, expected) => {
    expect(parseTheme(raw)).toBe(expected);
  });
});

describe("viewPrefs.parseMiniMode", () => {
  it.each([
    ["1", true],
    ["0", false],
    [null, false],
    ["true", false],
    ["", false],
  ] as const)("parseMiniMode(%j) -> %s", (raw, expected) => {
    expect(parseMiniMode(raw)).toBe(expected);
  });
});

describe("viewPrefs.nextTheme", () => {
  it("flips light to dark", () => {
    expect(nextTheme("light")).toBe("dark");
  });
  it("flips dark to light", () => {
    expect(nextTheme("dark")).toBe("light");
  });
});

describe("viewPrefs round trip", () => {
  it("persists theme across read/write", () => {
    const s = makeStorage();
    expect(readTheme(s)).toBe("dark");
    writeTheme(s, "light");
    expect(readTheme(s)).toBe("light");
    writeTheme(s, "dark");
    expect(readTheme(s)).toBe("dark");
  });

  it("persists mini mode across read/write", () => {
    const s = makeStorage();
    expect(readMiniMode(s)).toBe(false);
    writeMiniMode(s, true);
    expect(readMiniMode(s)).toBe(true);
    writeMiniMode(s, false);
    expect(readMiniMode(s)).toBe(false);
  });

  it("swallows storage errors silently", () => {
    const throwing: PrefStorage = {
      getItem: () => {
        throw new Error("denied");
      },
      setItem: () => {
        throw new Error("quota");
      },
    };
    expect(readTheme(throwing)).toBe("dark");
    expect(readMiniMode(throwing)).toBe(false);
    expect(() => writeTheme(throwing, "dark")).not.toThrow();
    expect(() => writeMiniMode(throwing, true)).not.toThrow();
  });
});
