package com.example.gather.model

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.*
import org.json.JSONObject

class GatherViewModel : ViewModel() {
    val avatarState = AvatarState(initialPosition = Offset(100f, 100f))
    val remotePeers = mutableStateListOf<RemotePeer>()
    
    var connectionStatus by mutableStateOf(ConnectionStatus.DISCONNECTED)
        private set

    var offlineMode by mutableStateOf(false)
    var url by mutableStateOf("")
    var token by mutableStateOf("")
    var isAutoFetching by mutableStateOf(false)
    var startupError by mutableStateOf<String?>(null)

    private var liveKitService: LiveKitService? = null
    private var syncJob: Job? = null
    private var lerpJob: Job? = null

    private val maxDistance = 300f
    private val authRepository = AuthRepository()

    fun autoFetchSandboxDetails() {
        if (url.isNotEmpty() || token.isNotEmpty() || isAutoFetching) return
        viewModelScope.launch {
            isAutoFetching = true
            try {
                println("GatherViewModel: Starting auto-fetch...")
                val details = authRepository.fetchSandboxConnectionDetails()
                if (details != null) {
                    url = details.serverUrl
                    token = details.token
                    println("GatherViewModel: Auto-fetch success: $url")
                } else {
                    println("GatherViewModel: Auto-fetch returned null (check local.properties)")
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
            onVideoTrackSubscribed = { id, track -> updatePeerVideoTrack(id, track) }
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
                liveKitService?.updateSpatialAudio(remotePeers, avatarState, mapConfig, maxDistance)
                delay(50)
            }
        }
    }

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

    fun disconnect() {
        syncJob?.cancel()
        lerpJob?.cancel()
        liveKitService?.disconnect()
        liveKitService = null
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
            maxDistance: Float
        ): Float {
            if (peer.isSpotlighted) return 1.0f
            if (localAvatar.status == UserStatus.QUIET_MODE || peer.status == UserStatus.QUIET_MODE) return 0.0f

            val localPos = localAvatar.position
            val localArea = mapConfig.privateAreas.find { it.rect.contains(localPos) }
            val peerPos = peer.position
            val peerArea = mapConfig.privateAreas.find { it.rect.contains(peerPos) }

            return when {
                localArea != null && localArea == peerArea -> 1.0f
                localArea != peerArea && (localArea != null || peerArea != null) -> 0.0f
                else -> {
                    val distance = (localPos - peerPos).getDistance()
                    if (distance > maxDistance) 0.0f
                    else (1.0f - (distance / maxDistance)).coerceIn(0.0f, 1.0f)
                }
            }
        }
    }
}
