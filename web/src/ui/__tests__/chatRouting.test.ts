import { describe, it, expect } from "vitest";
import {
  filterByTab,
  filterByScope,
  unreadByTab,
  collectDmPartners,
  type ChatTab,
} from "../ChatPanel";
import {
  LOCAL_CHAT_IDENTITY,
  type ChatMessage,
  type RemotePeer,
} from "../../state/syncleStore";

function msg(partial: Partial<ChatMessage> & { id: number; scope: ChatMessage["scope"] }): ChatMessage {
  return {
    fromIdentity: "peer-1",
    fromName: "Peer",
    fromColor: "#fff",
    text: "hi",
    ts: partial.id,
    ...partial,
  };
}

const NO_ZONE = null;
const NO_CHANNEL = null;
const NO_DM = null;

describe("filterByTab", () => {
  it("returns only global messages on the Global tab", () => {
    const messages = [
      msg({ id: 1, scope: "global" }),
      msg({ id: 2, scope: "table", tableId: "t1" }),
      msg({ id: 3, scope: "zone", zoneKey: "lounge" }),
    ];
    const out = filterByTab({
      messages,
      tab: "global",
      myZoneKey: "lounge",
      myIdentity: "me",
      channelId: NO_CHANNEL,
      dmTarget: NO_DM,
    });
    expect(out.map((m) => m.id)).toEqual([1]);
  });

  it("returns only matching-zone messages on the Zone tab", () => {
    const messages = [
      msg({ id: 1, scope: "zone", zoneKey: "lounge" }),
      msg({ id: 2, scope: "zone", zoneKey: "engineering" }),
      msg({ id: 3, scope: "global" }),
    ];
    expect(
      filterByTab({
        messages,
        tab: "zone",
        myZoneKey: "lounge",
        myIdentity: "me",
        channelId: NO_CHANNEL,
        dmTarget: NO_DM,
      }).map((m) => m.id),
    ).toEqual([1]);
  });

  it("returns nothing on Zone tab when not in a zone", () => {
    const messages = [msg({ id: 1, scope: "zone", zoneKey: "lounge" })];
    expect(
      filterByTab({
        messages,
        tab: "zone",
        myZoneKey: NO_ZONE,
        myIdentity: "me",
        channelId: NO_CHANNEL,
        dmTarget: NO_DM,
      }),
    ).toEqual([]);
  });

  it("returns only the selected channel's messages on Channels tab", () => {
    const messages = [
      msg({ id: 1, scope: "channel", channelId: "c1" }),
      msg({ id: 2, scope: "channel", channelId: "c2" }),
      msg({ id: 3, scope: "global" }),
    ];
    expect(
      filterByTab({
        messages,
        tab: "channels",
        myZoneKey: NO_ZONE,
        myIdentity: "me",
        channelId: "c1",
        dmTarget: NO_DM,
      }).map((m) => m.id),
    ).toEqual([1]);
  });

  it("returns both sides of a DM conversation on DMs tab", () => {
    const messages: ChatMessage[] = [
      // I sent to peer-2
      msg({
        id: 1,
        scope: "dm",
        fromIdentity: LOCAL_CHAT_IDENTITY,
        to: "peer-2",
      }),
      // peer-2 sent to me
      msg({ id: 2, scope: "dm", fromIdentity: "peer-2", to: "me" }),
      // unrelated DM with peer-3
      msg({ id: 3, scope: "dm", fromIdentity: "peer-3", to: "me" }),
      // not a DM
      msg({ id: 4, scope: "global" }),
    ];
    expect(
      filterByTab({
        messages,
        tab: "dms",
        myZoneKey: NO_ZONE,
        myIdentity: "me",
        channelId: NO_CHANNEL,
        dmTarget: "peer-2",
      }).map((m) => m.id),
    ).toEqual([1, 2]);
  });
});

describe("filterByScope (legacy)", () => {
  it("still works for global and table scopes", () => {
    const messages = [
      msg({ id: 1, scope: "global" }),
      msg({ id: 2, scope: "table", tableId: "t1" }),
      msg({ id: 3, scope: "table", tableId: "t2" }),
    ];
    expect(filterByScope(messages, "global", null).map((m) => m.id)).toEqual([1]);
    expect(filterByScope(messages, "table", "t1").map((m) => m.id)).toEqual([2]);
    expect(filterByScope(messages, "table", null)).toEqual([]);
  });
});

describe("unreadByTab", () => {
  it("counts only inbound messages newer than last-seen", () => {
    const messages: ChatMessage[] = [
      msg({ id: 1, scope: "global", fromIdentity: "peer-1" }),
      msg({ id: 2, scope: "global", fromIdentity: LOCAL_CHAT_IDENTITY }),
      msg({ id: 3, scope: "global", fromIdentity: "peer-1" }),
      msg({ id: 4, scope: "channel", channelId: "c1", fromIdentity: "peer-1" }),
    ];
    const seen: Record<ChatTab, number> = { global: 1, zone: 0, channels: 0, dms: 0 };
    const out = unreadByTab(messages, seen, {
      myZoneKey: NO_ZONE,
      myIdentity: "me",
      channelId: "c1",
      dmTarget: NO_DM,
    });
    expect(out.global).toBe(1); // id=3 inbound past seen=1
    expect(out.channels).toBe(1); // id=4
    expect(out.zone).toBe(0);
    expect(out.dms).toBe(0);
  });
});

describe("collectDmPartners", () => {
  it("returns distinct partners sorted by most recent", () => {
    const peers = new Map<string, RemotePeer>([
      [
        "peer-2",
        {
          identity: "peer-2",
          name: "Bob",
          color: "#abc",
          x: 0,
          y: 0,
          lastSeq: -1n,
          lastUpdate: 0,
          tableId: null,
          status: "available",
        },
      ],
    ]);
    const messages: ChatMessage[] = [
      msg({ id: 1, scope: "dm", fromIdentity: LOCAL_CHAT_IDENTITY, to: "peer-2", ts: 10 }),
      msg({ id: 2, scope: "dm", fromIdentity: "peer-3", to: "me", ts: 20 }),
      msg({ id: 3, scope: "dm", fromIdentity: "peer-2", to: "me", ts: 30 }),
    ];
    const partners = collectDmPartners(messages, peers, "me");
    expect(partners.map((p) => p.identity)).toEqual(["peer-2", "peer-3"]);
    // partner-3 is unknown to the peers map → falls back to identity for name.
    expect(partners[1].name).toBe("peer-3");
    // partner-2 picked up the nickname from peers.
    expect(partners[0].name).toBe("Bob");
  });
});
