package com.example.syncle.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * World space matches background image pixels (W_logic x H_logic).
 * Camera center follows the player until map edges are reached.
 */
data class MapViewport(
    val camX: Float,
    val camY: Float,
    val scale: Float,
    val logicWidth: Float,
    val logicHeight: Float,
)

object MapCamera {
    fun compute(
        playerX: Float,
        playerY: Float,
        screenWidth: Float,
        screenHeight: Float,
        logicWidth: Float,
        logicHeight: Float,
    ): MapViewport {
        val w = logicWidth.coerceAtLeast(1f)
        val h = logicHeight.coerceAtLeast(1f)
        val scale = maxOf(screenWidth / w, screenHeight / h)
        val halfVisibleW = screenWidth / (2f * scale)
        val halfVisibleH = screenHeight / (2f * scale)

        val camX =
            if (w <= halfVisibleW * 2f) {
                w / 2f
            } else {
                playerX.coerceIn(halfVisibleW, w - halfVisibleW)
            }

        val camY =
            if (h <= halfVisibleH * 2f) {
                h / 2f
            } else {
                playerY.coerceIn(halfVisibleH, h - halfVisibleH)
            }

        return MapViewport(camX, camY, scale, w, h)
    }

    fun worldToScreen(
        worldX: Float,
        worldY: Float,
        viewport: MapViewport,
        screenSize: Size,
    ): Offset {
        return Offset(
            x = (worldX - viewport.camX) * viewport.scale + screenSize.width / 2f,
            y = (worldY - viewport.camY) * viewport.scale + screenSize.height / 2f,
        )
    }

    fun screenToWorld(
        screenX: Float,
        screenY: Float,
        viewport: MapViewport,
        screenSize: Size,
    ): Offset {
        return Offset(
            x = (screenX - screenSize.width / 2f) / viewport.scale + viewport.camX,
            y = (screenY - screenSize.height / 2f) / viewport.scale + viewport.camY,
        )
    }
}
