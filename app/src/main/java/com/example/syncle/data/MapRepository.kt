package com.example.syncle.data

import androidx.compose.ui.geometry.Rect
import com.example.syncle.domain.CollisionSettings
import com.example.syncle.domain.InteractableItem
import com.example.syncle.domain.MapConfig
import org.json.JSONObject

class MapRepository {
    
    fun parseJsonConfig(jsonString: String): MapConfig {
        val root = JSONObject(jsonString)
        
        val walkableAreas = mutableListOf<Rect>()
        val walkableJson = root.optJSONArray("walkable_areas") ?: org.json.JSONArray()
        for (i in 0 until walkableJson.length()) {
            val obj = walkableJson.getJSONObject(i)
            walkableAreas.add(
                createRect(
                    obj.getDouble("x").toFloat(),
                    obj.getDouble("y").toFloat(),
                    obj.getDouble("width").toFloat(),
                    obj.getDouble("height").toFloat()
                )
            )
        }
        
        val tables = mutableListOf<InteractableItem>()
        val tablesJson = root.optJSONArray("tables") ?: org.json.JSONArray()
        for (i in 0 until tablesJson.length()) {
            val obj = tablesJson.getJSONObject(i)
            tables.add(
                InteractableItem(
                    id = obj.getString("id"),
                    rect = createRect(
                        obj.getDouble("x").toFloat(),
                        obj.getDouble("y").toFloat(),
                        obj.getDouble("width").toFloat(),
                        obj.getDouble("height").toFloat()
                    ),
                    displayName = obj.optString("display_name", obj.getString("id"))
                )
            )
        }

        val collisionObj = root.getJSONObject("collision_settings")
        val collisionSettings = CollisionSettings(
            type = collisionObj.getString("type"),
            strictMode = collisionObj.getBoolean("strict_mode")
        )
        
        return MapConfig(
            mapName = root.getString("map_name"),
            backgroundImage = root.getString("background_image"),
            walkableAreas = walkableAreas,
            tables = tables,
            collisionSettings = collisionSettings
        )
    }
}

/**
 * Helper to convert JSON raw data to Compose-friendly Rect.
 * Normalizes negative width/height to ensure left <= right and top <= bottom.
 */
fun createRect(x: Float, y: Float, width: Float, height: Float): Rect {
    val left = if (width < 0) x + width else x
    val top = if (height < 0) y + height else y
    val right = if (width < 0) x else x + width
    val bottom = if (height < 0) y else y + height
    return Rect(left, top, right, bottom)
}
