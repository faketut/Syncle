package com.example.syncle.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialAudioEngineTest {
    private val map =
        MapConfig(
            mapName = "test",
            backgroundImage = "bg",
            walkableAreas = listOf(Rect(0f, 0f, 500f, 500f)),
            tables =
                listOf(
                    InteractableItem("t1", Rect(100f, 100f, 150f, 130f)),
                ),
            collisionSettings = CollisionSettings("AABB", true),
        )

    private val engine = SpatialAudioEngine(maxDistance = 100f)

    @Test
    fun sameAcousticTable_fullVolume() {
        val local = AvatarState(Offset(120f, 115f))
        val peer = RemotePeer("p1", "P1", Offset(125f, 115f))
        peer.tableMeetingId = "t1"
        val vol = engine.calculateVolume(local, peer, map, localAcousticTableId = "t1")
        assertEquals(1.0f, vol, 0.001f)
    }

    @Test
    fun differentAcousticTable_silent() {
        val local = AvatarState(Offset(120f, 115f))
        val peer = RemotePeer("p1", "P1", Offset(400f, 400f))
        val vol = engine.calculateVolume(local, peer, map, localAcousticTableId = "t1")
        assertEquals(0.0f, vol, 0.001f)
    }

    @Test
    fun openFloor_distanceAttenuation() {
        val local = AvatarState(Offset(0f, 0f))
        val peer = RemotePeer("p1", "P1", Offset(50f, 0f))
        val vol = engine.calculateVolume(local, peer, map, localAcousticTableId = null)
        assertEquals(0.5f, vol, 0.001f)
    }

    @Test
    fun quietMode_mutes() {
        val local = AvatarState(Offset(0f, 0f))
        local.status = UserStatus.QUIET_MODE
        val peer = RemotePeer("p1", "P1", Offset(10f, 0f))
        val vol = engine.calculateVolume(local, peer, map, localAcousticTableId = null)
        assertEquals(0.0f, vol, 0.001f)
    }

    @Test
    fun volumeDedup_skipsSmallChanges() {
        assertTrue(engine.shouldApplyVolume("a", 1.0f))
        assertFalse(engine.shouldApplyVolume("a", 1.01f))
        assertTrue(engine.shouldApplyVolume("a", 0.9f))
    }
}
