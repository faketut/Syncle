package com.example.syncle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.example.syncle.model.AvatarState
import com.example.syncle.model.MapConfig
import com.example.syncle.model.RemotePeer
import com.example.syncle.model.TablePresence
import kotlin.math.roundToInt

private val LocalAvatarColor = Color(0xFF00E5FF)
private val RemoteAvatarColor = Color(0xFF9E9E9E)
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
    modifier: Modifier = Modifier
) {
    val nearbyId = avatarState.nearbyItemId

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(nearbyId, avatarState.position, logicWorldSize) {
                detectTapGestures { tapOffset ->
                    val screenSize = Size(size.width.toFloat(), size.height.toFloat())
                    val viewport = MapCamera.compute(
                        playerX = avatarState.position.x,
                        playerY = avatarState.position.y,
                        screenWidth = screenSize.width,
                        screenHeight = screenSize.height,
                        logicWidth = logicWorldSize.width,
                        logicHeight = logicWorldSize.height
                    )
                    val worldTap = MapCamera.screenToWorld(
                        tapOffset.x,
                        tapOffset.y,
                        viewport,
                        screenSize
                    )

                    val joinTableId = TablePresence.tableIdForJoinTap(
                        avatarState.position,
                        worldTap,
                        mapConfig
                    )
                    if (joinTableId != null) {
                        onJoinRoom(joinTableId)
                        return@detectTapGestures
                    }

                    val delta = worldTap - avatarState.position
                    onMove(delta)
                }
            }
    ) {
        val screenSize = size
        val viewport = MapCamera.compute(
            playerX = avatarState.position.x,
            playerY = avatarState.position.y,
            screenWidth = screenSize.width,
            screenHeight = screenSize.height,
            logicWidth = logicWorldSize.width,
            logicHeight = logicWorldSize.height
        )

        drawBackgroundCover(backgroundImage, viewport, screenSize)

        avatarState.nearbyItemId?.let { id ->
            mapConfig.tablesById[id]?.let { table ->
                drawTableInteractionEdgeHighlight(table.rect, viewport, screenSize)
            }
        }

        val localScreen = MapCamera.worldToScreen(
            avatarState.position.x,
            avatarState.position.y,
            viewport,
            screenSize
        )
        drawAvatarDot(
            center = localScreen,
            radiusPx = avatarState.radius * viewport.scale,
            fillColor = LocalAvatarColor,
            showSpeakingHalo = avatarState.isSpeaking
        )

        remotePeers.forEach { peer ->
            val peerScreen = MapCamera.worldToScreen(peer.position.x, peer.position.y, viewport, screenSize)
            drawAvatarDot(
                center = peerScreen,
                radiusPx = avatarState.radius * viewport.scale,
                fillColor = RemoteAvatarColor,
                showSpeakingHalo = peer.isSpeaking
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
    screenSize: Size
) {
    val topLeft = MapCamera.worldToScreen(tableRect.left, tableRect.top, viewport, screenSize)
    val bottomRight = MapCamera.worldToScreen(tableRect.right, tableRect.bottom, viewport, screenSize)
    val width = bottomRight.x - topLeft.x
    val height = bottomRight.y - topLeft.y
    if (width <= 1f || height <= 1f) return

    val strokeBase = (2.5f * viewport.scale).coerceIn(1.5f, 5f)
    val expandSteps = listOf(
        14f to 0.06f,
        10f to 0.12f,
        7f to 0.22f,
        4f to 0.38f,
        2f to 0.58f,
        0f to 0.88f
    )

    expandSteps.forEach { (expandPx, alpha) ->
        val pad = expandPx * viewport.scale.coerceIn(0.5f, 2f)
        drawRect(
            color = Color.White.copy(alpha = alpha),
            topLeft = topLeft - Offset(pad, pad),
            size = Size(width + pad * 2f, height + pad * 2f),
            style = Stroke(width = strokeBase)
        )
    }
}

private fun DrawScope.drawBackgroundCover(
    image: ImageBitmap?,
    viewport: MapViewport,
    screenSize: Size
) {
    if (image == null) {
        drawRect(color = Color(0xFF1A1A2E), size = screenSize)
        return
    }

    val mapW = viewport.logicWidth
    val mapH = viewport.logicHeight
    val scaledW = mapW * viewport.scale
    val scaledH = mapH * viewport.scale
    val topLeft = Offset(
        x = screenSize.width / 2f - viewport.camX * viewport.scale,
        y = screenSize.height / 2f - viewport.camY * viewport.scale
    )

    drawImage(
        image = image,
        dstOffset = androidx.compose.ui.unit.IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
        dstSize = androidx.compose.ui.unit.IntSize(scaledW.roundToInt(), scaledH.roundToInt())
    )
}

private fun DrawScope.drawAvatarDot(
    center: Offset,
    radiusPx: Float,
    fillColor: Color,
    showSpeakingHalo: Boolean
) {
    if (showSpeakingHalo) {
        drawCircle(
            color = SpeakingHalo,
            radius = radiusPx * 1.45f,
            center = center
        )
    }
    drawCircle(color = fillColor, radius = radiusPx.coerceAtLeast(4f), center = center)
}
