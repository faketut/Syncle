package com.example.syncle.domain

import androidx.compose.ui.geometry.Offset

/**
 * Immutable map data plus cached nearest-table lookups (position epsilon).
 */
class MapConfigCache(
    val config: MapConfig,
    private val positionEpsilon: Float = 2f,
) {
    private var lastQueryPosition: Offset? = null
    private var lastNearestTableId: String? = null

    fun nearestTableId(
        position: Offset,
        forceRefresh: Boolean = false,
    ): String? {
        if (!forceRefresh) {
            val cachedPos = lastQueryPosition
            if (cachedPos != null && (position - cachedPos).getDistance() < positionEpsilon) {
                return lastNearestTableId
            }
        }
        val resolved = config.nearestTableId(position)
        lastQueryPosition = position
        lastNearestTableId = resolved
        return resolved
    }

    /**
     * Active meeting table wins; otherwise nearest table at [position] (cached).
     */
    fun resolveLocalAcousticTable(
        position: Offset,
        activeMeetingTableId: String?,
    ): String? {
        if (activeMeetingTableId != null) {
            lastQueryPosition = position
            lastNearestTableId = activeMeetingTableId
            return activeMeetingTableId
        }
        return nearestTableId(position)
    }

    fun invalidateProximityCache() {
        lastQueryPosition = null
        lastNearestTableId = null
    }
}
