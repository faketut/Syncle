package com.example.syncle.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

object TablePresence {
    const val ATTR_TABLE_ID = "table_id"
    /** Max distance from avatar to table AABB edge to count as "near" for join/highlight. */
    const val INTERACTION_THRESHOLD = 60f
    /** Tap may land slightly outside the rect in world space and still join. */
    const val TAP_JOIN_HIT_THRESHOLD = 20f

    fun distanceToRect(point: Offset, rect: Rect): Float {
        val closestX = point.x.coerceIn(rect.left, rect.right)
        val closestY = point.y.coerceIn(rect.top, rect.bottom)
        return (point - Offset(closestX, closestY)).getDistance()
    }

    fun nearestTableId(position: Offset, mapConfig: MapConfig): String? {
        return mapConfig.nearestTableId(position)
    }

    fun tableIdForJoinTap(playerPosition: Offset, tapWorld: Offset, mapConfig: MapConfig): String? {
        return mapConfig.tables
            .map { table -> table to distanceToRect(tapWorld, table.rect) }
            .filter { (table, tapDist) ->
                tapDist <= TAP_JOIN_HIT_THRESHOLD &&
                    distanceToRect(playerPosition, table.rect) < INTERACTION_THRESHOLD
            }
            .minByOrNull { (_, tapDist) -> tapDist }
            ?.first
            ?.id
    }

    fun effectiveTableMeetingId(
        declaredTableId: String?,
        position: Offset,
        mapConfig: MapConfig
    ): String? {
        val declared = declaredTableId?.takeIf { it.isNotEmpty() }
        return declared ?: nearestTableId(position, mapConfig)
    }
}
