package com.example.syncle.domain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import io.livekit.android.room.participant.ConnectionQuality

class AvatarState(
    initialPosition: Offset = Offset.Zero,
    val radius: Float = 20f,
) {
    var position by mutableStateOf(initialPosition)
    var name by mutableStateOf("Me")
    var nearbyItemId by mutableStateOf<String?>(null)

    // Spotlight & Status
    var isSpotlighted by mutableStateOf(false)
    var status by mutableStateOf(UserStatus.AVAILABLE)
    var isSpeaking by mutableStateOf(false)
    var connectionQuality by mutableStateOf(ConnectionQuality.UNKNOWN)

    /**
     * Attempts to move the avatar by [delta].
     * Implements sliding collision: if a diagonal move fails, it tries to move along X or Y axis independently.
     * Returns true if any movement occurred.
     */
    fun move(
        delta: Offset,
        mapCache: MapConfigCache,
    ): Boolean {
        val mapConfig = mapCache.config
        // 1. Try full movement
        if (tryMoveTo(position + delta, mapConfig)) {
            position += delta
            updateNearbyItem(mapCache)
            return true
        }

        // 2. Try sliding - X axis only
        if (delta.x != 0f && tryMoveTo(position + Offset(delta.x, 0f), mapConfig)) {
            position += Offset(delta.x, 0f)
            updateNearbyItem(mapCache)
            return true
        }

        // 3. Try sliding - Y axis only
        if (delta.y != 0f && tryMoveTo(position + Offset(0f, delta.y), mapConfig)) {
            position += Offset(0f, delta.y)
            updateNearbyItem(mapCache)
            return true
        }

        return false
    }

    private fun tryMoveTo(
        newPosition: Offset,
        mapConfig: MapConfig,
    ): Boolean {
        val avatarRect =
            Rect(
                left = newPosition.x - radius,
                top = newPosition.y - radius,
                right = newPosition.x + radius,
                bottom = newPosition.y + radius,
            )

        val isInsideWalkable =
            mapConfig.walkableAreas.any { walkable ->
                walkable.contains(newPosition) || walkable.overlaps(avatarRect)
            }

        val collidesWithTable =
            mapConfig.tables.any { table ->
                avatarRect.overlaps(table.rect)
            }

        return isInsideWalkable && !collidesWithTable
    }

    private fun updateNearbyItem(mapCache: MapConfigCache) {
        nearbyItemId = mapCache.nearestTableId(position)
    }
}
