package com.example.gather

import android.content.Context
import androidx.compose.ui.geometry.Offset
import com.example.gather.model.*
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class SyncAndInterpolationTest {

    private val mockContext = mockk<Context>(relaxed = true)

    @Test
    fun `test lerp tracks moving target smoothly`() {
        val peer = RemotePeer("p1", "Peer 1", Offset(0f, 0f))
        
        // Target moves to 100
        peer.targetPosition = Offset(100f, 0f)
        peer.interpolate(0.5f) // Moves to 50
        assertEquals(50f, peer.position.x, 0.1f)
        
        // While moving, target changes to 200
        peer.targetPosition = Offset(200f, 0f)
        peer.interpolate(0.5f) // Moves half of (200 - 50) = 75. 50 + 75 = 125
        assertEquals(125f, peer.position.x, 0.1f)
    }

    @Test
    fun `test small distance threshold alignment`() {
        val peer = RemotePeer("p1", "Peer 1", Offset(0f, 0f))
        
        // Very small distance (0.5f < 1f threshold)
        peer.targetPosition = Offset(0.5f, 0f)
        peer.interpolate(0.1f)
        
        // Should snap to target
        assertEquals(0.5f, peer.position.x, 0f)
        assertEquals(peer.targetPosition, peer.position)
    }

    @Test
    fun `test out-of-order packet handling`() {
        val mapConfig = MapConfig("Test", "", emptyList(), emptyList(), emptyList(), CollisionSettings("AABB", true))
        val localAvatar = AvatarState()
        val manager = GatherViewModel()
        
        fun createPosJson(x: Float, seq: Long) = JSONObject().apply {
            put("type", "position")
            put("x", x.toDouble())
            put("y", 0.0)
            put("seq", seq)
        }.toString().toByteArray()

        // t1 arrives (seq 100, pos 10)
        manager.onDataReceived("p1", createPosJson(10f, 100))
        assertEquals(10f, manager.remotePeers[0].targetPosition.x, 0f)

        // t3 arrives (seq 300, pos 30) - Newer packet
        manager.onDataReceived("p1", createPosJson(30f, 300))
        assertEquals(30f, manager.remotePeers[0].targetPosition.x, 0f)

        // t2 arrives (seq 200, pos 20) - Late packet, should be IGNORED
        manager.onDataReceived("p1", createPosJson(20f, 200))
        
        // Target should still be 30, not 20
        assertEquals("Late packet should not overwrite newer state", 30f, manager.remotePeers[0].targetPosition.x, 0f) 
    }

    @Test
    fun `test large scale peer performance`() {
        val mapConfig = MapConfig("Test", "", emptyList(), emptyList(), emptyList(), CollisionSettings("AABB", true))
        val localAvatar = AvatarState()
        val manager = GatherViewModel()
        
        val startTime = System.currentTimeMillis()
        
        // Add 100 peers
        for (i in 1..100) {
            manager.onDataReceived("p$i", JSONObject().apply {
                put("type", "position")
                put("x", i.toDouble())
                put("y", i.toDouble())
            }.toString().toByteArray())
        }
        
        assertEquals(100, manager.remotePeers.size)
        
        // Simulate interpolation for all
        manager.remotePeers.forEach { it.interpolate(0.2f) }
        
        val endTime = System.currentTimeMillis()
        assertTrue("Updating 100 peers should be very fast (< 50ms)", (endTime - startTime) < 50)
    }
}
