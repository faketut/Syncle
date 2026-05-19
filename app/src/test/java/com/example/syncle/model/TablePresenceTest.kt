package com.example.syncle.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TablePresenceTest {

    private val map = MapConfig(
        mapName = "test",
        backgroundImage = "bg",
        walkableAreas = listOf(Rect(0f, 0f, 500f, 500f)),
        tables = listOf(InteractableItem("t1", Rect(100f, 100f, 120f, 110f))),
        collisionSettings = CollisionSettings("AABB", true)
    )

    @Test
    fun nearestTableId_withinThreshold() {
        val id = TablePresence.nearestTableId(Offset(110f, 105f), map)
        assertEquals("t1", id)
    }

    @Test
    fun nearestTableId_outsideThreshold() {
        val id = TablePresence.nearestTableId(Offset(10f, 10f), map)
        assertNull(id)
    }

    @Test
    fun nearestTableId_wideTable_nearLeftEdge_notCenter() {
        val wide = MapConfig(
            mapName = "test",
            backgroundImage = "bg",
            walkableAreas = listOf(Rect(0f, 0f, 1000f, 1000f)),
            tables = listOf(InteractableItem("wide", Rect(100f, 100f, 350f, 120f))),
            collisionSettings = CollisionSettings("AABB", true)
        )
        val leftBeside = Offset(70f, 110f)
        assertNull(TablePresence.nearestTableId(Offset(10f, 10f), wide))
        assertEquals("wide", TablePresence.nearestTableId(leftBeside, wide))
    }

    @Test
    fun tableIdForJoinTap_hitsTableEdgeWhilePlayerBeside() {
        val table = InteractableItem("t1", Rect(100f, 100f, 350f, 120f))
        val wideMap = MapConfig(
            mapName = "test",
            backgroundImage = "bg",
            walkableAreas = listOf(Rect(0f, 0f, 1000f, 1000f)),
            tables = listOf(table),
            collisionSettings = CollisionSettings("AABB", true)
        )
        val player = Offset(70f, 110f)
        val tapOnLeftEdge = Offset(105f, 110f)
        assertEquals("t1", TablePresence.tableIdForJoinTap(player, tapOnLeftEdge, wideMap))
    }

    @Test
    fun effectiveTable_prefersDeclared() {
        val id = TablePresence.effectiveTableMeetingId("declared", Offset(10f, 10f), map)
        assertEquals("declared", id)
    }
}
