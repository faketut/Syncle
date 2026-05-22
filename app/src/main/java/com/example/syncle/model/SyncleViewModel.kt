package com.example.syncle.model

import android.content.Context
import com.example.syncle.BuildConfig
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.syncle.domain.MapConfigCache
import com.example.syncle.domain.LiveKitEvent
import com.example.syncle.domain.PeerRegistry
import com.example.syncle.domain.PositionSyncEngine
import com.example.syncle.domain.SpatialAudioEngine
import com.example.syncle.domain.SyncleLog
import com.example.syncle.domain.TableMeetingController
import com.example.syncle.model.TablePresence
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    private var cameraTrackWatchJob: Job? = null

    // Connection screen state — single source of truth; UI observes via [uiState].
    private var url = ""
    private var token = ""
    private var isAutoFetching = false
    private var startupError: String? = null
    private var lastConnectError: String? = null
    private var connectionStatus = ConnectionStatus.DISCONNECTED

    private var lastSpeakingIds: Set<String> = emptySet()

    // Bumps every time something that could change spatial-audio output happens
    // (local move, peer move, peer status/table attr, peer joined/left). The 20Hz
    // sync loop skips the spatial-audio recompute when the flag is clean so an
    // idle room costs ~nothing on the main thread.
    private var spatialAudioDirty: Boolean = true

    private fun markSpatialAudioDirty() { spatialAudioDirty = true }

    private val _uiState = MutableStateFlow(SyncleUiState())
    val uiState: StateFlow<SyncleUiState> = _uiState.asStateFlow()

    private val authRepository = AuthRepository()

    fun setMapConfig(config: MapConfig) {
        mapConfig = config
        mapCache = MapConfigCache(config)
        pushUiState(mapReady = true)
    }

    fun getMapConfig(): MapConfig? = mapConfig

    fun getLiveKitRoom(): Room? = liveKitService?.getRoom()

    fun getLocalVideoTrack(): VideoTrack? = liveKitService?.getLocalVideoTrack()

    fun setUrl(value: String) {
        if (url == value) return
        url = value
        pushUiState()
    }

    fun setToken(value: String) {
        if (token == value) return
        token = value
        pushUiState()
    }

    fun setStartupError(message: String?) {
        if (startupError == message) return
        startupError = message
        pushUiState()
    }

    fun autoFetchSandboxDetails() {
        if (isAutoFetching) return
        viewModelScope.launch {
            isAutoFetching = true
            pushUiState()
            try {
                SyncleLog.d("Starting sandbox token auto-fetch (sandboxId=${BuildConfig.LIVEKIT_SANDBOX_ID})")
                val details = authRepository.fetchSandboxConnectionDetails()
                if (details != null) {
                    url = details.serverUrl
                    token = details.token
                    startupError = null
                    SyncleLog.d("Sandbox auto-fetch success")
                } else {
                    // AuthRepository logs http status/body; surface a short actionable hint in UI.
                    startupError =
                        "Sandbox token fetch failed. Check local.properties livekit.sandbox_id, and network access."
                    SyncleLog.w("Sandbox auto-fetch returned null")
                }
            } catch (e: Exception) {
                SyncleLog.e("Sandbox auto-fetch failed", e)
                startupError = "Startup Error: ${e.message}"
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
            lastConnectError = "Map not loaded yet. Wait a moment and try again."
            connectionStatus = ConnectionStatus.ERROR
            pushUiState()
            return
        }

        connectionStatus = ConnectionStatus.CONNECTING
        lastConnectError = null
        pushUiState()

        val service = LiveKitService(context.applicationContext)
        liveKitService = service
        collectLiveKitEvents(service)

        viewModelScope.launch {
            val result = service.connect(url, token)
            if (result.success) {
                connectionStatus = ConnectionStatus.CONNECTED
                lastConnectError = null
                startLoops(cache)
                SyncleLog.d("LiveKit connected")
            } else {
                connectionStatus = ConnectionStatus.ERROR
                lastConnectError = result.errorMessage
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
                    is LiveKitEvent.ParticipantDisconnected -> onParticipantDisconnected(event.participantId)
                    is LiveKitEvent.VideoTrackSubscribed -> onVideoTrackSubscribed(event.participantId, event.track)
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
        val moved = avatarState.move(delta, cache)
        if (moved) markSpatialAudioDirty()
        if (avatarState.nearbyItemId != previousNearby) {
            pushUiState()
        }
    }

    fun joinTableMeeting(tableId: String) {
        if (meeting.join(tableId)) {
            mapCache?.invalidateProximityCache()
            markSpatialAudioDirty()
            pushUiState()
        }
    }

    fun leaveTableMeeting() {
        meeting.leave()
        mapCache?.invalidateProximityCache()
        markSpatialAudioDirty()
        pushUiState()
    }

    fun toggleMeetingMic() {
        meeting.toggleMic()
        pushUiState()
    }

    fun toggleMeetingCamera() {
        meeting.toggleCamera()
        pushUiState()
        // The local video track appears asynchronously after enabling the camera;
        // poll briefly (instead of a magic 300ms delay) so the meeting tile lights up
        // as soon as the track is available, on any device.
        cameraTrackWatchJob?.cancel()
        cameraTrackWatchJob = viewModelScope.launch {
            val initialTrack = liveKitService?.getLocalVideoTrack()
            repeat(20) { // up to ~1s total (20 × 50ms)
                delay(50)
                if (liveKitService?.getLocalVideoTrack() !== initialTrack) {
                    pushUiState()
                    return@launch
                }
            }
        }
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
                positionSync.encodeIfMoved(avatarState.position, seq)?.let { payload ->
                    liveKitService?.publishPosition(payload)
                }
                meeting.syncPresence(cache)
                if (spatialAudioDirty) {
                    spatialAudioDirty = false
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
            markSpatialAudioDirty()
        }
    }

    private fun onParticipantDisconnected(id: String) {
        spatialAudio.clearPeer(id)
        peerRegistry.remove(id)
        markSpatialAudioDirty()
        pushUiState()
    }

    private fun onVideoTrackSubscribed(id: String, track: io.livekit.android.room.track.VideoTrack) {
        peerRegistry.getOrCreate(id).videoTrack = track
        pushUiState()
    }

    private fun onActiveSpeakersChanged(speakingIds: Set<String>) {
        if (speakingIds == lastSpeakingIds) return
        lastSpeakingIds = speakingIds
        val localIdentity = liveKitService?.getLocalIdentity()
        avatarState.isSpeaking = localIdentity != null && speakingIds.contains(localIdentity)
        peerRegistry.forEach { peer ->
            val speaking = speakingIds.contains(peer.id)
            if (peer.isSpeaking != speaking) peer.isSpeaking = speaking
        }
        pushUiState()
    }

    private fun onParticipantAttributes(id: String, attributes: Map<String, String>) {
        val peer = peerRegistry.get(id) ?: return
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
        if (changed) {
            markSpatialAudioDirty()
            pushUiState()
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
        markSpatialAudioDirty()
    }

    fun disconnect() {
        syncJob?.cancel()
        lerpJob?.cancel()
        eventsJob?.cancel()
        cameraTrackWatchJob?.cancel()
        liveKitService?.disconnect()
        liveKitService = null
        peerRegistry.clear()
        spatialAudio.clearAll()
        positionSync.reset()
        meeting.reset()
        lastSpeakingIds = emptySet()
        mapCache?.invalidateProximityCache()
        connectionStatus = ConnectionStatus.DISCONNECTED
        pushUiState()
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }

    private fun pushUiState(mapReady: Boolean? = null) {
        val config = mapConfig
        val ready = mapReady ?: (config != null)
        val meetingId = meeting.activeTableMeetingId
        _uiState.value = SyncleUiState(
            mapReady = ready,
            connection = ConnectionUi(
                status = connectionStatus,
                url = url,
                token = token,
                isAutoFetching = isAutoFetching,
                startupError = startupError,
                lastConnectError = lastConnectError
            ),
            meeting = MeetingUi(
                activeTableId = meetingId,
                tableTitle = meetingId?.let { id -> config?.let { meeting.tableDisplayName(id, it) } },
                participants = mapCache?.let {
                    meeting.buildParticipants(
                        it,
                        peerRegistry.snapshot(),
                        liveKitService?.getLocalIdentity(),
                        liveKitService?.getLocalVideoTrack()
                    )
                } ?: emptyList(),
                micEnabled = meeting.meetingMicEnabled,
                cameraEnabled = meeting.meetingCameraEnabled
            ),
            localAvatar = LocalAvatarUi(
                name = avatarState.name,
                nearbyItemId = avatarState.nearbyItemId
            )
        )
    }
}
