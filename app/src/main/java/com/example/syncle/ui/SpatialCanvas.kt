package com.example.syncle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.example.syncle.data.ProfileStore
import com.example.syncle.domain.AvatarState
import com.example.syncle.domain.MapConfig
import com.example.syncle.domain.RemotePeer
import com.example.syncle.domain.TablePresence
import kotlin.math.roundToInt

private val LocalAvatarColor = Color(0xFF00E5FF)
private val SpriteLight = Color(0xFFF9E8C9)
private val SpriteDark = Color(0xFF1A1A22)
private val RemoteAvatarFallback = Color(0xFF9E9E9E)
private val SpeakingHalo = Color(0xFF4CAF50).copy(alpha = 0.45f)

@Composable
fun SpatialCanvas(
    mapConfig: MapConfig,
    logicWorldSize: Size,
    backgroundImage: ImageBitmap?,
    avatarState: AvatarState,
    remotePeers: List<RemotePeer>,
    onMove: (Offset) -> Unit,
    onJoinRoom: (tableId: String) -> Unit = {},
    localCharacter: String? = null,
    localColor: String? = null,
    modifier: Modifier = Modifier,
) {
    val nearbyId = avatarState.nearbyItemId

    Canvas(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(nearbyId, avatarState.position, logicWorldSize) {
                    detectTapGestures { tapOffset ->
                        val screenSize = Size(size.width.toFloat(), size.height.toFloat())
                        val viewport =
                            MapCamera.compute(
                                playerX = avatarState.position.x,
                                playerY = avatarState.position.y,
                                screenWidth = screenSize.width,
                                screenHeight = screenSize.height,
                                logicWidth = logicWorldSize.width,
                                logicHeight = logicWorldSize.height,
                            )
                        val worldTap =
                            MapCamera.screenToWorld(
                                tapOffset.x,
                                tapOffset.y,
                                viewport,
                                screenSize,
                            )

                        val joinTableId =
                            TablePresence.tableIdForJoinTap(
                                avatarState.position,
                                worldTap,
                                mapConfig,
                            )
                        if (joinTableId != null) {
                            onJoinRoom(joinTableId)
                            return@detectTapGestures
                        }

                        val delta = worldTap - avatarState.position
                        onMove(delta)
                    }
                },
    ) {
        val screenSize = size
        val viewport =
            MapCamera.compute(
                playerX = avatarState.position.x,
                playerY = avatarState.position.y,
                screenWidth = screenSize.width,
                screenHeight = screenSize.height,
                logicWidth = logicWorldSize.width,
                logicHeight = logicWorldSize.height,
            )

        drawBackgroundCover(backgroundImage, viewport, screenSize)

        avatarState.nearbyItemId?.let { id ->
            mapConfig.tablesById[id]?.let { table ->
                drawTableInteractionEdgeHighlight(table.rect, viewport, screenSize)
            }
        }

        val localScreen =
            MapCamera.worldToScreen(
                avatarState.position.x,
                avatarState.position.y,
                viewport,
                screenSize,
            )
        val avatarRadiusPx = (avatarState.radius * viewport.scale).coerceAtLeast(10f)
        val localSprite = localCharacter?.let { ProfileStore.characterById(it).sprite }
        val localFill =
            localColor?.let {
                try {
                    Color(android.graphics.Color.parseColor(it))
                } catch (_: IllegalArgumentException) {
                    null
                }
            } ?: LocalAvatarColor
        drawAvatar(
            center = localScreen,
            radiusPx = avatarRadiusPx,
            fillColor = localFill,
            showSpeakingHalo = avatarState.isSpeaking,
            sprite = localSprite,
        )

        remotePeers.forEach { peer ->
            val peerScreen = MapCamera.worldToScreen(peer.position.x, peer.position.y, viewport, screenSize)
            val peerColor =
                try {
                    Color(android.graphics.Color.parseColor(peer.color))
                } catch (_: IllegalArgumentException) {
                    RemoteAvatarFallback
                }
            val peerSprite =
                peer.character?.let { ProfileStore.characterById(it).sprite }
                    ?: ProfileStore.characterByColor(peer.color)?.sprite
            drawAvatar(
                center = peerScreen,
                radiusPx = avatarRadiusPx,
                fillColor = peerColor,
                showSpeakingHalo = peer.isSpeaking,
                sprite = peerSprite,
            )
        }
    }
}

/**
 * White edge glow on the active table (logic-space rect), shown when avatar is within interaction range.
 */
private fun DrawScope.drawTableInteractionEdgeHighlight(
    tableRect: Rect,
    viewport: MapViewport,
    screenSize: Size,
) {
    val topLeft = MapCamera.worldToScreen(tableRect.left, tableRect.top, viewport, screenSize)
    val bottomRight = MapCamera.worldToScreen(tableRect.right, tableRect.bottom, viewport, screenSize)
    val width = bottomRight.x - topLeft.x
    val height = bottomRight.y - topLeft.y
    if (width <= 1f || height <= 1f) return

    val strokeBase = (2.5f * viewport.scale).coerceIn(1.5f, 5f)
    val expandSteps =
        listOf(
            14f to 0.06f,
            10f to 0.12f,
            7f to 0.22f,
            4f to 0.38f,
            2f to 0.58f,
            0f to 0.88f,
        )

    expandSteps.forEach { (expandPx, alpha) ->
        val pad = expandPx * viewport.scale.coerceIn(0.5f, 2f)
        drawRect(
            color = Color.White.copy(alpha = alpha),
            topLeft = topLeft - Offset(pad, pad),
            size = Size(width + pad * 2f, height + pad * 2f),
            style = Stroke(width = strokeBase),
        )
    }
}

private fun DrawScope.drawBackgroundCover(
    image: ImageBitmap?,
    viewport: MapViewport,
    screenSize: Size,
) {
    if (image == null) {
        drawRect(color = Color(0xFF1A1A2E), size = screenSize)
        return
    }

    val mapW = viewport.logicWidth
    val mapH = viewport.logicHeight
    val scaledW = mapW * viewport.scale
    val scaledH = mapH * viewport.scale
    val topLeft =
        Offset(
            x = screenSize.width / 2f - viewport.camX * viewport.scale,
            y = screenSize.height / 2f - viewport.camY * viewport.scale,
        )

    drawImage(
        image = image,
        dstOffset = androidx.compose.ui.unit.IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
        dstSize = androidx.compose.ui.unit.IntSize(scaledW.roundToInt(), scaledH.roundToInt()),
    )
}

private fun DrawScope.drawAvatar(
    center: Offset,
    radiusPx: Float,
    fillColor: Color,
    showSpeakingHalo: Boolean,
    sprite: PixelSprite?,
) {
    if (showSpeakingHalo) {
        drawCircle(
            color = SpeakingHalo,
            radius = radiusPx * 1.45f,
            center = center,
        )
    }
    val r = radiusPx.coerceAtLeast(4f)
    if (sprite == null) {
        drawCircle(color = fillColor, radius = r, center = center)
        return
    }
    // Soft colored backdrop so the sprite reads against any map tile.
    drawCircle(color = fillColor.copy(alpha = 0.35f), radius = r * 1.05f, center = center)
    drawPixelSprite(
        sprite = sprite,
        center = center,
        sizePx = r * 2.2f,
        primary = fillColor,
    )
}

private fun DrawScope.drawPixelSprite(
    sprite: PixelSprite,
    center: Offset,
    sizePx: Float,
    primary: Color,
) {
    val side = sprite.sideCells.coerceAtLeast(1)
    val cell = sizePx / side
    val originX = center.x - sizePx / 2f
    val originY = center.y - sizePx / 2f
    sprite.rows.forEachIndexed { y, row ->
        row.forEachIndexed inner@{ x, ch ->
            val color =
                when (ch) {
                    '1' -> primary
                    '2' -> SpriteLight
                    '3' -> SpriteDark
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
