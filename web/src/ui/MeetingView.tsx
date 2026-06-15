import { useEffect, useReducer, useRef } from "react";
import {
  Room,
  RoomEvent,
  Track,
  type RemoteParticipant,
  type RemoteTrackPublication,
  type LocalTrackPublication,
} from "livekit-client";
import { LOCAL_CHAT_IDENTITY, useSyncle } from "../state/syncleStore";
import {
  gridLayout,
  MAX_VISIBLE_TILES,
  overflowCount,
} from "../domain/meetingLayout";

/**
 * Fullscreen meeting view (M4). Mounted over the spatial canvas when the
 * user opts in from the HUD. Renders all video tiles at the same table
 * (self + remote cameras + remote screen shares) in an auto-laid-out grid
 * of up to 9 tiles, with:
 *
 *   - 2px ring on the active speaker (driven by `speakingIdentities`)
 *   - Floating reaction emoji rising from each tile (driven by `reactions`)
 *   - Top-right "Leave meeting" pill that closes the view but keeps the
 *     user seated, mic'd up, and connected — same model as Gather.
 *
 * Audio scoping is unchanged from the spatial view: the existing
 * SyncleScreen effects gate mic / video subscription to same-table peers,
 * so MeetingView just renders whatever is already wired up.
 */
export interface MeetingViewProps {
  room: Room;
  onLeave: () => void;
}

interface Tile {
  /** Stable React key. */
  key: string;
  /** LiveKit identity of the publisher, or LOCAL_CHAT_IDENTITY for self.
   *  Used to look up `speakingIdentities` and `reactions`. */
  identity: string;
  /** Display name (suffixed for screen shares + self). */
  name: string;
  /** Border / accent color. */
  color: string;
  /** Track to attach. We accept the structural minimum so mock tracks in
   *  tests stay easy to construct. */
  track: { attach: (el: HTMLVideoElement) => void; detach: (el: HTMLVideoElement) => void };
  /** True for the local self-preview (muted + mirrored). */
  isSelf: boolean;
  /** True for screen-share tiles (no mirror, "(sharing)" suffix). */
  isScreen: boolean;
}

export function MeetingView({ room, onLeave }: MeetingViewProps) {
  const self = useSyncle((s) => s.self);
  const peers = useSyncle((s) => s.peers);
  const speakingIdentities = useSyncle((s) => s.speakingIdentities);
  const reactions = useSyncle((s) => s.reactions);
  const leaveBtnRef = useRef<HTMLButtonElement | null>(null);

  // Capture the focused element at mount and restore it on unmount so
  // keyboard users return to the "Meeting view" HUD button after closing.
  useEffect(() => {
    const previouslyFocused =
      typeof document !== "undefined"
        ? (document.activeElement as HTMLElement | null)
        : null;
    // Move focus into the dialog on the next tick so the autofocus target
    // exists. Without this, screen readers stay on the trigger button.
    queueMicrotask(() => leaveBtnRef.current?.focus());
    return () => {
      previouslyFocused?.focus?.();
    };
  }, []);

  // Track events drive re-render; the lookup itself is imperative so we
  // don't have to mirror LiveKit state into Zustand.
  const [, bump] = useReducer((n: number) => n + 1, 0);
  useEffect(() => {
    const onChange = () => bump();
    room
      .on(RoomEvent.TrackSubscribed, onChange)
      .on(RoomEvent.TrackUnsubscribed, onChange)
      .on(RoomEvent.TrackMuted, onChange)
      .on(RoomEvent.TrackUnmuted, onChange)
      .on(RoomEvent.LocalTrackPublished, onChange)
      .on(RoomEvent.LocalTrackUnpublished, onChange);
    return () => {
      room
        .off(RoomEvent.TrackSubscribed, onChange)
        .off(RoomEvent.TrackUnsubscribed, onChange)
        .off(RoomEvent.TrackMuted, onChange)
        .off(RoomEvent.TrackUnmuted, onChange)
        .off(RoomEvent.LocalTrackPublished, onChange)
        .off(RoomEvent.LocalTrackUnpublished, onChange);
    };
  }, [room]);

  // Escape closes the meeting view. Stops propagation so it doesn't also
  // close a sticky-note modal opened elsewhere.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.preventDefault();
        e.stopPropagation();
        onLeave();
      }
    };
    window.addEventListener("keydown", onKey, true);
    return () => window.removeEventListener("keydown", onKey, true);
  }, [onLeave]);

  // Build tile list. Screen shares first (they're usually what people are
  // looking at), then self, then remotes.
  const tiles: Tile[] = [];

  const localScreen = findLocalScreenSharePub(room);
  if (
    self?.tableId != null &&
    localScreen?.track &&
    !localScreen.isMuted
  ) {
    tiles.push({
      key: "self-screen",
      identity: LOCAL_CHAT_IDENTITY,
      name: `${self.nickname} (sharing)`,
      color: self.color,
      track: localScreen.track,
      isSelf: true,
      isScreen: true,
    });
  }

  if (self?.tableId != null) {
    for (const [identity, rp] of room.remoteParticipants) {
      const peer = peers.get(identity);
      if (peer?.tableId !== self.tableId) continue;
      const screen = findRemoteScreenSharePub(rp);
      if (
        screen?.track &&
        screen.isSubscribed &&
        !screen.isMuted
      ) {
        tiles.push({
          key: `screen-${identity}`,
          identity,
          name: `${peer.name ?? identity} (sharing)`,
          color: peer.color ?? "#5AC8FA",
          track: screen.track,
          isSelf: false,
          isScreen: true,
        });
      }
    }
  }

  const localCam = findLocalCameraPub(room);
  if (
    self?.tableId != null &&
    localCam?.track &&
    !localCam.isMuted
  ) {
    tiles.push({
      key: "self-cam",
      identity: LOCAL_CHAT_IDENTITY,
      name: `${self.nickname} (you)`,
      color: self.color,
      track: localCam.track,
      isSelf: true,
      isScreen: false,
    });
  } else if (self) {
    // Always include a placeholder for self so the user sees themselves in
    // the grid even with camera off. Placeholder = colored swatch + name.
    tiles.push({
      key: "self-placeholder",
      identity: LOCAL_CHAT_IDENTITY,
      name: `${self.nickname} (you)`,
      color: self.color,
      track: PLACEHOLDER_TRACK,
      isSelf: true,
      isScreen: false,
    });
  }

  if (self?.tableId != null) {
    for (const [identity, rp] of room.remoteParticipants) {
      const peer = peers.get(identity);
      if (peer?.tableId !== self.tableId) continue;
      const cam = findRemoteCameraPub(rp);
      if (
        cam?.track &&
        cam.isSubscribed &&
        !cam.isMuted
      ) {
        tiles.push({
          key: `cam-${identity}`,
          identity,
          name: peer.name ?? identity,
          color: peer.color ?? "#5AC8FA",
          track: cam.track,
          isSelf: false,
          isScreen: false,
        });
      } else {
        // Camera-off remote — render placeholder so the participant is
        // still visible in the meeting roster.
        tiles.push({
          key: `placeholder-${identity}`,
          identity,
          name: peer.name ?? identity,
          color: peer.color ?? "#5AC8FA",
          track: PLACEHOLDER_TRACK,
          isSelf: false,
          isScreen: false,
        });
      }
    }
  }

  const overflow = overflowCount(tiles.length);
  const visible = tiles.slice(0, MAX_VISIBLE_TILES);
  const { cols, rows } = gridLayout(visible.length);

  return (
    <div className="meeting-view" role="dialog" aria-modal="true" aria-label="Meeting view">
      <div className="meeting-topbar">
        <div className="meeting-title">
          {self?.tableId ? `Meeting · ${self.tableId}` : "Meeting"}
        </div>
        <button
          ref={leaveBtnRef}
          type="button"
          className="meeting-leave"
          onClick={onLeave}
          aria-label="Leave meeting view"
          title="Leave meeting view (Esc)"
        >
          <LogOutIcon />
          <span>Leave</span>
        </button>
      </div>
      <div
        className="meeting-grid"
        style={
          {
            "--cols": cols,
            "--rows": rows,
          } as React.CSSProperties
        }
      >
        {visible.map((t) => (
          <MeetingTile
            key={t.key}
            tile={t}
            speaking={speakingIdentities.has(t.identity)}
            reaction={reactions.get(t.identity)?.glyph ?? null}
          />
        ))}
      </div>
      {overflow > 0 && (
        <div className="meeting-overflow" role="status">
          +{overflow} more participant{overflow === 1 ? "" : "s"} not shown
        </div>
      )}
    </div>
  );
}

function MeetingTile({
  tile,
  speaking,
  reaction,
}: {
  tile: Tile;
  speaking: boolean;
  reaction: string | null;
}) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  useEffect(() => {
    const el = videoRef.current;
    if (!el || tile.track === PLACEHOLDER_TRACK) return;
    tile.track.attach(el);
    return () => {
      tile.track.detach(el);
    };
  }, [tile.track]);

  const isPlaceholder = tile.track === PLACEHOLDER_TRACK;

  return (
    <div
      className={`meeting-tile${speaking ? " speaking" : ""}${tile.isScreen ? " screen" : ""}`}
      style={{ borderColor: speaking ? "#007AFF" : "transparent" }}
    >
      {isPlaceholder ? (
        <div
          className="meeting-tile-placeholder"
          style={{ background: tile.color }}
          aria-hidden="true"
        >
          <span>{initials(tile.name)}</span>
        </div>
      ) : (
        <video
          ref={videoRef}
          autoPlay
          playsInline
          muted={tile.isSelf}
          style={
            tile.isSelf && !tile.isScreen ? { transform: "scaleX(-1)" } : undefined
          }
        />
      )}
      <div className="meeting-tile-label" style={{ borderColor: tile.color }}>
        {tile.name}
      </div>
      {reaction && (
        <div className="meeting-tile-reaction" aria-hidden="true">
          <span>{reaction}</span>
        </div>
      )}
    </div>
  );
}

function LogOutIcon() {
  return (
    <svg
      width="14"
      height="14"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
      <polyline points="16 17 21 12 16 7" />
      <line x1="21" y1="12" x2="9" y2="12" />
    </svg>
  );
}

/** Strip "(you)" / "(sharing)" suffixes and return up to 2 initials. */
function initials(name: string): string {
  const clean = name.replace(/\(.+\)$/, "").trim();
  const parts = clean.split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[1][0]).toUpperCase();
}

// Sentinel track used for the camera-off placeholder tile. Comparing by
// reference lets MeetingTile skip the attach/detach effect.
const PLACEHOLDER_TRACK = {
  attach: () => {},
  detach: () => {},
} as const;

function findLocalCameraPub(room: Room): LocalTrackPublication | undefined {
  for (const pub of room.localParticipant.trackPublications.values()) {
    if (pub.kind === Track.Kind.Video && pub.source === Track.Source.Camera) {
      return pub;
    }
  }
  return undefined;
}

function findLocalScreenSharePub(room: Room): LocalTrackPublication | undefined {
  for (const pub of room.localParticipant.trackPublications.values()) {
    if (pub.kind === Track.Kind.Video && pub.source === Track.Source.ScreenShare) {
      return pub;
    }
  }
  return undefined;
}

function findRemoteCameraPub(p: RemoteParticipant): RemoteTrackPublication | undefined {
  for (const pub of p.videoTrackPublications.values()) {
    if (pub.source === Track.Source.Camera) return pub;
  }
  return undefined;
}

function findRemoteScreenSharePub(p: RemoteParticipant): RemoteTrackPublication | undefined {
  for (const pub of p.videoTrackPublications.values()) {
    if (pub.source === Track.Source.ScreenShare) return pub;
  }
  return undefined;
}
