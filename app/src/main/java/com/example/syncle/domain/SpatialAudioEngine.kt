package com.example.syncle.domain

import kotlin.math.abs

class SpatialAudioEngine(
    private val maxDistance: Float = 300f,
    private val volumeEpsilon: Float = 0.02f,
) {
    private val lastVolumeByPeerId = mutableMapOf<String, Float>()

    fun calculateVolume(
        localAvatar: AvatarState,
        peer: RemotePeer,
        mapConfig: MapConfig,
        localAcousticTableId: String?,
    ): Float {
        if (peer.isSpotlighted) return 1.0f
        if (localAvatar.status == UserStatus.QUIET_MODE || peer.status == UserStatus.QUIET_MODE) return 0.0f

        val peerTable =
            TablePresence.effectiveTableMeetingId(
                peer.tableMeetingId,
                peer.position,
                mapConfig,
            )

        if (localAcousticTableId != null) {
            return if (peerTable == localAcousticTableId) 1.0f else 0.0f
        }

        if (peerTable != null) return 0.0f

        val distance = (localAvatar.position - peer.position).getDistance()
        if (distance > maxDistance) return 0.0f
        return (1.0f - (distance / maxDistance)).coerceIn(0.0f, 1.0f)
    }

    /** @return true if volume was applied (changed beyond epsilon) */
    fun shouldApplyVolume(
        peerId: String,
        newVolume: Float,
    ): Boolean {
        val previous = lastVolumeByPeerId[peerId]
        if (previous != null && abs(previous - newVolume) < volumeEpsilon) {
            return false
        }
        lastVolumeByPeerId[peerId] = newVolume
        return true
    }

    fun clearPeer(peerId: String) {
        lastVolumeByPeerId.remove(peerId)
    }

    fun clearAll() {
        lastVolumeByPeerId.clear()
    }
}
