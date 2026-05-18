package com.example.gather.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import io.livekit.android.room.track.VideoTrack

class RemotePeer(
    val id: String,
    val name: String,
    initialPosition: Offset = Offset.Zero
) {
    // position is the "smoothed" position used for rendering
    var position by mutableStateOf(initialPosition)
    
    // targetPosition is the latest position received from the network
    var targetPosition by mutableStateOf(initialPosition)

    // LiveKit Video Track for this peer
    var videoTrack by mutableStateOf<VideoTrack?>(null)

    // Spotlight & Status
    var isSpotlighted by mutableStateOf(false)
    var status by mutableStateOf(UserStatus.AVAILABLE)

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
    AVAILABLE, BUSY, QUIET_MODE
}
