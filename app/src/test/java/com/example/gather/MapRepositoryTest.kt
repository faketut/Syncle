package com.example.gather

import com.example.gather.model.MapRepository
import org.json.JSONException
import org.junit.Assert.*
import org.junit.Test

class MapRepositoryTest {

    private val repository = MapRepository()

    @Test
    fun `test valid json parsing`() {
        val json = """
            {
                "map_name": "Office",
                "background_image": "floor.png",
                "walkable_areas": [{"x": 0, "y": 0, "width": 100, "height": 100}],
                "tables": [{"id": "t1", "x": 10, "y": 10, "width": 20, "height": 20}],
                "private_areas": [{"id": "p1", "x": 50, "y": 50, "width": 30, "height": 30}],
                "collision_settings": {"type": "AABB", "strict_mode": true}
            }
        """.trimIndent()

        val config = repository.parseJsonConfig(json)
        assertEquals("Office", config.mapName)
        assertEquals(1, config.walkableAreas.size)
        assertEquals(1, config.tables.size)
        assertEquals(1, config.privateAreas.size)
    }

    @Test
    fun `test optional fields missing`() {
        // Missing private_areas and tables
        val json = """
            {
                "map_name": "Office",
                "background_image": "floor.png",
                "walkable_areas": [],
                "collision_settings": {"type": "AABB", "strict_mode": true}
            }
        """.trimIndent()

        val config = repository.parseJsonConfig(json)
        assertTrue(config.tables.isEmpty())
        assertTrue(config.privateAreas.isEmpty())
    }

    @Test(expected = JSONException::class)
    fun `test required field missing throws exception`() {
        val json = """
            {
                "map_name": "Office"
            }
        """.trimIndent()
        // Should throw exception because collision_settings and others are missing
        repository.parseJsonConfig(json)
    }

    @Test
    fun `test negative dimensions handling`() {
        val json = """
            {
                "map_name": "Office",
                "background_image": "floor.png",
                "walkable_areas": [{"x": 10, "y": 10, "width": -50, "height": -50}],
                "tables": [],
                "collision_settings": {"type": "AABB", "strict_mode": true}
            }
        """.trimIndent()

        val config = repository.parseJsonConfig(json)
        val rect = config.walkableAreas[0]
        
        // Compose Rect(10, 10, -50, -50) might result in a rect where left > right
        // We want to see if our parser should normalize it or if it causes issues.
        // Usually, Rect(offset, size) handles it, but let's check behavior.
        assertTrue("Rect with negative size should be handled or normalized", rect.width >= 0)
    }
}
