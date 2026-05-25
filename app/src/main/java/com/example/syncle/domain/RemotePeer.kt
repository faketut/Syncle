package com.example.syncle.domain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import io.livekit.android.room.participant.ConnectionQuality
import io.livekit.android.room.track.VideoTrack

class RemotePeer(
    val id: String,
    val name: String,
    initialPosition: Offset = Offset.Zero,
    initialColor: String = "#888888",
) {
    /** Display name (nickname). Mutable so a snapshot pre-seed can refine it later. */
    var displayName by mutableStateOf(name)

    /** Accent color from backend / LiveKit attribute (e.g. "#4F8EF7"). */
    var color by mutableStateOf(initialColor)

    // position is the "smoothed" position used for rendering
    var position by mutableStateOf(initialPosition)

    // targetPosition is the latest position received from the network
    var targetPosition by mutableStateOf(initialPosition)

    // LiveKit Video Track for this peer
    var videoTrack by mutableStateOf<VideoTrack?>(null)

    // Spotlight & Status
    var isSpotlighted by mutableStateOf(false)
    var status by mutableStateOf(UserStatus.AVAILABLE)
    var isSpeaking by mutableStateOf(false)
    var connectionQuality by mutableStateOf(ConnectionQuality.UNKNOWN)

    /** Declared via LiveKit participant attribute when in a table meeting */
    var tableMeetingId by mutableStateOf<String?>(null)

    // Sync sequence to handle out-of-order packets
    var lastSequence: Long = -1

    /**
     * Smoothly interpolates [position] towards [targetPosition].
     * [alpha] is the interpolation factor (e.g., 0.1 for slow smoothing, 0.3 for fast).
     */
    fun interpolate(alpha: Float) {
        if (position != targetPosition) {
            val delta = targetPosition - position
            if (delta.getDistance() < 1f) {
                position = targetPosition
            } else {
                position += delta * alpha
            }
        }
    }
}

enum class UserStatus {
    AVAILABLE,
    BUSY,
    QUIET_MODE,
}
