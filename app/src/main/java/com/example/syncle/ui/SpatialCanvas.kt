package com.example.syncle.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.example.syncle.model.AvatarState
import com.example.syncle.model.InteractableItem
import com.example.syncle.model.MapConfig
import com.example.syncle.model.RemotePeer
import io.livekit.android.room.participant.ConnectionQuality

@Composable
fun SpatialCanvas(
    mapConfig: MapConfig,
    avatarState: AvatarState,
    remotePeers: List<RemotePeer>,
    onMove: (Offset) -> Unit,
    onJoinRoom: (roomId: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var scale by remember { mutableStateOf(1f) }

    // Pulsing alpha animation for the highlighted table border
    val infiniteTransition = rememberInfiniteTransition(label = "highlight_pulse")
    val highlightAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "highlight_alpha"
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E)) // dark fallback until background image loads
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 3f)
                    offset += pan
                }
            }
            .pointerInput(avatarState.nearbyItemId) {
                detectTapGestures { tapOffset ->
                    // Convert screen tap → world coordinates
                    val worldTap = (tapOffset - offset) / scale

                    // Check if the tap lands inside the highlighted (nearby) table
                    val nearbyId = avatarState.nearbyItemId
                    if (nearbyId != null) {
                        val tappedTable = mapConfig.tables.firstOrNull { table ->
                            table.id == nearbyId && table.rect.contains(worldTap)
                        }
                        if (tappedTable != null) {
                            // User explicitly tapped the highlighted table → join its room
                            onJoinRoom(tappedTable.id)
                            return@detectTapGestures
                        }
                    }

                    // Otherwise treat as a movement command
                    val delta = worldTap - avatarState.position
                    onMove(delta)
                }
            }
    ) {
        // ── 1. Background ────────────────────────────────────────────────────
        // TODO: Replace with ImageBitmap drawn via drawImage() once the asset pipeline
        // provides a real background image.  The dark fill serves as the placeholder.
        drawRect(
            color = Color(0xFF1A1A2E),
            topLeft = offset,
            size = Size(mapConfig.walkableAreas.fold(0f) { acc, r -> maxOf(acc, r.right) } * scale,
                        mapConfig.walkableAreas.fold(0f) { acc, r -> maxOf(acc, r.bottom) } * scale)
        )

        // ── 2. Collision map is intentionally NOT rendered ───────────────────
        // walkableAreas and tables are kept in MapConfig for collision and table meetings
        // solely for physics / AABB collision and spatial-audio calculations.

        // ── 3. Highlight border for the nearby / colliding table ─────────────
        val nearbyId = avatarState.nearbyItemId
        if (nearbyId != null) {
            val highlightTable = mapConfig.tables.firstOrNull { it.id == nearbyId }
            if (highlightTable != null) {
                val tableScreenTopLeft = Offset(
                    highlightTable.rect.left * scale + offset.x,
                    highlightTable.rect.top * scale + offset.y
                )
                val tableScreenSize = highlightTable.rect.size * scale

                // Outer glow — slightly expanded translucent rect
                val glowPad = 6f
                drawRect(
                    color = Color(0xFFFFD700).copy(alpha = highlightAlpha * 0.25f),
                    topLeft = tableScreenTopLeft - Offset(glowPad, glowPad),
                    size = Size(
                        tableScreenSize.width + glowPad * 2,
                        tableScreenSize.height + glowPad * 2
                    )
                )
                // Sharp border
                drawRect(
                    color = Color(0xFFFFD700).copy(alpha = highlightAlpha),
                    topLeft = tableScreenTopLeft,
                    size = tableScreenSize,
                    style = Stroke(width = 3f)
                )
            }
        }

        // ── 4. Local Avatar ──────────────────────────────────────────────────
        val localCenter = Offset(
            avatarState.position.x * scale + offset.x,
            avatarState.position.y * scale + offset.y
        )
        if (avatarState.isSpeaking) {
            drawCircle(
                color = Color(0xFF4CAF50).copy(alpha = 0.5f),
                radius = avatarState.radius * scale * 1.5f,
                center = localCenter
            )
        }
        drawCircle(
            color = Color(0xFF00E5FF),
            radius = avatarState.radius * scale,
            center = localCenter
        )
        
        val drawConnectionQuality = { quality: ConnectionQuality, center: Offset ->
            val color = when (quality) {
                ConnectionQuality.EXCELLENT -> Color(0xFF4CAF50)
                ConnectionQuality.GOOD -> Color(0xFFFFEB3B)
                ConnectionQuality.POOR, ConnectionQuality.LOST -> Color(0xFFF44336)
                else -> Color.Transparent
            }
            if (color != Color.Transparent) {
                drawCircle(
                    color = color,
                    radius = 5f * scale,
                    center = center + Offset(avatarState.radius * scale * 0.7f, -avatarState.radius * scale * 0.7f)
                )
            }
        }
        
        drawConnectionQuality(avatarState.connectionQuality, localCenter)

        // ── 5. Remote Peers ──────────────────────────────────────────────────
        remotePeers.forEach { peer ->
            val peerCenter = Offset(
                peer.position.x * scale + offset.x,
                peer.position.y * scale + offset.y
            )
            if (peer.isSpeaking) {
                drawCircle(
                    color = Color(0xFF4CAF50).copy(alpha = 0.5f),
                    radius = avatarState.radius * scale * 1.5f,
                    center = peerCenter
                )
            }
            drawCircle(
                color = Color(0xFF9E9E9E),
                radius = avatarState.radius * scale,
                center = peerCenter
            )
            drawConnectionQuality(peer.connectionQuality, peerCenter)
        }
    }
}
