import { describe, expect, it } from "vitest";
import {
  emptyMap,
  toRawConfig,
  type AuthoredMap,
} from "../mapAuthoring";

describe("toRawConfig", () => {
  it("auto-assigns ids/labels to tables that lack them", () => {
    const m: AuthoredMap = {
      ...emptyMap(),
      objects: [
        { uid: "a", type: "wall", x: 0, y: 0, width: 100, height: 10 },
        { uid: "b", type: "table", x: 0, y: 0, width: 60, height: 40 },
        { uid: "c", type: "table", x: 80, y: 0, width: 60, height: 40 },
      ],
    };
    const raw = toRawConfig(m);
    const tables = raw.objects!.filter((o) => o.type === "table");
    expect(tables).toHaveLength(2);
    expect(tables[0].id).toBe("table-1");
    expect(tables[0].label).toBe("Table 1");
    expect(tables[1].id).toBe("table-2");
    expect(tables[1].label).toBe("Table 2");
  });

  it("preserves user-set ids on tables", () => {
    const m: AuthoredMap = {
      ...emptyMap(),
      objects: [
        {
          uid: "x",
          type: "table",
          id: "lounge",
          label: "The Lounge",
          x: 0,
          y: 0,
          width: 60,
          height: 40,
        },
      ],
    };
    const raw = toRawConfig(m);
    expect(raw.objects![0].id).toBe("lounge");
    expect(raw.objects![0].label).toBe("The Lounge");
  });

  it("strips the editor-only `uid` from output", () => {
    const m: AuthoredMap = {
      ...emptyMap(),
      objects: [
        { uid: "should-be-stripped", type: "wall", x: 0, y: 0, width: 10, height: 10 },
      ],
    };
    const raw = toRawConfig(m);
    // 'uid' should not appear on serialized objects.
    expect(raw.objects![0]).not.toHaveProperty("uid");
  });

  it("propagates width/height/backgroundColor/name", () => {
    const m: AuthoredMap = {
      name: "Test Room",
      width: 1234,
      height: 567,
      backgroundColor: "#abcdef",
      objects: [],
    };
    const raw = toRawConfig(m);
    expect(raw.map_name).toBe("Test Room");
    expect(raw.width).toBe(1234);
    expect(raw.height).toBe(567);
    expect(raw.background_color).toBe("#abcdef");
  });
});
