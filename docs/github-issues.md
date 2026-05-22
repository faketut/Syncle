# Ready-to-paste GitHub issues — Syncle Android

> Generated from a static analysis pass on `main`. Each section below is one issue.
> Format: **Title** → suggested **labels** → **context** → **acceptance criteria** → **pointers**.
> File paths are workspace-relative; line numbers are 1-based and may drift slightly after edits.

---

## 1. Remove stale `com.example.gather` test directory (5 empty files)

**Labels:** `cleanup`, `dead-code`, `tests`, `good-first-issue`

### Context
The package `com.example.gather` is a leftover from the project's previous name. All five files under it are 0 bytes and contribute nothing — they confuse `./gradlew test` output, IDE navigation, and JVM test discovery, and leak the old brand into the test source tree.

```
app/src/test/java/com/example/gather/
├── AdvancedAudioTest.kt        (empty)
├── CollisionEdgeCaseTest.kt    (empty)
├── MapRepositoryTest.kt        (empty)
├── SpatialLogicTest.kt         (empty)
└── SyncAndInterpolationTest.kt (empty)
```

The replacement tests already exist under [app/src/test/java/com/example/syncle/](app/src/test/java/com/example/syncle/) (`MapConfigCacheTest`, `PositionSyncEngineTest`, `SpatialAudioEngineTest`, `TablePresenceTest`).

### Acceptance criteria
- [ ] `app/src/test/java/com/example/gather/` directory is deleted entirely.
- [ ] `./gradlew :app:testDebugUnitTest` still passes.
- [ ] No reference to the `com.example.gather` package remains (`rg "com\.example\.gather"` returns 0 hits).

### Pointers
- [app/src/test/java/com/example/gather/AdvancedAudioTest.kt](app/src/test/java/com/example/gather/AdvancedAudioTest.kt)
- [app/src/test/java/com/example/gather/CollisionEdgeCaseTest.kt](app/src/test/java/com/example/gather/CollisionEdgeCaseTest.kt)
- [app/src/test/java/com/example/gather/MapRepositoryTest.kt](app/src/test/java/com/example/gather/MapRepositoryTest.kt)
- [app/src/test/java/com/example/gather/SpatialLogicTest.kt](app/src/test/java/com/example/gather/SpatialLogicTest.kt)
- [app/src/test/java/com/example/gather/SyncAndInterpolationTest.kt](app/src/test/java/com/example/gather/SyncAndInterpolationTest.kt)

---

## 2. Drop dead JSON-position legacy decode path & redundant `org.json` dependency

**Labels:** `cleanup`, `dead-code`, `dependencies`, `perf`

### Context
`PositionSyncEngine.encodeIfMoved` only emits the 17-byte binary `TYPE_POSITION` packet, but `decode()` still falls back to `decodeLegacyJson()`. No producer in this repo or the documented protocol ever sends the JSON form, so the fallback is unreachable in normal operation — and on a hostile peer, it lets arbitrary attacker-controlled bytes be parsed as JSON on the 20 Hz hot path. The shipped JSON also bypasses the `seq` reorder check (defaults to 0).

The accompanying dependency `org.json:json:20230227` ([app/build.gradle](app/build.gradle#L75)) is also unnecessary: `org.json` ships with the Android platform, so adding the JAR only bloats the APK and risks classloader conflicts.

### Acceptance criteria
- [ ] `decodeLegacyJson(...)` and the `import org.json.JSONObject` in `PositionSyncEngine.kt` are removed.
- [ ] `PositionSyncEngine.decode()` returns `null` for any non-binary payload.
- [ ] `implementation 'org.json:json:20230227'` is removed from `app/build.gradle`.
- [ ] `MapRepository` (which also uses `org.json`) still compiles using the platform-provided classes (it will — the package name is identical).
- [ ] `./gradlew :app:assembleDebug` and `:app:testDebugUnitTest` pass.

### Pointers
- [app/src/main/java/com/example/syncle/domain/PositionSyncEngine.kt](app/src/main/java/com/example/syncle/domain/PositionSyncEngine.kt#L34-L52) — `decode()` and `decodeLegacyJson()`
- [app/build.gradle](app/build.gradle#L75) — `org.json:json:20230227`

---

## 3. Remove unused `lastBroadcastSeq` write in `PositionSyncEngine`

**Labels:** `cleanup`, `dead-code`, `good-first-issue`

### Context
`PositionSyncEngine` stores `lastBroadcastSeq` in `encodeIfMoved` and resets it in `reset()`, but the value is never read anywhere. It is pure dead state.

### Acceptance criteria
- [ ] Field `lastBroadcastSeq` is removed.
- [ ] All references (`= seq`, `= 0L`) are deleted.
- [ ] Tests in [PositionSyncEngineTest](app/src/test/java/com/example/syncle/domain/PositionSyncEngineTest.kt) still pass.

### Pointers
- [app/src/main/java/com/example/syncle/domain/PositionSyncEngine.kt](app/src/main/java/com/example/syncle/domain/PositionSyncEngine.kt#L11)
- [app/src/main/java/com/example/syncle/domain/PositionSyncEngine.kt](app/src/main/java/com/example/syncle/domain/PositionSyncEngine.kt#L21)
- [app/src/main/java/com/example/syncle/domain/PositionSyncEngine.kt](app/src/main/java/com/example/syncle/domain/PositionSyncEngine.kt#L61)

---

## 4. Fix `MapConfig.computeMapBounds` initial `Float.MIN_VALUE` bug

**Labels:** `bug`, `clean-code`

### Context
`computeMapBounds` initializes `right` and `bottom` to `Float.MIN_VALUE`, which in Java/Kotlin is the **smallest positive** float (≈ 1.4e-45), not the most negative value. If any walkable area has negative `right`/`bottom` (e.g. a map authored with negative coordinates, or a future world with origin not at top-left), the computed bounds will be wrong, breaking `mapDrawSize` and the camera clamp. The convention for "min/max accumulator seeds" is `-Float.MAX_VALUE` / `Float.MAX_VALUE`.

### Acceptance criteria
- [ ] `right` and `bottom` are seeded with `-Float.MAX_VALUE`.
- [ ] A new unit test in `MapConfigCacheTest` (or a new `MapConfigTest`) covers a walkable rect with negative coordinates and asserts the computed bounds equal the rect.

### Pointers
- [app/src/main/java/com/example/syncle/model/MapConfig.kt](app/src/main/java/com/example/syncle/model/MapConfig.kt#L27-L40)

---

## 5. Replace `Divider` with `HorizontalDivider` (Material3 deprecation)

**Labels:** `clean-code`, `compose`, `good-first-issue`

### Context
`androidx.compose.material3.Divider` was deprecated in Material3; the replacement is `HorizontalDivider` (or `VerticalDivider`). The deprecation will become a warning today and a removal later.

### Acceptance criteria
- [ ] All call sites use `HorizontalDivider`.
- [ ] Build emits no `Divider is deprecated` warning.

### Pointers
- [app/src/main/java/com/example/syncle/ui/TableMeetingOverlay.kt](app/src/main/java/com/example/syncle/ui/TableMeetingOverlay.kt#L98)

---

## 6. Modernize `MainActivity` ViewModel acquisition + remove crash-prone preview

**Labels:** `clean-code`, `compose`, `modernization`

### Context
Several modernization opportunities in [MainActivity.kt](app/src/main/java/com/example/syncle/MainActivity.kt):

1. `ViewModelProvider(this)[SyncleViewModel::class.java]` is the legacy API; the idiomatic Compose form is `by viewModels()` (from `androidx.activity:activity-ktx`/`activity-compose`).
2. `e.printStackTrace()` in the asset-loading fallback bypasses `SyncleLog`.
3. `SynclePreview()` instantiates a real `SyncleViewModel()` and a full `SyncleApp(vm)` — previews should not exercise LiveKit code paths or hit `viewModelScope`. The preview will fail to render (or run network code) in Android Studio's Layout Inspector.
4. Hard-coded Chinese strings (`"Syncle 需要麦克风与相机权限..."`, `"授予权限"`) belong in `res/values/strings.xml` so they're localizable and accessible to translation tools.

### Acceptance criteria
- [ ] `MainActivity` obtains `viewModel` via `by viewModels()`.
- [ ] The asset-load `catch` logs via `SyncleLog.e("loadMapConfig failed", e)`.
- [ ] `SynclePreview` is removed or rewritten to call a stateless `SyncleScreen` composable with mock data (no `SyncleViewModel`).
- [ ] User-facing permission strings live in `res/values/strings.xml` (and a `values-zh/strings.xml` if we want to keep zh-CN copy).
- [ ] App still builds + permission flow still works on a clean install.

### Pointers
- [app/src/main/java/com/example/syncle/MainActivity.kt](app/src/main/java/com/example/syncle/MainActivity.kt#L29) — `ViewModelProvider`
- [app/src/main/java/com/example/syncle/MainActivity.kt](app/src/main/java/com/example/syncle/MainActivity.kt#L40) — `e.printStackTrace()`
- [app/src/main/java/com/example/syncle/MainActivity.kt](app/src/main/java/com/example/syncle/MainActivity.kt#L110-L120) — hard-coded zh strings
- [app/src/main/java/com/example/syncle/MainActivity.kt](app/src/main/java/com/example/syncle/MainActivity.kt#L207-L228) — `SynclePreview`

---

## 7. Bump Compose / lifecycle / activity versions; remove pinned `ui-geometry:1.4.3`

**Labels:** `modernization`, `dependencies`

### Context
[app/build.gradle](app/build.gradle) pins:

- Compose BOM `2023.03.00` (≈ Compose 1.4.x) — current is `2024.x`.
- `lifecycle-runtime-ktx:2.6.1`, `lifecycle-runtime-compose:2.6.1`.
- `activity-compose:1.7.2`.
- `core-ktx:1.10.1`.
- A hard-pinned `androidx.compose.ui:ui-geometry:1.4.3` in the test classpath (line 81). This must match the main `compose-bom` and should normally come transitively — pinning it tightly to 1.4.3 fights the BOM.

Bumping the BOM unlocks newer `HorizontalDivider`, stable `material-icons-extended` artifacts, perf fixes, and is necessary before AGP/Kotlin upgrades.

### Acceptance criteria
- [ ] `compose-bom` is bumped to the latest stable line (e.g. `2024.09.00` or newer).
- [ ] `lifecycle-*` bumped to `2.8.x`.
- [ ] `activity-compose` bumped to `1.9.x`.
- [ ] `core-ktx` bumped to `1.13.x`.
- [ ] The hard-coded `ui-geometry:1.4.3` line is either removed or replaced with `testImplementation "androidx.compose.ui:ui-geometry"` (no version, BOM-aligned).
- [ ] `kotlinCompilerExtensionVersion` updated to match the new Compose compiler / Kotlin combo if needed.
- [ ] `./gradlew :app:assembleDebug` and `:app:testDebugUnitTest` pass.

### Pointers
- [app/build.gradle](app/build.gradle#L67-L83)
- [build.gradle](build.gradle#L3-L5) — Kotlin/AGP versions
- [gradle/wrapper/gradle-wrapper.properties](gradle/wrapper/gradle-wrapper.properties)

---

## 8. Collapse `SyncleViewModel` boilerplate getters/setters; trigger UI from a single observable

**Labels:** `clean-code`, `refactor`

### Context
[SyncleViewModel.kt](app/src/main/java/com/example/syncle/model/SyncleViewModel.kt) keeps every connection field in two places (`xxxInternal` private + public `var` with custom getter/setter that calls `pushUiState()`). This is ≈ 60 lines of mechanical glue that is easy to get wrong — every new field needs the same pattern, and the `private set:` syntax on `connectionStatus`/`isAutoFetching` plus a public mutable `url`/`token` mixes write semantics confusingly.

Better: keep the canonical state in `_uiState` (or a private `MutableStateFlow<ConnectionUi>`) and expose `fun setUrl(...)` / `fun setToken(...)` mutators. UI already collects `uiState`, so the parallel backing fields are not needed.

### Acceptance criteria
- [ ] `urlInternal`, `tokenInternal`, `isAutoFetchingInternal`, `startupErrorInternal`, `lastConnectErrorInternal`, `connectionStatusInternal` and the matching `var` properties are reduced to a single source of truth (e.g. nested `MutableStateFlow<ConnectionUi>` or a `private data class ConnectionInternalState`).
- [ ] All call sites in `MainActivity` (`viewModel.url = it`, `viewModel.token = it`) are updated.
- [ ] `pushUiState()` is no longer invoked from setters; UI updates flow naturally through the state container.
- [ ] No behavior change visible to the user.

### Pointers
- [app/src/main/java/com/example/syncle/model/SyncleViewModel.kt](app/src/main/java/com/example/syncle/model/SyncleViewModel.kt#L44-L99) — backing fields + accessors
- [app/src/main/java/com/example/syncle/MainActivity.kt](app/src/main/java/com/example/syncle/MainActivity.kt#L161-L170) — call sites

---

## 9. Remove magic-number `delay(300)` in `toggleMeetingCamera`

**Labels:** `bug-risk`, `clean-code`, `compose`

### Context
After flipping the camera the ViewModel posts UI immediately, then launches a coroutine that `delay(300)` and pushes again — a hack to wait for the LiveKit local video track to appear so the `MeetingParticipant.videoTrack` becomes non-null. On slower devices this race fails (track not ready) and on faster ones we burn an unnecessary frame. The right fix is to observe the local track creation (e.g. participant track-published event) and push state then.

### Acceptance criteria
- [ ] The `delay(300)` block is removed.
- [ ] State refreshes when `LocalParticipant` actually publishes/unpublishes the camera track (e.g. by collecting the existing `RoomEvent.TrackPublished` / `TrackUnpublished` or by exposing the local track as a `Flow` from `LiveKitService`).
- [ ] Local-video tile in `TableMeetingOverlay` lights up reliably on cold cameras (manually verified once).

### Pointers
- [app/src/main/java/com/example/syncle/model/SyncleViewModel.kt](app/src/main/java/com/example/syncle/model/SyncleViewModel.kt#L195-L201) — `toggleMeetingCamera`
- [app/src/main/java/com/example/syncle/model/LiveKitService.kt](app/src/main/java/com/example/syncle/model/LiveKitService.kt#L67-L106) — `setupRoomListener` (add the new event)
- [app/src/main/java/com/example/syncle/domain/LiveKitEvent.kt](app/src/main/java/com/example/syncle/domain/LiveKitEvent.kt) — add a `LocalVideoTrackChanged` variant

---

## 10. Eliminate non-null assertions (`!!`) on lookups that can return null

**Labels:** `bug-risk`, `clean-code`

### Context
Three `!!` sites can crash the app if invariants drift:

1. `TablePresence.tableIdForJoinTap` does `mapConfig.tablesById[id]!!.rect` — `id` came from `mapConfig.tables`, so today it's safe, but the explicit `!!` will surprise a future contributor who hands a filtered `tables` list to this helper.
2. `AuthRepository` does `response.body!!.string()` after an `isSuccessful && body != null` check; safe today but the redundant `!!` reads as if a contract is missing.
3. `TableMeetingOverlay.MeetingParticipantTile` does `room = room!!` — guarded by `showVideo = room != null`, but `!!` defeats the smart cast that a local `val r = room ?: return` would give.

### Acceptance criteria
- [ ] All three `!!` operators are replaced with smart casts (`val r = room ?: return`), `requireNotNull` with a descriptive message, or `let { ... }` blocks — whichever is most idiomatic per site.
- [ ] No behavioral change.

### Pointers
- [app/src/main/java/com/example/syncle/model/TablePresence.kt](app/src/main/java/com/example/syncle/model/TablePresence.kt#L28)
- [app/src/main/java/com/example/syncle/model/AuthRepository.kt](app/src/main/java/com/example/syncle/model/AuthRepository.kt#L51)
- [app/src/main/java/com/example/syncle/ui/TableMeetingOverlay.kt](app/src/main/java/com/example/syncle/ui/TableMeetingOverlay.kt#L186)

---

## 11. Sub-sample background bitmap; avoid OOM on large `room1.jpg`

**Labels:** `perf`, `bug-risk`

### Context
`rememberMapBackgroundImage` reads the asset stream straight through `BitmapFactory.decodeStream(input)` and converts to `ImageBitmap` with no `inSampleSize`, no `inPreferredConfig`, and no awareness of screen size. A 4k+ JPEG ⇒ ~64 MB ARGB_8888 bitmap, which will OOM on entry-level devices and is wasted memory since `MapCamera.compute` typically draws it at scale ≤ 2×.

Bonus: world-space dimensions are derived from the bitmap pixel size ([SyncleScreen.kt L36-L40](app/src/main/java/com/example/syncle/ui/SyncleScreen.kt#L36-L40)), so sub-sampling must preserve the **logical** size as the original bitmap pixels — pass the unscaled dimensions separately, or supply logical map size via `map_config.json` and stop deriving it from the bitmap.

### Acceptance criteria
- [ ] Bitmap decode uses two-pass `inJustDecodeBounds` + `inSampleSize` to cap output to (e.g.) max screen-dimension × 2 in pixels.
- [ ] `RGB_565` config is used when the source has no alpha (most JPEGs).
- [ ] `logicWorldSize` is unaffected by sub-sampling — read from `map_config.json` (preferred) or from `inJustDecodeBounds` results, not from the decoded bitmap's `width/height`.
- [ ] Manual test: open the app — background still renders crisply and the avatar coordinate space is unchanged.

### Pointers
- [app/src/main/java/com/example/syncle/ui/MapBackgroundLoader.kt](app/src/main/java/com/example/syncle/ui/MapBackgroundLoader.kt#L11-L23)
- [app/src/main/java/com/example/syncle/ui/SyncleScreen.kt](app/src/main/java/com/example/syncle/ui/SyncleScreen.kt#L34-L40)
- [app/src/main/java/com/example/syncle/model/MapConfig.kt](app/src/main/java/com/example/syncle/model/MapConfig.kt#L13)

---

## 12. Stop publishing identical positions every 50 ms (cheap perf win + bandwidth)

**Labels:** `perf`, `clean-code`

### Context
The 20 Hz sync loop calls `positionSync.encodeIfMoved(...)` which already returns `null` when the position hasn't changed, so we don't publish data — good. However, the loop also unconditionally calls `meeting.syncPresence(cache)` and `liveKitService?.updateSpatialAudio(...)` 20×/s even when nothing has moved on either side. `SpatialAudioEngine.shouldApplyVolume` short-circuits with `volumeEpsilon`, but we still:

- Build `currentRoom.remoteParticipants.values.associateBy { ... }` (allocation + iteration) every tick.
- Iterate every track publication of every remote participant.

When idle (everyone parked) this is pure CPU wasted on the main thread (loop runs on `viewModelScope` ⇒ Main dispatcher).

### Acceptance criteria
- [ ] Maintain a dirty flag set when local moves OR any remote `targetPosition`/status changes; skip `updateSpatialAudio` when the flag is clean.
- [ ] Cache the `participantByIdentity` map in `LiveKitService` and invalidate it on `ParticipantConnected`/`ParticipantDisconnected` rather than rebuilding each call.
- [ ] Run the 20 Hz sync loop on `Dispatchers.Default` (encoding is CPU work; publication is suspending anyway).
- [ ] Idle CPU on a 2-peer room drops measurably in a quick `adb shell top` check (record before/after in the PR description).

### Pointers
- [app/src/main/java/com/example/syncle/model/SyncleViewModel.kt](app/src/main/java/com/example/syncle/model/SyncleViewModel.kt#L222-L246) — sync loop
- [app/src/main/java/com/example/syncle/model/LiveKitService.kt](app/src/main/java/com/example/syncle/model/LiveKitService.kt#L122-L149) — `updateSpatialAudio` / `applyVolume`

---

## 13. `PeerVideoOverlay` `derivedStateOf` keyed on volatile inputs — recomputes every frame

**Labels:** `perf`, `compose`

### Context
```kotlin
val nearbyPeers by remember(remotePeers, localPosition) {
    derivedStateOf {
        remotePeers.filter { (localPosition - it.position).getDistance() < proximityThreshold && it.videoTrack != null }
    }
}
```
The `remember` key is `localPosition` — an `Offset` that changes on every avatar tick — so the `derivedStateOf` is recreated every move, defeating the snapshot read-tracking the API is built for. The filter also reads `peer.position` (a `mutableStateOf`) for every peer, so a plain `derivedStateOf { … }` without `localPosition` in the key would already recompute correctly when needed.

### Acceptance criteria
- [ ] `remember { derivedStateOf { … } }` keyed only on a stable identity (e.g. the threshold) or no key at all.
- [ ] All inputs are read inside the lambda so Compose tracks them as snapshot reads.
- [ ] Manually verified: tile list updates as peers move in/out of the 300 px ring; recomposition profiler (Layout Inspector) shows fewer recompositions per move.

### Pointers
- [app/src/main/java/com/example/syncle/ui/PeerVideoOverlay.kt](app/src/main/java/com/example/syncle/ui/PeerVideoOverlay.kt#L27-L40)

---

## 14. `PositionSyncEngine.sequenceNow()` uses wall-clock; can stall or regress

**Labels:** `bug-risk`, `clean-code`

### Context
`sequenceNow()` returns `System.currentTimeMillis()`. Two adjacent calls within the same millisecond return the same `seq`, which the receiver compares with `>=` — currently harmless (we'd just accept the newer packet), but:

- If the device clock jumps backwards (NTP correction, user toggling timezone), all subsequent packets get an "older" seq than peers' last-seen and could conflict with reorder logic if it grows.
- `seq` is a per-sender concept; a monotonic 64-bit counter is both more correct and cheaper.

### Acceptance criteria
- [ ] `sequenceNow()` is replaced with an instance-scoped `AtomicLong` (or just a `Long` since the loop is single-threaded) that increments on every encode.
- [ ] Existing tests in [PositionSyncEngineTest](app/src/test/java/com/example/syncle/domain/PositionSyncEngineTest.kt) updated to reflect the monotonic contract.

### Pointers
- [app/src/main/java/com/example/syncle/domain/PositionSyncEngine.kt](app/src/main/java/com/example/syncle/domain/PositionSyncEngine.kt#L66-L69)
- [app/src/main/java/com/example/syncle/model/SyncleViewModel.kt](app/src/main/java/com/example/syncle/model/SyncleViewModel.kt#L226) — call site

---

## 15. Lock the `okhttp` dependency (currently relied on transitively from LiveKit SDK)

**Labels:** `bug-risk`, `dependencies`

### Context
[AuthRepository.kt](app/src/main/java/com/example/syncle/model/AuthRepository.kt#L6-L9) imports `okhttp3.*`, but `app/build.gradle` declares **no** `okhttp` dependency. It currently resolves because LiveKit's SDK exposes OkHttp transitively; the day LiveKit refactors that out — or upgrades to a binary-incompatible OkHttp — `AuthRepository` will fail to compile or hit `NoClassDefFoundError` at runtime. Direct uses must declare their own dependencies.

### Acceptance criteria
- [ ] Add explicit `implementation 'com.squareup.okhttp3:okhttp:<latest 4.x>'` to `app/build.gradle`.
- [ ] (Optional) Use the OkHttp BOM (`platform('com.squareup.okhttp3:okhttp-bom:...')`) so any future logging-interceptor stays in lockstep.
- [ ] Build passes; no version conflict warnings.

### Pointers
- [app/build.gradle](app/build.gradle#L67-L96)
- [app/src/main/java/com/example/syncle/model/AuthRepository.kt](app/src/main/java/com/example/syncle/model/AuthRepository.kt)

---

## 16. Fix `generate_token.py` typo: room is `"syncle -office"` (extra space)

**Labels:** `bug`, `dev-tools`, `good-first-issue`

### Context
[generate_token.py](generate_token.py#L30) sets `room = "syncle -office"` but `AuthRepository.fetchSandboxConnectionDetails` ([line 27](app/src/main/java/com/example/syncle/model/AuthRepository.kt#L27)) and the README both use `"syncle-office"`. Tokens minted by this script join a different room from the app's sandbox flow — a silent mismatch during local testing.

Also note `LIVEKIT_API_KEY = "devkey"` / `LIVEKIT_API_SECRET = "secret"` are committed in source. They are LiveKit's documented dev-mode defaults, but the file should state this explicitly and pull from env when present.

### Acceptance criteria
- [ ] `room = "syncle-office"` (no space).
- [ ] Keys are read from `os.environ.get("LIVEKIT_API_KEY", "devkey")` / `..._SECRET`.
- [ ] A brief comment notes that the defaults match `docker run livekit/livekit-server --dev` and must not be used in production.

### Pointers
- [generate_token.py](generate_token.py#L7-L8) — keys
- [generate_token.py](generate_token.py#L30) — room name
- [app/src/main/java/com/example/syncle/model/AuthRepository.kt](app/src/main/java/com/example/syncle/model/AuthRepository.kt#L27)

---

## 17. Sanitize `map_config.json` double-space + load fallbacks vs. asset divergence

**Labels:** `clean-code`, `data`

### Context
- `map_config.json` `"map_name": "Syncle  Office Alpha"` has a double space.
- `MainActivity` defines a hard-coded `MapConfig` fallback ([L41-L46](app/src/main/java/com/example/syncle/MainActivity.kt#L41-L46)) with a single 2000×2000 walkable area and one table. The fallback diverges from the real asset, and silently masks asset/parse failures — users see "the app works" with a completely different world. A failure to load should surface in the UI (error screen) instead.

### Acceptance criteria
- [ ] Trim/normalize the `map_name` string in the asset.
- [ ] Delete the hard-coded `MapConfig` fallback. On asset/parse failure, route through `viewModel.startupError = "..."` so the existing error screen renders.
- [ ] Existing happy-path manual run unchanged.

### Pointers
- [app/src/main/assets/map_config.json](app/src/main/assets/map_config.json#L2)
- [app/src/main/java/com/example/syncle/MainActivity.kt](app/src/main/java/com/example/syncle/MainActivity.kt#L34-L48)

---

## 18. Memoize `MeetingUi.participants` in `pushUiState`; cut allocations during meetings

**Labels:** `perf`, `compose`

### Context
`pushUiState()` is called every 50 ms (and on each event). Whenever a meeting is active, it rebuilds the participants list from scratch:

```kotlin
participants = mapCache?.let {
    meeting.buildParticipants(it, peerRegistry.snapshot(), liveKitService?.getLocalIdentity(), liveKitService?.getLocalVideoTrack())
} ?: emptyList()
```

`peerRegistry.snapshot()` allocates a new `List<RemotePeer>` every tick; `buildParticipants` then filters and maps. Even when nothing changed, a new `MeetingUi` is produced — and because `MeetingUi`/`MeetingParticipant` are `data class`-equal, downstream `LazyVerticalGrid` should be stable, but the upstream allocation pressure is real (≈ 20 K MeetingParticipant objects per minute per peer).

### Acceptance criteria
- [ ] Only rebuild `MeetingUi.participants` when (a) the active table id changed, (b) the local mic/cam flag changed, (c) the `peerRegistry` membership changed, or (d) a member's `tableMeetingId`/`status`/`videoTrack`/`isSpeaking` changed.
- [ ] Other 50 Hz ticks reuse the previously emitted list reference.
- [ ] No visible regression in the meeting overlay (speaking ring, mute icons, video tiles all still react).

### Pointers
- [app/src/main/java/com/example/syncle/model/SyncleViewModel.kt](app/src/main/java/com/example/syncle/model/SyncleViewModel.kt#L342-L370)
- [app/src/main/java/com/example/syncle/domain/TableMeetingController.kt](app/src/main/java/com/example/syncle/domain/TableMeetingController.kt#L57-L74)
- [app/src/main/java/com/example/syncle/domain/PeerRegistry.kt](app/src/main/java/com/example/syncle/domain/PeerRegistry.kt#L26)

---

## 19. `LiveKitService.disconnect` is fire-and-forget; race with reconnect

**Labels:** `bug-risk`, `clean-code`

### Context
`disconnect()` cancels `serviceScope`, then calls the **synchronous** `room?.disconnect()`. LiveKit's `Room.disconnect()` returns immediately but actually releases native peerconnections asynchronously; immediately re-allocating a new `Room` (via `connect()` again) can race the teardown and produce dangling RTC handles. Also, recreating `serviceScope` inside `disconnect()` means a `connect()` call interleaved with `disconnect()` could observe the old or the new scope.

### Acceptance criteria
- [ ] `disconnect()` is `suspend` and awaits `room?.disconnect()` completion if the SDK exposes a suspending form (LiveKit 2.x does).
- [ ] `serviceScope` is created in `connect()` (one scope per connection lifecycle) and cancelled in `disconnect()`; not re-allocated inside `disconnect()`.
- [ ] `SyncleViewModel.disconnect()` becomes a `viewModelScope.launch { ... }` that awaits service disconnect before clearing local state.

### Pointers
- [app/src/main/java/com/example/syncle/model/LiveKitService.kt](app/src/main/java/com/example/syncle/model/LiveKitService.kt#L186-L196)
- [app/src/main/java/com/example/syncle/model/SyncleViewModel.kt](app/src/main/java/com/example/syncle/model/SyncleViewModel.kt#L319-L335)

---

## 20. `SyncleViewModel` unused imports / minor cleanups

**Labels:** `cleanup`, `good-first-issue`

### Context
While touching `SyncleViewModel.kt` for the larger refactors, sweep stragglers:

- `import kotlinx.coroutines.Dispatchers` is unused.
- `import com.example.syncle.model.TablePresence` is unused (we never reference it directly in this file).
- `lastNearbyItemId` field is assigned at the bottom of `pushUiState` but the value is never read elsewhere — dead state.

### Acceptance criteria
- [ ] All three are removed.
- [ ] `./gradlew :app:compileDebugKotlin` reports no new warnings.

### Pointers
- [app/src/main/java/com/example/syncle/model/SyncleViewModel.kt](app/src/main/java/com/example/syncle/model/SyncleViewModel.kt#L16)
- [app/src/main/java/com/example/syncle/model/SyncleViewModel.kt](app/src/main/java/com/example/syncle/model/SyncleViewModel.kt#L17)
- [app/src/main/java/com/example/syncle/model/SyncleViewModel.kt](app/src/main/java/com/example/syncle/model/SyncleViewModel.kt#L46) — `lastNearbyItemId`
- [app/src/main/java/com/example/syncle/model/SyncleViewModel.kt](app/src/main/java/com/example/syncle/model/SyncleViewModel.kt#L375-L377) — final write

---

## 21. Move domain types out of `model/` package; rename for clarity

**Labels:** `refactor`, `architecture`, `discussion`

### Context
The current package split is mostly historical: `model/` holds the ViewModel, repositories, services, UI-bound state classes (`SyncleUiState`, `MeetingUi`), and pure data (`MapConfig`, `RemotePeer`, `TablePresence`). This makes "is this a UDF model or a UI model?" hard to answer at a glance — `MeetingUi`'s `participants: List<MeetingParticipant>` even forces `model/SyncleUiState.kt` to `import com.example.syncle.ui.MeetingParticipant`, a model → UI dependency that inverts the intended layering.

Suggested target shape (`com.example.syncle.<layer>`):
- `app` — `MainActivity`, app-level wiring.
- `ui` — composables, `SyncleUiState`, `MeetingUi`, `MeetingParticipant`.
- `viewmodel` — `SyncleViewModel`.
- `domain` — `PeerRegistry`, `PositionSyncEngine`, `SpatialAudioEngine`, `TableMeetingController`, `MapConfigCache`, `TablePresence`, `MapConfig`, `RemotePeer`, `AvatarState`, `UserStatus`.
- `data` (or `repo`) — `MapRepository`, `AuthRepository`, `LiveKitService`, `LiveKitConnectResult`, `LiveKitEvent`.

### Acceptance criteria
- [ ] Discussion: confirm the layering above (or propose an alternative) before code moves.
- [ ] No domain class imports from `ui.*`.
- [ ] `SyncleUiState` and friends live in `ui.state` (or similar).
- [ ] All tests compile + pass after move.

### Pointers
- [app/src/main/java/com/example/syncle/model/SyncleUiState.kt](app/src/main/java/com/example/syncle/model/SyncleUiState.kt#L3) — `import com.example.syncle.ui.MeetingParticipant`
- [app/src/main/java/com/example/syncle/domain/TableMeetingController.kt](app/src/main/java/com/example/syncle/domain/TableMeetingController.kt#L8-L10) — also imports `ui.MeetingParticipant`
- All files under [app/src/main/java/com/example/syncle/model/](app/src/main/java/com/example/syncle/model/)

---

## 22. Allow `android:allowBackup="false"` (or define a backup rules file)

**Labels:** `security`, `clean-code`

### Context
[AndroidManifest.xml](app/src/main/AndroidManifest.xml#L18) sets `android:allowBackup="true"` with no `android:fullBackupContent` rules. Anything cached on disk (future: tokens, identity) would be backed up to Google Drive / `adb backup` by default. Since the app holds LiveKit tokens and may store identity in the future, the safer default is `allowBackup="false"`, or to declare a `backup_rules.xml` that excludes auth artifacts.

### Acceptance criteria
- [ ] Either `android:allowBackup="false"` is set, or a `res/xml/backup_rules.xml` is added and referenced via `android:fullBackupContent`.
- [ ] No regression in app install/uninstall flow.

### Pointers
- [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml#L18)

---

## 23. Enable R8 / minification for `release` builds

**Labels:** `perf`, `release`, `clean-code`

### Context
`buildTypes.release { minifyEnabled false }` ships an unshrunk APK. With LiveKit + Compose this leaves a noticeably larger binary and skips R8's optimizations + obfuscation. The `proguardFiles` line is already wired; it just needs to be turned on (and a `proguard-rules.pro` smoke test).

### Acceptance criteria
- [ ] `minifyEnabled true` for `release`.
- [ ] `shrinkResources true` (after verifying it doesn't strip needed assets like `room1.jpg`).
- [ ] Add any required `-keep` rules for LiveKit / WebRTC / OkHttp in `proguard-rules.pro` (LiveKit publishes a consumer ProGuard config — verify it is consumed).
- [ ] `./gradlew :app:assembleRelease` succeeds; manual smoke test (sandbox connect + move + join table + leave) passes on the resulting APK.

### Pointers
- [app/build.gradle](app/build.gradle#L36-L41)

---

## 24. Add `compileDebugKotlin` warning-as-error toggle (optional, low priority)

**Labels:** `clean-code`, `discussion`

### Context
The codebase is small and clean; promoting `allWarningsAsErrors = true` in `kotlinOptions` would prevent regressions like the deprecated `Divider` reappearing, unused imports re-accumulating, etc. Worth doing **after** issues #5, #20 land so the build is clean to begin with.

### Acceptance criteria
- [ ] After other cleanups land, set `kotlinOptions { allWarningsAsErrors = true }` in `app/build.gradle`.
- [ ] Build remains green.

### Pointers
- [app/build.gradle](app/build.gradle#L46-L48)
