package com.example.gather.model

import android.content.Context
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.track.DataPublishReliability
import io.livekit.android.room.track.RemoteVideoTrack
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.*

class LiveKitService(
    private val context: Context,
    private val onDataReceived: (String, ByteArray) -> Unit,
    private val onParticipantDisconnected: (String) -> Unit,
    private val onVideoTrackSubscribed: (String, VideoTrack) -> Unit
) {
    private var room: Room? = null
    
    // 引入 SupervisorJob 与 CoroutineExceptionHandler 彻底防止底层协程崩溃导致应用闪退
    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        exception.printStackTrace()
        println("LiveKitService: Caught unhandled coroutine exception: ${exception.message}")
    }
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob() + exceptionHandler)

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
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        }
    }

    private fun setupRoomListener(currentRoom: Room) {
        serviceScope.launch {
            try {
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
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun broadcastPosition(data: ByteArray) {
        val currentRoom = room ?: return
        // 检查房间是否处于 CONNECTED 状态，防止在数据通道未就绪时频繁 publish 导致底层崩溃
        if (currentRoom.state != Room.State.CONNECTED) return

        serviceScope.launch {
            try {
                currentRoom.localParticipant.publishData(
                    data = data,
                    reliability = DataPublishReliability.LOSSY
                )
            } catch (e: Throwable) {
                // 捕获所有 Throwable 防止崩溃
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
        if (currentRoom.state != Room.State.CONNECTED) return

        try {
            remotePeers.forEach { peer ->
                val volume = GatherViewModel.calculateVolume(localAvatar, peer, mapConfig, maxDistance)
                val participant = currentRoom.remoteParticipants.values.find { it.identity?.value == peer.id }
                participant?.let { p ->
                    // Note: Track volume setting implementation in LiveKit SDK
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun disconnect() {
        try {
            serviceScope.coroutineContext.cancelChildren()
            room?.disconnect()
            room = null
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}
