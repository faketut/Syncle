package com.example.syncle.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

data class MapConfig(
    val mapName: String,
    val backgroundImage: String,
    val walkableAreas: List<Rect>,
    val tables: List<InteractableItem>,
    val privateAreas: List<PrivateArea>,
    val collisionSettings: CollisionSettings
)

data class PrivateArea(
    val id: String,
    val rect: Rect
)

data class InteractableItem(
    val id: String,
    val rect: Rect,
    /** LiveKit room name / meeting room identifier for this table */
    val roomId: String = id
)

data class CollisionSettings(
    val type: String,
    val strictMode: Boolean
)

