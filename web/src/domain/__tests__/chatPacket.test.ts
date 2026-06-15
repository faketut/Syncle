import { describe, it, expect } from "vitest";
import {
  encodeChat,
  decodeChat,
  PACKET_TYPE_CHAT,
  CHAT_TEXT_MAX_LEN,
  sanitizeChatText,
  mentionsNickname,
  dmConversationKey,
} from "../chatPacket";

describe("chat packet", () => {
  it("round-trips a global message", () => {
    const payload = { scope: "global" as const, text: "hello", ts: 12345 };
    const enc = encodeChat(payload);
    expect(enc[0]).toBe(PACKET_TYPE_CHAT);
    const dec = decodeChat(enc);
    expect(dec).toEqual({ scope: "global", text: "hello", ts: 12345, tableId: undefined });
  });

  it("round-trips a table-scoped message with tableId", () => {
    const payload = {
      scope: "table" as const,
      tableId: "table-eng",
      text: "hi team",
      ts: 1,
    };
    expect(decodeChat(encodeChat(payload))).toEqual(payload);
  });

  it("rejects packets with the wrong tag", () => {
    const bad = new Uint8Array([1, 0x7b, 0x7d]); // tag 1, body "{}"
    expect(decodeChat(bad)).toBeNull();
  });

  it("rejects too-short packets", () => {
    expect(decodeChat(new Uint8Array([PACKET_TYPE_CHAT]))).toBeNull();
  });

  it("rejects invalid JSON body", () => {
    const bad = new Uint8Array([PACKET_TYPE_CHAT, 0x7b]); // "{" — not closed
    expect(decodeChat(bad)).toBeNull();
  });

  it("rejects unknown scope", () => {
    const bad = encodeChat({
      // @ts-expect-error invalid scope on purpose
      scope: "world",
      text: "hi",
      ts: 0,
    });
    expect(decodeChat(bad)).toBeNull();
  });

  it("rejects empty text", () => {
    const bad = encodeChat({ scope: "global", text: "", ts: 0 });
    expect(decodeChat(bad)).toBeNull();
  });

  it("rejects table scope without tableId", () => {
    // Construct manually because TypeScript would reject the missing field.
    const body = JSON.stringify({ scope: "table", text: "x", ts: 0 });
    const enc = new Uint8Array(1 + body.length);
    enc[0] = PACKET_TYPE_CHAT;
    enc.set(new TextEncoder().encode(body), 1);
    expect(decodeChat(enc)).toBeNull();
  });

  describe("sanitizeChatText", () => {
    it("trims whitespace", () => {
      expect(sanitizeChatText("  hello  ")).toBe("hello");
    });

    it("collapses newlines to spaces", () => {
      expect(sanitizeChatText("a\nb\r\nc")).toBe("a b c");
    });

    it("clamps to max length", () => {
      const long = "x".repeat(CHAT_TEXT_MAX_LEN + 50);
      expect(sanitizeChatText(long).length).toBe(CHAT_TEXT_MAX_LEN);
    });
  });

  describe("M3 scopes", () => {
    it("round-trips a zone message", () => {
      const p = {
        scope: "zone" as const,
        zoneKey: "lounge",
        text: "anyone here?",
        ts: 99,
      };
      expect(decodeChat(encodeChat(p))).toEqual(p);
    });

    it("round-trips a channel message", () => {
      const p = {
        scope: "channel" as const,
        channelId: "chan-1",
        text: "ship it",
        ts: 1,
      };
      expect(decodeChat(encodeChat(p))).toEqual(p);
    });

    it("round-trips a DM", () => {
      const p = {
        scope: "dm" as const,
        to: "user-2",
        text: "hey",
        ts: 2,
      };
      expect(decodeChat(encodeChat(p))).toEqual(p);
    });

    it("rejects zone scope without zoneKey", () => {
      const body = JSON.stringify({ scope: "zone", text: "x", ts: 0 });
      const enc = new Uint8Array(1 + body.length);
      enc[0] = PACKET_TYPE_CHAT;
      enc.set(new TextEncoder().encode(body), 1);
      expect(decodeChat(enc)).toBeNull();
    });

    it("rejects channel scope without channelId", () => {
      const body = JSON.stringify({ scope: "channel", text: "x", ts: 0 });
      const enc = new Uint8Array(1 + body.length);
      enc[0] = PACKET_TYPE_CHAT;
      enc.set(new TextEncoder().encode(body), 1);
      expect(decodeChat(enc)).toBeNull();
    });

    it("rejects dm scope without to", () => {
      const body = JSON.stringify({ scope: "dm", text: "x", ts: 0 });
      const enc = new Uint8Array(1 + body.length);
      enc[0] = PACKET_TYPE_CHAT;
      enc.set(new TextEncoder().encode(body), 1);
      expect(decodeChat(enc)).toBeNull();
    });
  });

  describe("dmConversationKey", () => {
    it("is symmetric across argument order", () => {
      expect(dmConversationKey("a", "b")).toBe(dmConversationKey("b", "a"));
    });

    it("differs for different pairs", () => {
      expect(dmConversationKey("a", "b")).not.toBe(dmConversationKey("a", "c"));
    });
  });

  describe("mentionsNickname", () => {
    it("matches @nickname with surrounding whitespace", () => {
      expect(mentionsNickname("hey @alice can you check?", "alice")).toBe(true);
    });

    it("matches at start of text", () => {
      expect(mentionsNickname("@alice ping", "alice")).toBe(true);
    });

    it("is case-insensitive", () => {
      expect(mentionsNickname("yo @Alice!", "alice")).toBe(true);
    });

    it("requires the @-prefix", () => {
      expect(mentionsNickname("alice did it", "alice")).toBe(false);
    });

    it("does not match a prefix-only substring", () => {
      expect(mentionsNickname("hi @alicebot", "alice")).toBe(false);
    });

    it("returns false on empty nickname", () => {
      expect(mentionsNickname("hi everyone", "")).toBe(false);
    });
  });
});
