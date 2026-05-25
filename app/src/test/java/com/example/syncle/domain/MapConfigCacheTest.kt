package com.example.syncle.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapConfigCacheTest {
    private val map =
        MapConfig(
            mapName = "test",
            backgroundImage = "bg",
            walkableAreas = listOf(Rect(0f, 0f, 500f, 500f)),
            tables = listOf(InteractableItem("t1", Rect(100f, 100f, 120f, 110f))),
            collisionSettings = CollisionSettings("AABB", true),
        )

    @Test
    fun nearestTableId_usesCacheWithinEpsilon() {
        val cache = MapConfigCache(map, positionEpsilon = 5f)
        val near = Offset(110f, 105f)
        assertEquals("t1", cache.nearestTableId(near))
        assertEquals("t1", cache.nearestTableId(Offset(111f, 106f)))
    }

    @Test
    fun nearestTableId_forceRefreshAfterMove() {
        val cache = MapConfigCache(map, positionEpsilon = 5f)
        assertEquals("t1", cache.nearestTableId(Offset(110f, 105f)))
        assertNull(cache.nearestTableId(Offset(10f, 10f), forceRefresh = true))
    }

    @Test
    fun resolveLocalAcousticTable_prefersActiveMeeting() {
        val cache = MapConfigCache(map)
        assertEquals("meeting", cache.resolveLocalAcousticTable(Offset(10f, 10f), "meeting"))
    }

    @Test
    fun computeMapBounds_handlesNegativeCoordinates() {
        // Regression: seeding right/bottom with Float.MIN_VALUE (smallest
        // positive float) instead of -Float.MAX_VALUE produced bounds where
        // right/bottom collapsed to ~0 for negative-coord walkable rects.
        val rect = Rect(-200f, -150f, -50f, -25f)
        val bounds = MapConfig.computeMapBounds(listOf(rect))
        assertEquals(rect, bounds)
    }
}
