package com.example.syncle.model

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.syncle.data.DeviceIdStore
import com.example.syncle.data.Profile
import com.example.syncle.data.ProfileStore
import com.example.syncle.data.RoomStateReporter
import com.example.syncle.data.SnapshotApi
import com.example.syncle.domain.MapConfigCache
import com.example.syncle.domain.LiveKitEvent
import com.example.syncle.domain.PeerRegistry
import com.example.syncle.domain.PositionSyncEngine
import com.example.syncle.domain.SpatialAudioEngine
import com.example.syncle.domain.SyncleLog
import com.example.syncle.domain.TableMeetingController
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SyncleViewModel : ViewModel() {
    val avatarState = AvatarState(initialPosition = Offset(100f, 100f))

    private val peerRegistry = PeerRegistry()
    val remotePeers = peerRegistry.observablePeers

    private val positionSync = PositionSyncEngine()
    private val spatialAudio = SpatialAudioEngine()
    private val meeting = TableMeetingController(avatarState) { liveKitService }

    private var liveKitService: LiveKitService? = null
    private var mapConfig: MapConfig? = null
    private var mapCache: MapConfigCache? = null
    private var syncJob: Job? = null
    private var lerpJob: Job? = null
    private var eventsJob: Job? = null
    private var derivedUiJob: Job? = null

    private var urlInternal = ""
    private var tokenInternal = ""
    private var isAutoFetchingInternal = false
    private var startupErrorInternal: String? = null
    private var lastConnectErrorInternal: String? = null
    private var connectionStatusInternal = ConnectionStatus.DISCONNECTED

    private var lastSpeakingIds: Set<String> = emptySet()

    // Set whenever local position or any input that feeds syncPresence /
    // updateSpatialAudio changes. The 20 Hz sync loop reads-and-clears this
    // and short-circuits the expensive per-peer work when the world has not
    // moved — saving CPU + bandwidth when everyone is parked. Initialized
    // true so the very first tick after connect publishes a baseline.
    private val spatialDirty = java.util.concurrent.atomic.AtomicBoolean(true)

    // Memoization for MeetingUi.participants. pushUiState() fires on every
    // 50 ms sync tick during a meeting; rebuilding the participants list
    // each time allocated ~20k MeetingParticipant per minute per peer for
    // zero visible change. Reuse the prior list reference when none of the
    // inputs feeding into buildMeetingParticipants have changed.
    private var participantsCacheKey: ParticipantsKey? = null
    private var participantsCache: List<com.example.syncle.ui.MeetingParticipant> = emptyList()

    private val _uiState = MutableStateFlow(SyncleUiState())
    val uiState: StateFlow<SyncleUiState> = _uiState.asStateFlow()

    private val authRepository = AuthRepository()
    private val snapshotApi = SnapshotApi()
    private val stateReporter = RoomStateReporter()
    private var sessionUserId: String? = null
    private var sessionProfile: Profile? = null
    private var sessionRoom: String = "syncle-office"
    private var reporterJob: Job? = null

    init {
        // Reactively rebuild UI whenever the peer registry or the active
        // table id changes. This is a safety net so that even if a future
        // code path forgets to call pushUiState(), the meeting room
        // participant list (which is derived from peerRegistry.snapshot()
        // and meeting.activeTableMeetingId) still stays in sync.
        derivedUiJob = viewModelScope.launch {
            combine(peerRegistry.revision, meeting.activeTableIdFlow) { _, _ -> Unit }
                .collect { pushUiState() }
        }
    }

    fun setMapConfig(config: MapConfig) {
        mapConfig = config
        mapCache = MapConfigCache(config)
        pushUiState(mapReady = true)
    }

    fun getMapConfig(): MapConfig? = mapConfig

    fun getLiveKitRoom(): Room? = liveKitService?.getRoom()

    fun getLocalVideoTrack(): VideoTrack? = liveKitService?.getLocalVideoTrack()

    // Connection screen bindings
    var url: String
        get() = urlInternal
        set(value) {
            urlInternal = value
            pushUiState()
        }
    var token: String
        get() = tokenInternal
        set(value) {
            tokenInternal = value
            pushUiState()
        }
    var isAutoFetching: Boolean
        get() = isAutoFetchingInternal
        private set(value) {
            isAutoFetchingInternal = value
            pushUiState()
        }
    var startupError: String?
        get() = startupErrorInternal
        private set(value) {
            startupErrorInternal = value
            pushUiState()
        }

    fun reportStartupError(message: String) {
        startupError = message
    }
    var connectionStatus: ConnectionStatus
        get() = connectionStatusInternal
        private set(value) {
            connectionStatusInternal = value
            pushUiState()
        }

    fun autoFetchSession(context: Context) {
        if (isAutoFetching) return
        viewModelScope.launch {
            isAutoFetching = true
            try {
                val deviceId = DeviceIdStore(context).getOrCreate()
                val profile = ProfileStore(context).get()
                avatarState.name = profile.nickname
                sessionProfile = profile
                SyncleLog.d("Fetching session: deviceId=$deviceId nick=${profile.nickname}")
                val details = authRepository.fetchSession(
                    deviceId = deviceId,
                    nickname = profile.nickname,
                    color = profile.color,
                    room = sessionRoom,
                )
                if (details != null) {
                    urlInternal = details.serverUrl
                    tokenInternal = details.token
                    sessionUserId = details.userId
                    startupErrorInternal = null
                    SyncleLog.d("Session fetch success userId=${details.userId}")
                } else {
                    startupErrorInternal =
                        "Backend session fetch failed. Check syncle.backend_url in local.properties and that the server is reachable."
                    SyncleLog.w("Session fetch returned null")
                }
            } catch (e: Exception) {
                SyncleLog.e("Session fetch failed", e)
                startupErrorInternal = "Startup Error: ${e.message}"
            } finally {
                isAutoFetching = false
                pushUiState()
            }
        }
    }

    fun connect(context: Context) {
        if (connectionStatus == ConnectionStatus.CONNECTING || connectionStatus == ConnectionStatus.CONNECTED) return
        val cache = mapCache
        if (cache == null) {
            lastConnectErrorInternal = "Map not loaded yet. Wait a moment and try again."
            connectionStatus = ConnectionStatus.ERROR
            pushUiState()
            return
        }

        connectionStatus = ConnectionStatus.CONNECTING
        lastConnectErrorInternal = null

        val service = LiveKitService(context.applicationContext)
        liveKitService = service
        collectLiveKitEvents(service)

        viewModelScope.launch {
            val result = service.connect(urlInternal, tokenInternal)
            if (result.success) {
                connectionStatus = ConnectionStatus.CONNECTED
                lastConnectErrorInternal = null
                publishLocalProfileAttribute()
                seedFromSnapshot()
                startLoops(cache)
                startStateReporter()
                SyncleLog.d("LiveKit connected")
            } else {
                connectionStatus = ConnectionStatus.ERROR
                lastConnectErrorInternal = result.errorMessage
                SyncleLog.w("LiveKit connect failed: ${result.errorMessage}")
            }
            pushUiState()
        }
    }

    private fun collectLiveKitEvents(service: LiveKitService) {
        eventsJob?.cancel()
        eventsJob = viewModelScope.launch {
            service.events.collect { event ->
                when (event) {
                    is LiveKitEvent.DataReceived -> onDataReceived(event.participantId, event.data)
                    is LiveKitEvent.ParticipantConnected -> onParticipantConnected(event.participantId, event.name, event.attributes)
                    is LiveKitEvent.ParticipantDisconnected -> onParticipantDisconnected(event.participantId)
                    is LiveKitEvent.VideoTrackSubscribed -> onVideoTrackSubscribed(event.participantId, event.track)
                    is LiveKitEvent.LocalVideoTrackChanged -> pushUiState()
                    is LiveKitEvent.ActiveSpeakersChanged -> onActiveSpeakersChanged(event.speakingIds)
                    is LiveKitEvent.ParticipantAttributesChanged -> onParticipantAttributes(event.participantId, event.attributes)
                    is LiveKitEvent.ConnectionQualityChanged -> onConnectionQualityChanged(event.participantId, event.quality)
                }
            }
        }
    }

    fun onMove(delta: Offset) {
        val cache = mapCache ?: return
        val previousNearby = avatarState.nearbyItemId
        avatarState.move(delta, cache)
        spatialDirty.set(true)
        if (avatarState.nearbyItemId != previousNearby) {
            pushUiState()
        }
    }

    fun joinTableMeeting(tableId: String) {
        if (meeting.join(tableId)) {
            mapCache?.invalidateProximityCache()
            spatialDirty.set(true)
            pushUiState()
        }
    }

    fun leaveTableMeeting() {
        meeting.leave()
        mapCache?.invalidateProximityCache()
        spatialDirty.set(true)
        pushUiState()
    }

    fun toggleMeetingMic() {
        meeting.toggleMic()
        pushUiState()
    }

    fun toggleMeetingCamera() {
        meeting.toggleCamera()
        pushUiState()
        // No need to schedule a delayed re-push for the local video tile: when
        // the camera track actually publishes (or unpublishes), the SDK fires
        // LocalTrackPublished / LocalTrackUnpublished and we re-push from
        // collectLiveKitEvents().
    }

    private fun startLoops(cache: MapConfigCache) {
        startSyncLoop(cache)
        startInterpolationLoop()
    }

    private fun startSyncLoop(cache: MapConfigCache) {
        val mapConfig = cache.config
        syncJob = viewModelScope.launch {
            while (isActive) {
                val seq = positionSync.nextSequence()
                val payload = positionSync.encodeIfMoved(avatarState.position, seq)
                if (payload != null) {
                    liveKitService?.publishPosition(payload)
                }
                // syncPresence + updateSpatialAudio iterate every remote
                // participant and allocate a participantByIdentity map; skip
                // when neither the local avatar nor any remote target has
                // moved since the last tick. encodeIfMoved already short-
                // circuits its own publish, so this just guards the heavy
                // per-peer work.
                if (spatialDirty.getAndSet(false)) {
                    meeting.syncPresence(cache)
                    val acousticTable = cache.resolveLocalAcousticTable(
                        avatarState.position,
                        meeting.activeTableMeetingId
                    )
                    liveKitService?.updateSpatialAudio(
                        peerRegistry.snapshot(),
                        avatarState,
                        mapConfig,
                        acousticTable,
                        spatialAudio
                    )
                }
                delay(50)
            }
        }
    }

    private fun startInterpolationLoop() {
        lerpJob = viewModelScope.launch {
            while (isActive) {
                var anyMoving = false
                peerRegistry.forEach { peer ->
                    if (peer.position != peer.targetPosition) {
                        peer.interpolate(0.2f)
                        anyMoving = true
                    }
                }
                // Peer.position drives spatial volume; keep the sync loop
                // doing work while any peer is animating toward its target.
                if (anyMoving) spatialDirty.set(true)
                delay(if (anyMoving) 16 else 50)
            }
        }
    }

    private fun onDataReceived(participantId: String, data: ByteArray) {
        val packet = positionSync.decode(data) ?: return
        updatePeerPosition(participantId, Offset(packet.x, packet.y), packet.seq)
    }

    private fun updatePeerPosition(id: String, position: Offset, seq: Long) {
        val peer = peerRegistry.getOrCreate(id, position)
        if (seq >= peer.lastSequence) {
            peer.targetPosition = position
            peer.lastSequence = seq
            spatialDirty.set(true)
        }
    }

    private fun onParticipantConnected(id: String, name: String?, attributes: Map<String, String>) {
        // Register the peer immediately so they're visible even before they move
        // (no position packets) or change any attribute (initial attrs are already published).
        val peer = peerRegistry.getOrCreate(id)
        if (!name.isNullOrBlank() && peer.displayName != name) {
            peer.displayName = name
            peerRegistry.markChanged()
        }
        onParticipantAttributes(id, attributes)
        // Snapshot may have new info (tableId, position, color) that the LiveKit
        // attribute events haven't delivered yet. Pull it now so a late joiner
        // shows up in the existing user's UI as soon as they post their first state.
        seedFromSnapshot()
        pushUiState()
    }

    private fun onParticipantDisconnected(id: String) {
        spatialAudio.clearPeer(id)
        peerRegistry.remove(id)
        pushUiState()
    }

    private fun onVideoTrackSubscribed(id: String, track: io.livekit.android.room.track.VideoTrack) {
        peerRegistry.getOrCreate(id).videoTrack = track
        // Bump registry revision so pushUiState()'s participant cache key
        // changes; otherwise a new remote video tile won't appear until some
        // other peer field also changes.
        peerRegistry.markChanged()
        pushUiState()
    }

    private fun onActiveSpeakersChanged(speakingIds: Set<String>) {
        if (speakingIds == lastSpeakingIds) return
        lastSpeakingIds = speakingIds
        val localIdentity = liveKitService?.getLocalIdentity()
        avatarState.isSpeaking = localIdentity != null && speakingIds.contains(localIdentity)
        var anyPeerChanged = false
        peerRegistry.forEach { peer ->
            val speaking = speakingIds.contains(peer.id)
            if (peer.isSpeaking != speaking) {
                peer.isSpeaking = speaking
                anyPeerChanged = true
            }
        }
        if (anyPeerChanged) peerRegistry.markChanged()
        pushUiState()
    }

    private fun onParticipantAttributes(id: String, attributes: Map<String, String>) {
        // Use getOrCreate so that an attribute event arriving before any position/connect
        // event still materializes the peer.
        val peer = peerRegistry.getOrCreate(id)

        var changed = false
        val statusStr = attributes["status"]
        if (statusStr != null) {
            val status = try {
                UserStatus.valueOf(statusStr)
            } catch (_: Exception) {
                UserStatus.AVAILABLE
            }
            if (peer.status != status) {
                peer.status = status
                changed = true
            }
        }
        if (attributes.containsKey(TablePresence.ATTR_TABLE_ID)) {
            val tableId = attributes[TablePresence.ATTR_TABLE_ID]?.takeIf { it.isNotEmpty() }
            if (peer.tableMeetingId != tableId) {
                peer.tableMeetingId = tableId
                changed = true
            }
        }
        attributes[ATTR_COLOR]?.takeIf { it.isNotEmpty() }?.let { newColor ->
            if (peer.color != newColor) {
                peer.color = newColor
                changed = true
            }
        }
        if (changed) {
            peerRegistry.markChanged()
            pushUiState()
        }
    }

    private fun publishLocalProfileAttribute() {
        val profile = sessionProfile ?: return
        liveKitService?.setLocalAttributes(mapOf(ATTR_COLOR to profile.color))
    }

    private fun seedFromSnapshot() {
        val selfId = sessionUserId
        viewModelScope.launch {
            val peers = snapshotApi.fetch(sessionRoom)
            var changed = false
            peers.asSequence()
                .filter { it.userId != selfId }
                .forEach { snap ->
                    val pos = Offset(snap.x, snap.y)
                    // For new peers, seed position from snapshot. For peers we
                    // already track, do NOT overwrite position once LiveKit
                    // data packets have started arriving (lastSequence >= 0) —
                    // otherwise snapshot clobbers in-flight interpolation and
                    // causes visible jitter / jumps.
                    val isNew = peerRegistry.get(snap.userId) == null
                    val peer = peerRegistry.getOrCreate(snap.userId, pos)
                    if (peer.displayName != snap.nickname) {
                        peer.displayName = snap.nickname
                        changed = true
                    }
                    if (peer.color != snap.color) {
                        peer.color = snap.color
                        changed = true
                    }
                    if (peer.tableMeetingId != snap.tableId) {
                        peer.tableMeetingId = snap.tableId
                        changed = true
                    }
                    if (isNew || peer.lastSequence < 0) {
                        peer.targetPosition = pos
                        peer.position = pos
                        changed = true
                    }
                }
            if (changed) {
                peerRegistry.markChanged()
                pushUiState()
            }
            SyncleLog.d("Snapshot seeded peers=${peers.size} changed=$changed")
        }
    }

    private fun startStateReporter() {
        reporterJob?.cancel()
        val userId = sessionUserId ?: return
        val token = tokenInternal
        if (token.isEmpty()) return
        reporterJob = viewModelScope.launch {
            while (isActive) {
                stateReporter.report(
                    room = sessionRoom,
                    userId = userId,
                    token = token,
                    tableId = meeting.activeTableMeetingId,
                    position = avatarState.position,
                )
                // Re-pull the room snapshot from the backend so peers' tableId,
                // position, nickname and color stay fresh even if LiveKit
                // attribute / data events are delayed or dropped. Backend is the
                // source of truth.
                seedFromSnapshot()
                delay(REPORTER_INTERVAL_MS)
            }
        }
    }

    private fun onConnectionQualityChanged(id: String, quality: io.livekit.android.room.participant.ConnectionQuality) {
        val localIdentity = liveKitService?.getLocalIdentity()
        if (id == localIdentity) {
            if (avatarState.connectionQuality != quality) {
                avatarState.connectionQuality = quality
            }
        } else {
            peerRegistry.get(id)?.let { peer ->
                if (peer.connectionQuality != quality) peer.connectionQuality = quality
            }
        }
    }

    fun setLocalStatus(newStatus: UserStatus) {
        avatarState.status = newStatus
        liveKitService?.setLocalAttributes(mapOf("status" to newStatus.name))
    }

    fun disconnect() {
        syncJob?.cancel()
        lerpJob?.cancel()
        eventsJob?.cancel()
        reporterJob?.cancel()
        val service = liveKitService
        liveKitService = null
        // Capture state under viewModelScope confinement so a concurrent
        // connect() can't observe half-cleared state.
        val userId = sessionUserId
        val token = tokenInternal
        val tableId = meeting.activeTableMeetingId
        val pos = avatarState.position
        viewModelScope.launch {
            // Best-effort final state push so late joiners see our last
            // position even after we leave, until the 60s freshness window
            // expires.
            if (userId != null && token.isNotEmpty()) {
                try {
                    stateReporter.report(sessionRoom, userId, token, tableId, pos)
                } catch (e: Exception) {
                    SyncleLog.w("Final state report failed: ${e.message}")
                }
            }
            // Await full SDK teardown before clearing local state so the next
            // connect() can't race against dangling native handles.
            service?.disconnect()
            peerRegistry.clear()
            spatialAudio.clearAll()
            positionSync.reset()
            meeting.reset()
            lastSpeakingIds = emptySet()
            mapCache?.invalidateProximityCache()
            connectionStatus = ConnectionStatus.DISCONNECTED
            pushUiState()
        }
    }

    override fun onCleared() {
        super.onCleared()
        // viewModelScope is cancelled as part of onCleared, so launching the
        // suspending disconnect() there would be a no-op. Use a detached
        // scope on Dispatchers.IO so the SDK still tears down cleanly when
        // the process is leaving the ViewModel behind.
        val service = liveKitService
        liveKitService = null
        if (service != null) {
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try {
                    service.disconnect()
                } catch (_: Exception) {
                    // best-effort
                }
            }
        }
    }

    private fun pushUiState(mapReady: Boolean? = null) {
        val config = mapConfig
        val ready = mapReady ?: (config != null)
        val meetingId = meeting.activeTableMeetingId
        val cache = mapCache
        val localIdentity = liveKitService?.getLocalIdentity()
        val localVideo = liveKitService?.getLocalVideoTrack()
        val participants = if (cache == null || meetingId == null) {
            participantsCacheKey = null
            participantsCache = emptyList()
            emptyList()
        } else {
            val key = ParticipantsKey(
                meetingId = meetingId,
                micEnabled = meeting.meetingMicEnabled,
                cameraEnabled = meeting.meetingCameraEnabled,
                localIdentity = localIdentity,
                localVideoTrack = localVideo,
                peerRevision = peerRegistry.revision.value,
                localName = avatarState.name,
                localSpeaking = avatarState.isSpeaking,
            )
            if (key == participantsCacheKey) {
                participantsCache
            } else {
                val fresh = meeting.buildParticipants(
                    cache,
                    peerRegistry.snapshot(),
                    localIdentity,
                    localVideo
                )
                participantsCacheKey = key
                participantsCache = fresh
                fresh
            }
        }
        _uiState.value = SyncleUiState(
            mapReady = ready,
            connection = ConnectionUi(
                status = connectionStatusInternal,
                url = urlInternal,
                token = tokenInternal,
                isAutoFetching = isAutoFetchingInternal,
                startupError = startupErrorInternal,
                lastConnectError = lastConnectErrorInternal
            ),
            meeting = MeetingUi(
                activeTableId = meetingId,
                tableTitle = meetingId?.let { id -> config?.let { meeting.tableDisplayName(id, it) } },
                participants = participants,
                micEnabled = meeting.meetingMicEnabled,
                cameraEnabled = meeting.meetingCameraEnabled
            ),
            localAvatar = LocalAvatarUi(
                name = avatarState.name,
                nearbyItemId = avatarState.nearbyItemId
            )
        )
    }

    private companion object {
        const val ATTR_COLOR = "color"
        const val REPORTER_INTERVAL_MS = 10_000L
    }
}

private data class ParticipantsKey(
    val meetingId: String,
    val micEnabled: Boolean,
    val cameraEnabled: Boolean,
    val localIdentity: String?,
    val localVideoTrack: io.livekit.android.room.track.VideoTrack?,
    val peerRevision: Long,
    val localName: String,
    val localSpeaking: Boolean,
)
