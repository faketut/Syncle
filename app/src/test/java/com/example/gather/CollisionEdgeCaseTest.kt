package com.example.gather

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.example.gather.model.AvatarState
import com.example.gather.model.CollisionSettings
import com.example.gather.model.MapConfig
import org.junit.Assert.*
import org.junit.Test

class CollisionEdgeCaseTest {

    @Test
    fun `test boundary consistency at right and bottom edges`() {
        val rect = Rect(0f, 0f, 100f, 100f)
        
        // Offset(100, 100) is exactly on the bottom-right corner.
        // Compose Rect.contains(offset) returns true if left <= offset.x < right and top <= offset.y < bottom
        assertFalse("Offset at exactly 'right' should not be contained", rect.contains(Offset(100f, 50f)))
        assertFalse("Offset at exactly 'bottom' should not be contained", rect.contains(Offset(50f, 100f)))
        
        // This confirms our logic should use 'isInside' carefully for movement.
        // If an avatar is at 100, and walkable area is 0-100, it's actually OUTSIDE.
    }

    @Test
    fun `test corner collision sliding logic`() {
        // Vertical wall at x=60 to 80
        val wall = Rect(60f, 0f, 80f, 200f)
        val mapConfig = MapConfig(
            "Test", "", 
            listOf(Rect(0f, 0f, 500f, 500f)), 
            listOf(com.example.gather.model.InteractableItem("wall", wall)), 
            emptyList(), 
            CollisionSettings("AABB", true)
        )
        
        // Avatar at (35, 100), radius 20. Right edge is at 55.
        // Wall starts at 60.
        val avatar = AvatarState(initialPosition = Offset(35f, 100f)) 
        
        // Try to move diagonally: Right +20 (to 55), Down +10 (to 110).
        // New right edge would be 55+20 = 75 -> Collides with wall (60-80).
        // Sliding logic should trigger: 
        // 1. Full move (55, 110) fails.
        // 2. X move (55, 100) fails.
        // 3. Y move (35, 110) succeeds!
        
        val moved = avatar.move(Offset(20f, 10f), mapConfig)
        
        assertTrue("Movement should be partially successful (sliding)", moved)
        assertEquals(35f, avatar.position.x, 0.1f)
        assertEquals(110f, avatar.position.y, 0.1f)
    }

    @Test
    fun `test exact boundary touch`() {
        val wall = Rect(100f, 0f, 120f, 100f)
        val mapConfig = MapConfig("Test", "", listOf(Rect(0f, 0f, 200f, 200f)), 
            listOf(com.example.gather.model.InteractableItem("wall", wall)), 
            emptyList(), CollisionSettings("AABB", true))
        
        // Avatar radius 20. Position 80. Right edge is 100.
        // Exactly touches the wall at x=100.
        val avatar = AvatarState(initialPosition = Offset(80f, 50f))
        
        // Try to move 0.1f right. Should fail.
        val moved = avatar.move(Offset(0.1f, 0f), mapConfig)
        assertFalse("Should not be able to move into wall even by 0.1", moved)
        assertEquals(80f, avatar.position.x, 0.01f)
    }
}
