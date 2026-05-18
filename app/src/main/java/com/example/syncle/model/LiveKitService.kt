package com.example.syncle.model

import android.content.Context
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.ConnectionQuality
import io.livekit.android.room.track.DataPublishReliability
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.room.track.RemoteVideoTrack
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LiveKitService(
    private val context: Context,
    private val onDataReceived: (String, ByteArray) -> Unit,
    private val onParticipantDisconnected: (String) -> Unit,
    private val onVideoTrackSubscribed: (String, VideoTrack) -> Unit,
    private val onActiveSpeakersChanged: (List<String>) -> Unit,
    private val onParticipantAttributesChanged: (String, Map<String, String>) -> Unit,
    private val onConnectionQualityChanged: (String, ConnectionQuality) -> Unit
) {
    private var room: Room? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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
            currentRoom.localParticipant.setMicrophoneEnabled(true)
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
                        val identity = event.participant?.identity?.value ?: return@collect
                        onDataReceived(identity, event.data)
                    }
                    is io.livekit.android.events.RoomEvent.ParticipantDisconnected -> {
                        val identity = event.participant.identity?.value ?: return@collect
                        onParticipantDisconnected(identity)
                    }
                    is io.livekit.android.events.RoomEvent.TrackSubscribed -> {
                        val track = event.track
                        if (track is RemoteVideoTrack) {
                            val identity = event.participant.identity?.value ?: return@collect
                            onVideoTrackSubscribed(identity, track)
                        }
                    }
                    is io.livekit.android.events.RoomEvent.ActiveSpeakersChanged -> {
                        val speakingIds = event.speakers.mapNotNull { it.identity?.value }
                        onActiveSpeakersChanged(speakingIds)
                    }
                    is io.livekit.android.events.RoomEvent.ParticipantAttributesChanged -> {
                        val identity = event.participant.identity?.value ?: return@collect
                        onParticipantAttributesChanged(identity, event.attributes)
                    }
                    is io.livekit.android.events.RoomEvent.ConnectionQualityChanged -> {
                        val identity = event.participant.identity?.value ?: return@collect
                        onConnectionQualityChanged(identity, event.quality)
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
        maxDistance: Float,
        localTableMeetingId: String?
    ) {
        val currentRoom = room ?: return
        remotePeers.forEach { peer ->
            val volume = SyncleViewModel.calculateVolume(
                localAvatar,
                peer,
                mapConfig,
                maxDistance,
                localTableMeetingId
            )
            val participant = currentRoom.remoteParticipants.values.find { it.identity?.value == peer.id }
            participant?.let { p ->
                p.audioTracks.values.forEach { publication ->
                    val audioTrack = publication.track as? RemoteAudioTrack
                    audioTrack?.setVolume(volume.toDouble())
                }
            }
        }
    }

    fun getLocalIdentity(): String? {
        return room?.localParticipant?.identity?.value
    }

    fun setLocalAttributes(attributes: Map<String, String>) {
        room?.localParticipant?.setAttributes(attributes)
    }

    fun setCameraEnabled(enabled: Boolean) {
        serviceScope.launch {
            try {
                room?.localParticipant?.setCameraEnabled(enabled)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        serviceScope.launch {
            try {
                room?.localParticipant?.setMicrophoneEnabled(enabled)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun disconnect() {
        try {
            serviceScope.cancel()
            room?.disconnect()
            room = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
