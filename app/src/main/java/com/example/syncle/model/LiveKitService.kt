package com.example.syncle.model

import android.content.Context
import com.example.syncle.domain.LiveKitEvent
import com.example.syncle.domain.SpatialAudioEngine
import com.example.syncle.domain.SyncleLog
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.DataPublishReliability
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.room.track.RemoteVideoTrack
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LiveKitService(
    private val context: Context
) {
    private var room: Room? = null

    // One scope per connection lifecycle. Allocated in connect(), cancelled
    // and joined in disconnect(). Null means "no active connection".
    private var serviceScope: CoroutineScope? = null

    private val _events = MutableSharedFlow<LiveKitEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<LiveKitEvent> = _events.asSharedFlow()

    suspend fun connect(url: String, token: String): LiveKitConnectResult {
        return withContext(Dispatchers.IO) {
            val trimmedUrl = url.trim()
            val trimmedToken = token.trim()
            if (trimmedUrl.isEmpty() || trimmedToken.isEmpty()) {
                return@withContext LiveKitConnectResult(
                    success = false,
                    errorMessage = "Server URL or token is empty. Wait for sandbox fetch or paste a token."
                )
            }
            try {
                val currentRoom = LiveKit.create(
                    appContext = context.applicationContext,
                    options = RoomOptions(
                        adaptiveStream = true,
                        dynacast = true,
                    )
                )
                room = currentRoom
                val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
                serviceScope = scope
                setupRoomListener(currentRoom, scope)
                currentRoom.connect(url = trimmedUrl, token = trimmedToken)
                currentRoom.localParticipant.setMicrophoneEnabled(true)
                // Replay existing participants so peers who joined before us appear immediately,
                // even if they aren't currently moving (no position packets) and their attributes
                // were published before our connect (no "changed" event will fire for them).
                currentRoom.remoteParticipants.values.forEach { p ->
                    val identity = p.identity?.value ?: return@forEach
                    _events.tryEmit(LiveKitEvent.ParticipantConnected(identity, p.name, p.attributes))
                    // Also replay any already-subscribed video tracks. The LiveKit
                    // SDK does NOT reliably re-emit TrackSubscribed for tracks that
                    // were subscribed before our event collector started, so peers
                    // who turned on their camera before we connected would never
                    // show video otherwise.
                    p.trackPublications.values.forEach { pub ->
                        val track = pub.track
                        if (track is RemoteVideoTrack) {
                            SyncleLog.d("Replay subscribed video track id=$identity sid=${pub.sid}")
                            _events.tryEmit(LiveKitEvent.VideoTrackSubscribed(identity, track))
                        }
                    }
                }
                LiveKitConnectResult(success = true)
            } catch (e: Exception) {
                SyncleLog.e("LiveKit connect failed (url=$trimmedUrl)", e)
                LiveKitConnectResult(
                    success = false,
                    errorMessage = e.message ?: e.javaClass.simpleName
                )
            }
        }
    }

    private fun setupRoomListener(currentRoom: Room, scope: CoroutineScope) {
        scope.launch {
            currentRoom.events.collect { event ->
                when (event) {
                    is io.livekit.android.events.RoomEvent.DataReceived -> {
                        val identity = event.participant?.identity?.value ?: return@collect
                        _events.tryEmit(LiveKitEvent.DataReceived(identity, event.data))
                    }
                    is io.livekit.android.events.RoomEvent.ParticipantConnected -> {
                        val identity = event.participant.identity?.value ?: return@collect
                        _events.tryEmit(
                            LiveKitEvent.ParticipantConnected(
                                identity,
                                event.participant.name,
                                event.participant.attributes
                            )
                        )
                    }
                    is io.livekit.android.events.RoomEvent.ParticipantDisconnected -> {
                        val identity = event.participant.identity?.value ?: return@collect
                        _events.tryEmit(LiveKitEvent.ParticipantDisconnected(identity))
                    }
                    is io.livekit.android.events.RoomEvent.TrackSubscribed -> {
                        val track = event.track
                        val identity = event.participant.identity?.value
                        if (track is RemoteVideoTrack && identity != null) {
                            _events.tryEmit(LiveKitEvent.VideoTrackSubscribed(identity, track))
                        }
                    }
                    is io.livekit.android.events.RoomEvent.TrackSubscriptionFailed -> {
                        val identity = event.participant.identity?.value
                        SyncleLog.w("TrackSubscriptionFailed id=$identity sid=${event.sid} reason=${event.exception.message}")
                    }
                    is io.livekit.android.events.RoomEvent.ActiveSpeakersChanged -> {
                        val speakingIds = event.speakers.mapNotNull { it.identity?.value }.toSet()
                        _events.tryEmit(LiveKitEvent.ActiveSpeakersChanged(speakingIds))
                    }
                    is io.livekit.android.events.RoomEvent.ParticipantAttributesChanged -> {
                        val identity = event.participant.identity?.value ?: return@collect
                        _events.tryEmit(
                            LiveKitEvent.ParticipantAttributesChanged(identity, event.changedAttributes)
                        )
                    }
                    is io.livekit.android.events.RoomEvent.ConnectionQualityChanged -> {
                        val identity = event.participant.identity?.value ?: return@collect
                        _events.tryEmit(LiveKitEvent.ConnectionQualityChanged(identity, event.quality))
                    }
                    else -> {}
                }
            }
        }
    }

    suspend fun publishPosition(data: ByteArray) {
        val currentRoom = room ?: return
        withContext(Dispatchers.IO) {
            try {
                currentRoom.localParticipant.publishData(
                    data = data,
                    reliability = DataPublishReliability.LOSSY
                )
            } catch (e: Exception) {
                SyncleLog.e("publishPosition failed", e)
            }
        }
    }

    fun updateSpatialAudio(
        remotePeers: List<RemotePeer>,
        localAvatar: AvatarState,
        mapConfig: MapConfig,
        localAcousticTableId: String?,
        audioEngine: SpatialAudioEngine
    ) {
        val currentRoom = room ?: return
        val participantByIdentity = currentRoom.remoteParticipants.values.associateBy { it.identity?.value }

        remotePeers.forEach { peer ->
            val volume = audioEngine.calculateVolume(
                localAvatar,
                peer,
                mapConfig,
                localAcousticTableId
            )
            if (!audioEngine.shouldApplyVolume(peer.id, volume)) return@forEach

            val participant = participantByIdentity[peer.id] ?: return@forEach
            applyVolume(participant, volume)
        }
    }

    private fun applyVolume(participant: Participant, volume: Float) {
        participant.trackPublications.values.forEach { publication ->
            val audioTrack = publication.track as? RemoteAudioTrack ?: return@forEach
            audioTrack.setVolume(volume.toDouble())
        }
    }

    fun getRoom(): Room? = room

    fun getLocalIdentity(): String? = room?.localParticipant?.identity?.value

    fun getLocalVideoTrack(): VideoTrack? {
        val participant = room?.localParticipant ?: return null
        return participant.trackPublications.values.firstNotNullOfOrNull { publication ->
            publication.track as? VideoTrack
        }
    }

    fun setLocalAttributes(attributes: Map<String, String>) {
        room?.localParticipant?.updateAttributes(attributes)
    }

    fun setCameraEnabled(enabled: Boolean) {
        val scope = serviceScope ?: return
        scope.launch(Dispatchers.IO) {
            try {
                room?.localParticipant?.setCameraEnabled(enabled)
            } catch (e: Exception) {
                SyncleLog.e("setCameraEnabled failed", e)
            }
        }
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        val scope = serviceScope ?: return
        scope.launch(Dispatchers.IO) {
            try {
                room?.localParticipant?.setMicrophoneEnabled(enabled)
            } catch (e: Exception) {
                SyncleLog.e("setMicrophoneEnabled failed", e)
            }
        }
    }

    suspend fun disconnect() {
        val scope = serviceScope
        val currentRoom = room
        serviceScope = null
        room = null
        // Cancel the event collector first, then await its teardown so a
        // subsequent connect() can't observe an event from the previous Room.
        scope?.coroutineContext?.get(Job)?.cancelAndJoin()
        // Room.disconnect() in livekit-android 2.x asynchronously releases
        // native PeerConnections; in older versions it returned immediately
        // and the next LiveKit.create() could race the teardown. We call it
        // on Dispatchers.IO and treat it as best-effort — if the SDK ever
        // promotes this to a true suspending API, switching the call site is
        // a one-liner.
        if (currentRoom != null) {
            try {
                withContext(Dispatchers.IO) { currentRoom.disconnect() }
            } catch (e: Exception) {
                SyncleLog.e("room.disconnect failed", e)
            }
        }
    }
}
