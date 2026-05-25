# Syncle shared contracts

Authoritative definitions for values that MUST be kept in sync between the
Android client and the Node backend. If you change one side, change the other
side in the same PR.

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
