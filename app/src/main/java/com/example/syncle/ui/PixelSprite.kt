package com.example.syncle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * Tiny pixel-art renderer. Each row string picks a palette entry per char:
 * '.' = transparent, '1' = primary (character color), '2' = light (face /
 * accent), '3' = outline / dark. Grids are square; rendered with crisp 1:1
 * pixel scaling centered in the available canvas.
 */
data class PixelSprite(val rows: List<String>) {
    val sideCells: Int = rows.size
}

@Composable
fun PixelSpriteView(
    sprite: PixelSprite,
    primary: Color,
    modifier: Modifier = Modifier,
    light: Color = Color(0xFFF9E8C9),
    dark: Color = Color(0xFF1A1A22),
    background: Color = Color.Transparent,
) {
    Canvas(modifier = modifier) {
        val side = sprite.sideCells.coerceAtLeast(1)
        val cell = minOf(size.width, size.height) / side
        val originX = (size.width - cell * side) / 2f
        val originY = (size.height - cell * side) / 2f
        if (background != Color.Transparent) {
            drawRect(background, Offset(originX, originY), Size(cell * side, cell * side))
        }
        sprite.rows.forEachIndexed { y, row ->
            row.forEachIndexed inner@{ x, ch ->
                val color =
                    when (ch) {
                        '1' -> primary
                        '2' -> light
                        '3' -> dark
                        else -> null
                    } ?: return@inner
                drawRect(
                    color = color,
                    topLeft = Offset(originX + x * cell, originY + y * cell),
                    size = Size(cell, cell),
                )
            }
        }
    }
}
