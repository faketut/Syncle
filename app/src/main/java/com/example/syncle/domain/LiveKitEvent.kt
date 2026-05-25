package com.example.syncle.domain

import io.livekit.android.room.participant.ConnectionQuality
import io.livekit.android.room.track.VideoTrack

sealed class LiveKitEvent {
    data class DataReceived(val participantId: String, val data: ByteArray) : LiveKitEvent() {
        override fun equals(other: Any?) = other is DataReceived && participantId == other.participantId && data.contentEquals(other.data)

        override fun hashCode() = 31 * participantId.hashCode() + data.contentHashCode()
    }

    data class ParticipantConnected(
        val participantId: String,
        val name: String?,
        val attributes: Map<String, String>,
    ) : LiveKitEvent()

    data class ParticipantDisconnected(val participantId: String) : LiveKitEvent()

    data class VideoTrackSubscribed(val participantId: String, val track: VideoTrack) : LiveKitEvent()

    /** Emitted when the LocalParticipant publishes or unpublishes its camera track. */
    object LocalVideoTrackChanged : LiveKitEvent()

    data class ActiveSpeakersChanged(val speakingIds: Set<String>) : LiveKitEvent()

    data class ParticipantAttributesChanged(val participantId: String, val attributes: Map<String, String>) : LiveKitEvent()

    data class ConnectionQualityChanged(val participantId: String, val quality: ConnectionQuality) : LiveKitEvent()

    /**
     * The Room was lost and the LiveKit SDK has stopped trying to recover.
     * The owning ViewModel should drive its own reconnect policy from here.
     */
    data class Disconnected(val reason: String?) : LiveKitEvent()

    /** SDK-driven transient reconnect started. */
    object Reconnecting : LiveKitEvent()

    /** SDK-driven transient reconnect succeeded. */
    object Reconnected : LiveKitEvent()
}
