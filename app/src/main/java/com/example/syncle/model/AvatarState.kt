package com.example.syncle.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

class AvatarState(
    initialPosition: Offset = Offset.Zero,
    val radius: Float = 20f
) {
    var position by mutableStateOf(initialPosition)
    var name by mutableStateOf("Me")
    var nearbyItemId by mutableStateOf<String?>(null)
    
    // Spotlight & Status
    var isSpotlighted by mutableStateOf(false)
    var status by mutableStateOf(UserStatus.AVAILABLE)

    /**
     * Attempts to move the avatar by [delta].
     * Implements sliding collision: if a diagonal move fails, it tries to move along X or Y axis independently.
     * Returns true if any movement occurred.
     */
    fun move(delta: Offset, mapConfig: MapConfig): Boolean {
        // 1. Try full movement
        if (tryMoveTo(position + delta, mapConfig)) {
            position += delta
            updateNearbyItem(mapConfig)
            return true
        }

        // 2. Try sliding - X axis only
        if (delta.x != 0f && tryMoveTo(position + Offset(delta.x, 0f), mapConfig)) {
            position += Offset(delta.x, 0f)
            updateNearbyItem(mapConfig)
            return true
        }

        // 3. Try sliding - Y axis only
        if (delta.y != 0f && tryMoveTo(position + Offset(0f, delta.y), mapConfig)) {
            position += Offset(0f, delta.y)
            updateNearbyItem(mapConfig)
            return true
        }

        return false
    }

    private fun tryMoveTo(newPosition: Offset, mapConfig: MapConfig): Boolean {
        val avatarRect = Rect(
            center = newPosition,
            radius = radius
        )

        // Must be within at least one walkable area
        val isInsideWalkable = mapConfig.walkableAreas.any { it.contains(newPosition) }
        
        // Must NOT collide with any tables
        val collidesWithTable = mapConfig.tables.any { it.rect.overlaps(avatarRect) }

        return isInsideWalkable && !collidesWithTable
    }

    private fun updateNearbyItem(mapConfig: MapConfig) {
        val interactionThreshold = 60f
        val closest = mapConfig.tables.firstOrNull { table ->
            // Simple distance check from center to rect
            val dist = (position - table.rect.center).getDistance()
            dist < interactionThreshold
        }
        nearbyItemId = closest?.id
    }
}
