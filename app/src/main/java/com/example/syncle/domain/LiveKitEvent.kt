package com.example.syncle.domain

import io.livekit.android.room.participant.ConnectionQuality
import io.livekit.android.room.track.VideoTrack

sealed class LiveKitEvent {
    data class DataReceived(val participantId: String, val data: ByteArray) : LiveKitEvent() {
        override fun equals(other: Any?) = other is DataReceived && participantId == other.participantId && data.contentEquals(other.data)
        override fun hashCode() = 31 * participantId.hashCode() + data.contentHashCode()
    }

    data class ParticipantDisconnected(val participantId: String) : LiveKitEvent()
    data class VideoTrackSubscribed(val participantId: String, val track: VideoTrack) : LiveKitEvent()
    data class ActiveSpeakersChanged(val speakingIds: Set<String>) : LiveKitEvent()
    data class ParticipantAttributesChanged(val participantId: String, val attributes: Map<String, String>) : LiveKitEvent()
    data class ConnectionQualityChanged(val participantId: String, val quality: ConnectionQuality) : LiveKitEvent()
}
