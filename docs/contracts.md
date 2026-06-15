# Syncle shared contracts

Authoritative definitions for values that MUST be kept in sync between the
**Android client**, the **Web client** (`web/`), and the **Node backend**
(`server/`). If you change one side, change the others in the same PR.

For the binary position packet (17-byte little-endian: `type=1 | x:f32 | y:f32 | seq:i64`),
see [app/.../PositionSyncEngine.kt](../app/src/main/java/com/example/syncle/domain/PositionSyncEngine.kt)
and [web/src/domain/positionPacket.ts](../web/src/domain/positionPacket.ts) — both
implementations must stay byte-identical.

## Room name

| Property | Value |
| --- | --- |
| Regex | `^[a-z0-9-]{3,64}$` |
| Allowed chars | lowercase letters, digits, hyphen |
| Min length | 3 |
| Max length | 64 |
| Default | `syncle-office` |

Why constrained: room names appear in URLs, log lines, and LiveKit identifiers;
keeping them ASCII-safe avoids encoding bugs across the stack.

### Where it lives

| Side | File | Symbol |
| --- | --- | --- |
| Server | [server/src/routes/sessions.ts](../server/src/routes/sessions.ts) | `z.string().regex(/^[a-z0-9-]{3,64}$/, ...)` |
| Client | [app/src/main/java/com/example/syncle/data/ProfileStore.kt](../app/src/main/java/com/example/syncle/data/ProfileStore.kt) | `ProfileStore.ROOM_REGEX` |

### Test coverage

- Server: [server/tests/routes.test.ts](../server/tests/routes.test.ts) — `it.each` covers `"ab"`, `"UPPER"`, `"bad room"`, `"bad/slash"`, 65-char input, plus the positive `team-alpha-42`.
- Client: covered indirectly by `ProfileStore.isValidRoom` callers; the UI surfaces validation errors inline on the join screen.

## Nickname

| Property | Value |
| --- | --- |
| Min length | 1 (after trim) |
| Max length | 32 |
| Allowed chars | unrestricted (UTF-8) |

| Side | File | Symbol |
| --- | --- | --- |
| Client | [app/src/main/java/com/example/syncle/data/ProfileStore.kt](../app/src/main/java/com/example/syncle/data/ProfileStore.kt) | `ProfileStore.NICKNAME_MAX_LEN`, `isValidNickname` |
| Server | not currently enforced — server accepts whatever the client sends |

If the server adds nickname validation later, mirror these bounds.

## Color (accent)

| Property | Value |
| --- | --- |
| Format | CSS hex string, e.g. `#4F8EF7` |
| Palette | 8 fixed swatches |

| Side | File | Symbol |
| --- | --- | --- |
| Client | [app/src/main/java/com/example/syncle/data/ProfileStore.kt](../app/src/main/java/com/example/syncle/data/ProfileStore.kt) | `ProfileStore.PALETTE` |
| Server | passes the value through — no validation |

## /v1/sessions request

```
POST /v1/sessions
Content-Type: application/json

{
  "deviceId":  string,        // stable per-device UUID
  "nickname":  string,        // display name
  "color":     string,        // "#RRGGBB"
  "room":      string         // matches room regex above
}
```

Response (200):

```
{
  "serverUrl": "ws://...",
  "token":     string,         // LiveKit JWT
  "userId":    string,         // server-assigned identity
  "nickname":  string,
  "color":     string,
  "expiresAt": number          // epoch ms when the token expires
}
```

Client behavior on token expiry: the reconnect loop refreshes the JWT when
`expiresAt - now < 60_000` ms before the next LiveKit connect attempt
(see `SyncleViewModel.scheduleReconnect`).

## LiveKit participant attributes

Per-participant key/value strings published via
`LocalParticipant.setAttributes(...)` and observed by remotes via
`RoomEvent.ParticipantAttributesChanged`. Both clients MUST use the keys
below verbatim; servers do not validate them.

| Key | Type | Purpose | Empty-string meaning |
| --- | --- | --- | --- |
| `table_id` | string | Currently seated table id. Drives the sit-at-table meeting feature. | "explicitly stood up" (cleared) |
| `nickname` | string | Display name. Falls back to LiveKit identity when missing. | "not published" — keep existing |
| `color`    | string | `#RRGGBB` accent color for avatar. Falls back to a default. | "not published" — keep existing |
| `character`| string | Android-only sprite character id. Web ignores. | "not published" — keep existing |

### Where it lives

| Side | File | Symbol |
| --- | --- | --- |
| Android | [app/.../TablePresence.kt](../app/src/main/java/com/example/syncle/domain/TablePresence.kt) | `ATTR_TABLE_ID = "table_id"` |
| Android | [app/.../SyncleViewModel.kt](../app/src/main/java/com/example/syncle/viewmodel/SyncleViewModel.kt) | `ATTR_COLOR`, `ATTR_NICKNAME`, `ATTR_CHARACTER` |
| Web | [web/src/data/liveKitService.ts](../web/src/data/liveKitService.ts) | `setTableAttribute`, `publishProfileAttributes` |

### Required server grant

The JWT must include `canUpdateOwnMetadata: true` for clients to be allowed
to call `setAttributes`. Without it, LiveKit responds with
`SignalRequestError: does not have permission to update own metadata`.
See [server/src/livekit.ts](../server/src/livekit.ts).
