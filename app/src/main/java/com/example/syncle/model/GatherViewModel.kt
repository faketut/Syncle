package com.example.syncle.model

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.livekit.android.room.participant.ConnectionQuality
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.*
import org.json.JSONObject

enum class ConnectionStatus {
    CONNECTING, CONNECTED, DISCONNECTED, ERROR
}

class SyncleViewModel : ViewModel() {
    val avatarState = AvatarState(initialPosition = Offset(100f, 100f))
    val remotePeers = mutableStateListOf<RemotePeer>()
    
    var connectionStatus by mutableStateOf(ConnectionStatus.DISCONNECTED)
        private set

    var url by mutableStateOf("")
    var token by mutableStateOf("")
    var isAutoFetching by mutableStateOf(false)
    var startupError by mutableStateOf<String?>(null)

    private var liveKitService: LiveKitService? = null
    private var syncJob: Job? = null
    private var lerpJob: Job? = null

    /** Active table meeting (same LiveKit room; isolated via attributes + volume) */
    var activeTableMeetingId by mutableStateOf<String?>(null)
        private set

    var meetingMicEnabled by mutableStateOf(true)
        private set

    var meetingCameraEnabled by mutableStateOf(false)
        private set

    private val maxDistance = 300f
    private val authRepository = AuthRepository()

    fun autoFetchSandboxDetails() {
        if (isAutoFetching) return
        viewModelScope.launch {
            isAutoFetching = true
            try {
                println("SyncleViewModel: Starting auto-fetch...")
                val details = authRepository.fetchSandboxConnectionDetails()
                if (details != null) {
                    url = details.serverUrl
                    token = details.token
                    println("SyncleViewModel: Auto-fetch success: $url")
                } else {
                    println("SyncleViewModel: Auto-fetch returned null (check local.properties)")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                startupError = "Startup Error: ${e.message}"
            } finally {
                isAutoFetching = false
            }
        }
    }

    fun connect(context: Context, mapConfig: MapConfig) {
        if (connectionStatus == ConnectionStatus.CONNECTING || connectionStatus == ConnectionStatus.CONNECTED) return
        connectionStatus = ConnectionStatus.CONNECTING
        
        liveKitService = LiveKitService(
            context = context,
            onDataReceived = { id, data -> onDataReceived(id, data) },
            onParticipantDisconnected = { id -> remotePeers.removeAll { it.id == id } },
            onVideoTrackSubscribed = { id, track -> updatePeerVideoTrack(id, track) },
            onActiveSpeakersChanged = { speakers -> updateActiveSpeakers(speakers) },
            onParticipantAttributesChanged = { id, attrs -> updateParticipantAttributes(id, attrs) },
            onConnectionQualityChanged = { id, quality -> updateConnectionQuality(id, quality) }
        )

        viewModelScope.launch {
            val success = liveKitService?.connect(url, token) ?: false
            if (success) {
                startLoops(mapConfig)
                connectionStatus = ConnectionStatus.CONNECTED
                println("LiveKit: Connected successfully to $url")
            } else {
                connectionStatus = ConnectionStatus.ERROR
            }
        }
    }

    private fun startLoops(mapConfig: MapConfig) {
        startSyncLoop(mapConfig)
        startInterpolationLoop()
    }

    private fun startSyncLoop(mapConfig: MapConfig) {
        syncJob = viewModelScope.launch {
            while (isActive) {
                broadcastPosition()
                syncTableMeetingPresence(mapConfig)
                liveKitService?.updateSpatialAudio(
                    remotePeers,
                    avatarState,
                    mapConfig,
                    maxDistance,
                    resolveLocalAcousticTable(mapConfig)
                )
                delay(50)
            }
        }
    }

    /** Table zone used for audio isolation (meeting UI or standing at table). */
    fun resolveLocalAcousticTable(mapConfig: MapConfig): String? {
        return activeTableMeetingId
            ?: TablePresence.nearestTableId(avatarState.position, mapConfig)
    }

    private fun syncTableMeetingPresence(mapConfig: MapConfig) {
        val active = activeTableMeetingId ?: return
        val atTable = TablePresence.nearestTableId(avatarState.position, mapConfig)
        if (atTable != active) {
            leaveTableMeeting()
        }
    }

    fun joinTableMeeting(tableId: String) {
        if (avatarState.nearbyItemId != tableId) return
        activeTableMeetingId = tableId
        meetingCameraEnabled = true
        liveKitService?.setLocalAttributes(mapOf(TablePresence.ATTR_TABLE_ID to tableId))
        liveKitService?.setCameraEnabled(true)
    }

    fun leaveTableMeeting() {
        activeTableMeetingId = null
        meetingCameraEnabled = false
        liveKitService?.setLocalAttributes(mapOf(TablePresence.ATTR_TABLE_ID to ""))
        liveKitService?.setCameraEnabled(false)
    }

    fun toggleMeetingMic() {
        meetingMicEnabled = !meetingMicEnabled
        liveKitService?.setMicrophoneEnabled(meetingMicEnabled)
    }

    fun toggleMeetingCamera() {
        meetingCameraEnabled = !meetingCameraEnabled
        liveKitService?.setCameraEnabled(meetingCameraEnabled)
    }

    fun tableMeetingPeers(mapConfig: MapConfig): List<RemotePeer> {
        val tableId = activeTableMeetingId ?: return emptyList()
        return remotePeers.filter { peer ->
            TablePresence.effectiveTableMeetingId(peer.tableMeetingId, peer.position, mapConfig) == tableId
        }
    }

    fun tableDisplayName(tableId: String, mapConfig: MapConfig): String {
        return mapConfig.tables.find { it.id == tableId }?.displayName ?: tableId
    }

    fun liveKitLocalIdentity(): String? = liveKitService?.getLocalIdentity()

    private fun startInterpolationLoop() {
        lerpJob = viewModelScope.launch {
            while (isActive) {
                remotePeers.forEach { it.interpolate(0.2f) }
                delay(16)
            }
        }
    }

    private fun broadcastPosition() {
        val service = liveKitService ?: return
        val positionData = JSONObject().apply {
            put("type", "position")
            put("x", avatarState.position.x)
            put("y", avatarState.position.y)
            put("seq", System.currentTimeMillis())
        }
        service.broadcastPosition(positionData.toString().toByteArray())
    }

    fun onDataReceived(participantId: String, data: ByteArray) {
        try {
            val json = JSONObject(String(data))
            if (json.getString("type") == "position") {
                val x = json.getDouble("x").toFloat()
                val y = json.getDouble("y").toFloat()
                val seq = json.optLong("seq", 0)
                updatePeerPosition(participantId, Offset(x, y), seq)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updatePeerPosition(id: String, position: Offset, seq: Long) {
        val peer = remotePeers.find { it.id == id }
        if (peer != null) {
            if (seq >= peer.lastSequence) {
                peer.targetPosition = position
                peer.lastSequence = seq
            }
        } else {
            val newPeer = RemotePeer(id = id, name = "Peer $id", initialPosition = position)
            newPeer.lastSequence = seq
            remotePeers.add(newPeer)
        }
    }

    private fun updatePeerVideoTrack(id: String, track: VideoTrack) {
        val peer = remotePeers.find { it.id == id }
        if (peer != null) {
            peer.videoTrack = track
        } else {
            val newPeer = RemotePeer(id = id, name = "Peer $id")
            newPeer.videoTrack = track
            remotePeers.add(newPeer)
        }
    }

    private fun updateActiveSpeakers(speakingIds: List<String>) {
        val localIdentity = liveKitService?.getLocalIdentity()
        avatarState.isSpeaking = localIdentity != null && speakingIds.contains(localIdentity)
        remotePeers.forEach { peer ->
            peer.isSpeaking = speakingIds.contains(peer.id)
        }
    }

    private fun updateParticipantAttributes(id: String, attributes: Map<String, String>) {
        val peer = remotePeers.find { it.id == id } ?: return
        val statusStr = attributes["status"]
        if (statusStr != null) {
            peer.status = try { UserStatus.valueOf(statusStr) } catch (e: Exception) { UserStatus.AVAILABLE }
        }
        if (attributes.containsKey(TablePresence.ATTR_TABLE_ID)) {
            peer.tableMeetingId = attributes[TablePresence.ATTR_TABLE_ID]?.takeIf { it.isNotEmpty() }
        }
    }

    private fun updateConnectionQuality(id: String, quality: ConnectionQuality) {
        val localIdentity = liveKitService?.getLocalIdentity()
        if (id == localIdentity) {
            avatarState.connectionQuality = quality
        } else {
            remotePeers.find { it.id == id }?.connectionQuality = quality
        }
    }

    fun setLocalStatus(newStatus: UserStatus) {
        avatarState.status = newStatus
        liveKitService?.setLocalAttributes(mapOf("status" to newStatus.name))
    }

    fun disconnect() {
        syncJob?.cancel()
        lerpJob?.cancel()
        liveKitService?.disconnect()
        liveKitService = null
        activeTableMeetingId = null
        meetingCameraEnabled = false
        connectionStatus = ConnectionStatus.DISCONNECTED
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }

    companion object {
        fun calculateVolume(
            localAvatar: AvatarState,
            peer: RemotePeer,
            mapConfig: MapConfig,
            maxDistance: Float,
            localTableMeetingId: String?
        ): Float {
            if (peer.isSpotlighted) return 1.0f
            if (localAvatar.status == UserStatus.QUIET_MODE || peer.status == UserStatus.QUIET_MODE) return 0.0f

            val peerTable = TablePresence.effectiveTableMeetingId(
                peer.tableMeetingId,
                peer.position,
                mapConfig
            )

            if (localTableMeetingId != null) {
                return if (peerTable == localTableMeetingId) 1.0f else 0.0f
            }

            if (peerTable != null) return 0.0f

            val distance = (localAvatar.position - peer.position).getDistance()
            if (distance > maxDistance) return 0.0f
            return (1.0f - (distance / maxDistance)).coerceIn(0.0f, 1.0f)
        }
    }
}
