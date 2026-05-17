package com.example.gather.model

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.DataPublishReliability
import io.livekit.android.room.track.RemoteVideoTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.*
import org.json.JSONObject

enum class ConnectionStatus {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

class RoomManager(
    val context: Context,
    val localAvatar: AvatarState,
    val mapConfig: MapConfig
) {
    val remotePeers = mutableStateListOf<RemotePeer>()
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var syncJob: Job? = null
    private var lerpJob: Job? = null
    
    // LiveKit Room
    private var room: Room? = null
    
    var connectionStatus by mutableStateOf(ConnectionStatus.DISCONNECTED)
        private set

    // Spatial Audio Settings
    private val maxDistance = 300f

    /**
     * Connect to a LiveKit room.
     */
    fun connect(url: String, token: String) {
        connectionStatus = ConnectionStatus.CONNECTING
        scope.launch {
            try {
                val currentRoom = LiveKit.create(
                    appContext = context.applicationContext,
                    options = RoomOptions(
                        adaptiveStream = true,
                        dynacast = true,
                    )
                )
                room = currentRoom

                setupRoomListener(currentRoom)
                
                currentRoom.connect(
                    url = url,
                    token = token
                )

                startLoops()
                connectionStatus = ConnectionStatus.CONNECTED
                println("LiveKit: Connected successfully to $url")
            } catch (e: Exception) {
                e.printStackTrace()
                connectionStatus = ConnectionStatus.ERROR
            }
        }
    }

    private fun setupRoomListener(currentRoom: Room) {
        scope.launch {
            currentRoom.events.collect { event ->
                when (event) {
                    is io.livekit.android.events.RoomEvent.DataReceived -> {
                        val identity = event.participant?.identity?.value ?: ""
                        onDataReceived(identity, event.data)
                    }
                    is io.livekit.android.events.RoomEvent.ParticipantDisconnected -> {
                        val identity = event.participant.identity?.value
                        remotePeers.removeAll { it.id == identity }
                    }
                    is io.livekit.android.events.RoomEvent.TrackSubscribed -> {
                        val track = event.track
                        if (track is RemoteVideoTrack) {
                            val identity = event.participant.identity?.value ?: ""
                            updatePeerVideoTrack(identity, track)
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun startLoops() {
        startSyncLoop()
        startInterpolationLoop()
    }

    fun stop() {
        syncJob?.cancel()
        lerpJob?.cancel()
        room?.disconnect()
        connectionStatus = ConnectionStatus.DISCONNECTED
    }

    private fun startSyncLoop() {
        syncJob = scope.launch {
            while (isActive) {
                broadcastPosition()
                updateSpatialAudio()
                delay(50) 
            }
        }
    }

    private fun startInterpolationLoop() {
        lerpJob = scope.launch {
            while (isActive) {
                remotePeers.forEach { it.interpolate(0.2f) }
                delay(16)
            }
        }
    }

    private fun updateSpatialAudio() {
        val currentRoom = room ?: return
        remotePeers.forEach { peer ->
            val volume = calculateVolume(localAvatar, peer, mapConfig, maxDistance)
            // Apply volume to LiveKit participant
            val participant = currentRoom.remoteParticipants.values.find { it.identity?.value == peer.id }
            participant?.let { p ->
                // Note: Track volume setting implementation
            }
        }
    }

    private fun broadcastPosition() {
        val currentRoom = room ?: return
        val positionData = JSONObject().apply {
            put("type", "position")
            put("x", localAvatar.position.x)
            put("y", localAvatar.position.y)
            put("seq", System.currentTimeMillis())
        }
        
        scope.launch {
            currentRoom.localParticipant.publishData(
                data = positionData.toString().toByteArray(),
                reliability = DataPublishReliability.LOSSY
            )
        }
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
