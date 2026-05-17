package com.example.gather.model

import android.content.Context
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.track.DataPublishReliability
import io.livekit.android.room.track.RemoteVideoTrack
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LiveKitService(
    private val context: Context,
    private val onDataReceived: (String, ByteArray) -> Unit,
    private val onParticipantDisconnected: (String) -> Unit,
    private val onVideoTrackSubscribed: (String, VideoTrack) -> Unit
) {
    private var room: Room? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    suspend fun connect(url: String, token: String): Boolean {
        return try {
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
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun setupRoomListener(currentRoom: Room) {
        serviceScope.launch {
            currentRoom.events.collect { event ->
                when (event) {
                    is io.livekit.android.events.RoomEvent.DataReceived -> {
                        val identity = event.participant?.identity?.value ?: ""
                        onDataReceived(identity, event.data)
                    }
                    is io.livekit.android.events.RoomEvent.ParticipantDisconnected -> {
                        val identity = event.participant.identity?.value ?: ""
                        onParticipantDisconnected(identity)
                    }
                    is io.livekit.android.events.RoomEvent.TrackSubscribed -> {
                        val track = event.track
                        if (track is RemoteVideoTrack) {
                            val identity = event.participant.identity?.value ?: ""
                            onVideoTrackSubscribed(identity, track)
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun broadcastPosition(data: ByteArray) {
        val currentRoom = room ?: return
        serviceScope.launch {
            try {
                currentRoom.localParticipant.publishData(
                    data = data,
                    reliability = DataPublishReliability.LOSSY
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateSpatialAudio(
        remotePeers: List<RemotePeer>,
        localAvatar: AvatarState,
        mapConfig: MapConfig,
        maxDistance: Float
    ) {
        val currentRoom = room ?: return
        remotePeers.forEach { peer ->
            val volume = GatherViewModel.calculateVolume(localAvatar, peer, mapConfig, maxDistance)
            val participant = currentRoom.remoteParticipants.values.find { it.identity?.value == peer.id }
            participant?.let { p ->
                // Note: Track volume setting implementation in LiveKit SDK
            }
        }
    }

    fun disconnect() {
        try {
            room?.disconnect()
            room = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
