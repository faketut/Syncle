import { useEffect, useReducer, useRef } from "react";
import {
  Room,
  RoomEvent,
  Track,
  type RemoteParticipant,
  type RemoteTrackPublication,
  type LocalTrackPublication,
} from "livekit-client";
import { useSyncle } from "../state/syncleStore";

export interface VideoTilesProps {
  room: Room;
}

/** Renders a horizontal strip of camera tiles for the local user plus any
 *  remote peer at the same table whose camera is published and unmuted.
 *  Mirrors the audio-scoping policy: cameras outside our table aren't shown
 *  (and we also unsubscribe their video tracks elsewhere). */
export function VideoTiles({ room }: VideoTilesProps) {
  const self = useSyncle((s) => s.self);
  const peers = useSyncle((s) => s.peers);
  // Force a re-render when any track event fires; the actual track lookup is
  // imperative against `room` so we don't need to mirror tracks into state.
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

  // Always render the strip container; conditional tiles inside.
  // Local self-preview only when seated AND camera publishing is live.
  const localCamPub = findLocalCameraPub(room);
  const showLocal = self?.tableId != null && localCamPub && !localCamPub.isMuted;
  // Local screen-share self-preview (always-on when publishing — gives the
  // sharer instant feedback that the picker picked the right surface).
  const localScreenPub = findLocalScreenSharePub(room);
  const showLocalScreen = self?.tableId != null && localScreenPub && !localScreenPub.isMuted;

  // Remote tiles: peers at our table with a live, subscribed camera or
  // screen-share track. Screen-share tiles render first (and bigger).
  const remoteCamTiles: { identity: string; name: string; color: string; pub: RemoteTrackPublication }[] = [];
  const remoteScreenTiles: { identity: string; name: string; color: string; pub: RemoteTrackPublication }[] = [];
  if (self?.tableId) {
    for (const [identity, rp] of room.remoteParticipants) {
      const peer = peers.get(identity);
      if (peer?.tableId !== self.tableId) continue;
      const cam = findRemoteCameraPub(rp);
      if (cam && cam.isSubscribed && !cam.isMuted && cam.track) {
        remoteCamTiles.push({
          identity,
          name: peer.name ?? identity,
          color: peer.color ?? "#5AC8FA",
          pub: cam,
        });
      }
      const screen = findRemoteScreenSharePub(rp);
      if (screen && screen.isSubscribed && !screen.isMuted && screen.track) {
        remoteScreenTiles.push({
          identity,
          name: peer.name ?? identity,
          color: peer.color ?? "#5AC8FA",
          pub: screen,
        });
      }
    }
  }

  if (
    !showLocal &&
    !showLocalScreen &&
    remoteCamTiles.length === 0 &&
    remoteScreenTiles.length === 0
  ) {
    return null;
  }

  return (
    <div className="video-tiles">
      {showLocalScreen && localScreenPub?.track && (
        <VideoTile
          key="__self_screen__"
          name={`${self?.nickname ?? "You"} (sharing)`}
          color={self?.color ?? "#4F8EF7"}
          track={localScreenPub.track}
          muted
          large
        />
      )}
      {remoteScreenTiles.map((t) => (
        <VideoTile
          key={`screen:${t.identity}`}
          name={`${t.name} (sharing)`}
          color={t.color}
          track={t.pub.track!}
          large
        />
      ))}
      {showLocal && localCamPub?.track && (
        <VideoTile
          key="__self__"
          name={`${self?.nickname ?? "You"} (you)`}
          color={self?.color ?? "#4F8EF7"}
          track={localCamPub.track}
          muted
          mirror
        />
      )}
      {remoteCamTiles.map((t) => (
        <VideoTile
          key={t.identity}
          name={t.name}
          color={t.color}
          track={t.pub.track!}
        />
      ))}
    </div>
  );
}

function VideoTile({
  name,
  color,
  track,
  muted,
  mirror,
  large,
}: {
  name: string;
  color: string;
  track: { attach: (el: HTMLVideoElement) => void; detach: (el: HTMLVideoElement) => void };
  muted?: boolean;
  mirror?: boolean;
  large?: boolean;
}) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  useEffect(() => {
    const el = videoRef.current;
    if (!el) return;
    track.attach(el);
    return () => {
      track.detach(el);
    };
  }, [track]);
  return (
    <div className={`video-tile${large ? " large" : ""}`}>
      <video
        ref={videoRef}
        autoPlay
        playsInline
        muted={muted}
        style={mirror ? { transform: "scaleX(-1)" } : undefined}
      />
      <div className="video-tile-label" style={{ borderColor: color }}>
        {name}
      </div>
    </div>
  );
}

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
