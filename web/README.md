# Syncle Web

React + Vite + TypeScript client. Joins the **same LiveKit room** and talks to the
**same Syncle backend** as the Android app, so a browser user and an Android user
appear as ordinary peers in the same spatial world.

## V1 scope (this scaffold)

- Login → `POST /v1/sessions` → LiveKit JWT (same flow as Android)
- Connect to LiveKit room
- Render the office map (`room1.jpg` + `map_config.json` shared with Android)
- WASD / arrow keys to move local avatar (AABB walkable clamping)
- 20 Hz binary position broadcast over LiveKit data channel
- Decode remote positions (Android peers included) and render as circles

**Not yet:** camera/microphone, table meetings, spatial audio gain, reconnect
backoff, mobile touch controls. These ship in later iterations.

## Quick start

```bash
# 1. Start backend + LiveKit (from repo root)
docker compose up --build

# 2. Web dev server
cd web
cp .env.example .env
npm install
npm run dev          # http://localhost:5173
```

The `predev` hook copies `map_config.json` and `room1.jpg` from
`app/src/main/assets/` into `public/`. Re-run `npm run sync-assets` after the
Android assets change.

## Layout

```
web/
├── public/                  (generated; git-ignored)
├── scripts/sync-assets.mjs  copies shared world assets from app/src/main/assets
├── src/
│   ├── data/                sessionApi.ts, liveKitService.ts
│   ├── domain/              positionPacket.ts (binary protocol mirror of
│   │                         PositionSyncEngine.kt), mapConfig.ts, camera.ts
│   ├── state/syncleStore.ts zustand store
│   ├── ui/                  JoinScreen, SyncleScreen, SpatialCanvas
│   ├── types/mapConfig.ts
│   ├── App.tsx, main.tsx, styles.css
└── vite.config.ts
```

## Shared contracts

The wire format must match `app/` and `server/` exactly. See
[../docs/contracts.md](../docs/contracts.md) for room/nickname/color rules and
`/v1/sessions` shape, and [src/domain/positionPacket.ts](src/domain/positionPacket.ts)
for the 17-byte position packet (little-endian: `type=1 | x:f32 | y:f32 | seq:i64`).
