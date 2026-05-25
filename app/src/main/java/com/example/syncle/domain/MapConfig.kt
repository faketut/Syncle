package com.example.syncle.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

data class MapConfig(
    val mapName: String,
    val backgroundImage: String,
    val walkableAreas: List<Rect>,
    val tables: List<InteractableItem>,
    val collisionSettings: CollisionSettings,
    val tablesById: Map<String, InteractableItem> = tables.associateBy { it.id },
    val mapBounds: Rect = computeMapBounds(walkableAreas),
    val mapDrawSize: Size = Size(mapBounds.width.coerceAtLeast(1f), mapBounds.height.coerceAtLeast(1f)),
) {
    fun nearestTableId(position: Offset): String? {
        return tables
            .map { it.id to TablePresence.distanceToRect(position, it.rect) }
            .filter { (_, dist) -> dist < TablePresence.INTERACTION_THRESHOLD }
            .minByOrNull { (_, dist) -> dist }
            ?.first
    }

    companion object {
        fun computeMapBounds(walkableAreas: List<Rect>): Rect {
            if (walkableAreas.isEmpty()) return Rect(0f, 0f, 1000f, 1000f)
            var left = Float.MAX_VALUE
            var top = Float.MAX_VALUE
            // Seed maxes with -Float.MAX_VALUE (most negative), NOT
            // Float.MIN_VALUE (which is the smallest positive float ~1.4e-45).
            // The old seed silently produced wrong bounds for any walkable
            // rect with a negative right/bottom.
            var right = -Float.MAX_VALUE
            var bottom = -Float.MAX_VALUE
            walkableAreas.forEach { r ->
                left = minOf(left, r.left)
                top = minOf(top, r.top)
                right = maxOf(right, r.right)
                bottom = maxOf(bottom, r.bottom)
            }
            return Rect(left, top, right, bottom)
        }
    }
}

data class InteractableItem(
    val id: String,
    val rect: Rect,
    val displayName: String = id,
)

data class CollisionSettings(
    val type: String,
    val strictMode: Boolean,
)
