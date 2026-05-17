package com.example.gather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.example.gather.model.AvatarState
import com.example.gather.model.MapConfig
import com.example.gather.model.RemotePeer

@Composable
fun SpatialCanvas(
    mapConfig: MapConfig,
    avatarState: AvatarState,
    remotePeers: List<RemotePeer>,
    onMove: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var scale by remember { mutableStateOf(1f) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale *= zoom
                    offset += pan
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    val worldTap = (tapOffset - offset) / scale
                    val delta = worldTap - avatarState.position
                    onMove(delta)
                }
            }
    ) {
        // 1. Draw Background
        drawRect(
            color = Color.White,
            topLeft = offset,
            size = this.size * scale,
            style = Stroke(width = 2f)
        )

        // 2. Draw Walkable Areas
        mapConfig.walkableAreas.forEach { area ->
            drawRect(
                color = Color.White.copy(alpha = 0.1f),
                topLeft = Offset(
                    area.left * scale + offset.x,
                    area.top * scale + offset.y
                ),
                size = area.size * scale
            )
            drawRect(
                color = Color.White.copy(alpha = 0.3f),
                topLeft = Offset(
                    area.left * scale + offset.x,
                    area.top * scale + offset.y
                ),
                size = area.size * scale,
                style = Stroke(width = 1f)
            )
        }

        // 3. Draw Tables
        mapConfig.tables.forEach { table ->
            val isNearby = avatarState.nearbyItemId == table.id
            val color = if (isNearby) Color.Yellow else Color.Cyan
            val alpha = if (isNearby) 0.4f else 0.2f
            
            drawRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(
                    table.rect.left * scale + offset.x,
                    table.rect.top * scale + offset.y
                ),
                size = table.rect.size * scale
            )
            
            if (isNearby) {
                drawRect(
                    color = Color.Yellow,
                    topLeft = Offset(
                        table.rect.left * scale + offset.x,
                        table.rect.top * scale + offset.y
                    ),
                    size = table.rect.size * scale,
                    style = Stroke(width = 3f * scale)
                )
            }
        }

        // 3b. Draw Private Areas
        mapConfig.privateAreas.forEach { area ->
            drawRect(
                color = Color.Magenta.copy(alpha = 0.05f),
                topLeft = Offset(
                    area.rect.left * scale + offset.x,
                    area.rect.top * scale + offset.y
                ),
                size = area.rect.size * scale
            )
            drawRect(
                color = Color.Magenta.copy(alpha = 0.2f),
                topLeft = Offset(
                    area.rect.left * scale + offset.x,
                    area.rect.top * scale + offset.y
                ),
                size = area.rect.size * scale,
                style = Stroke(width = 1f)
            )
        }

        // 4. Draw Local Avatar
        drawCircle(
            color = Color.White,
            radius = avatarState.radius * scale,
            center = Offset(
                avatarState.position.x * scale + offset.x,
                avatarState.position.y * scale + offset.y
            )
        )

        // 5. Draw Remote Peers
        remotePeers.forEach { peer ->
            drawCircle(
                color = Color.Gray,
                radius = avatarState.radius * scale,
                center = Offset(
                    peer.position.x * scale + offset.x,
                    peer.position.y * scale + offset.y
                )
            )
        }
    }
}
