package com.example.gather

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.example.gather.model.*
import org.junit.Assert.*
import org.junit.Test

class AdvancedAudioTest {

    private val maxDistance = 300f

    @Test
    fun `test triple user isolation - different rooms and public area`() {
        val room1 = PrivateArea("room1", Rect(0f, 0f, 100f, 100f))
        val room2 = PrivateArea("room2", Rect(200f, 0f, 300f, 100f))
        val mapConfig = MapConfig(
            "Test", "", emptyList(), emptyList(),
            listOf(room1, room2),
            CollisionSettings("AABB", true)
        )

        val avatarA = AvatarState(initialPosition = Offset(50f, 50f)) // Room 1
        val peerB = RemotePeer("pB", "Peer B", Offset(250f, 50f)) // Room 2
        val peerC = RemotePeer("pC", "Peer C", Offset(150f, 50f)) // Public Area
        
        val volAB = GatherViewModel.calculateVolume(avatarA, peerB, mapConfig, maxDistance)
        val volAC = GatherViewModel.calculateVolume(avatarA, peerC, mapConfig, maxDistance)
        
        assertEquals("A should not hear B (different rooms)", 0.0f, volAB, 0f)
        assertEquals("A should not hear C (A in room, C in public)", 0.0f, volAC, 0f)
    }

    @Test
    fun `test overlapping private areas priority`() {
        val room1 = PrivateArea("room1", Rect(0f, 0f, 100f, 100f))
        val room2 = PrivateArea("room2", Rect(50f, 0f, 150f, 100f)) 
        
        val mapConfig = MapConfig(
            "Test", "", emptyList(), emptyList(),
            listOf(room1, room2),
            CollisionSettings("AABB", true)
        )

        val pos = Offset(75f, 50f)
        val selectedArea = mapConfig.privateAreas.find { it.rect.contains(pos) }
        
        assertEquals("Should select the first room in the list for overlapping areas", "room1", selectedArea?.id)
    }

    @Test
    fun `test volume transition at room boundary`() {
        val room = PrivateArea("room1", Rect(0f, 0f, 100f, 100f))
        val mapConfig = MapConfig(
            "Test", "", emptyList(), emptyList(),
            listOf(room),
            CollisionSettings("AABB", true)
        )

        val peer = RemotePeer("p1", "Peer 1", Offset(99f, 50f)) // Just inside

        // Case 1: Local is inside at (98, 50) -> Full volume
        val volInside = GatherViewModel.calculateVolume(AvatarState(Offset(98f, 50f)), peer, mapConfig, maxDistance)
        assertEquals(1.0f, volInside, 0f)

        // Case 2: Local is just outside at (101, 50) -> Muted due to isolation
        val volOutside = GatherViewModel.calculateVolume(AvatarState(Offset(101f, 50f)), peer, mapConfig, maxDistance)
        assertEquals(0.0f, volOutside, 0f)
    }
}
