package com.example.gather.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.gather.R
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

    // 加载 R.drawable.room1 背景贴图
    val backgroundBitmap = ImageBitmap.imageResource(id = R.drawable.room1)

    // 原生画笔用于绘制精美抗锯齿文字标签
    val textPaint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
    }
    val labelBgPaint = remember {
        Paint().apply {
            color = android.graphics.Color.parseColor("#80000000") // 半透明黑色底框
            isAntiAlias = true
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)) // 深色科技感底层背景
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
        val canvasWidth = this.size.width
        val canvasHeight = this.size.height

        // 1. Draw Background Image (room1.png)
        drawImage(
            image = backgroundBitmap,
            dstOffset = IntOffset(offset.x.toInt(), offset.y.toInt()),
            dstSize = IntSize((canvasWidth * scale).toInt(), (canvasHeight * scale).toInt())
        )

        // 1b. 绘制一层轻微的深色网格遮罩提升科技感与对比度
        drawRect(
            color = Color(0xFF0F172A).copy(alpha = 0.35f),
            topLeft = offset,
            size = Size(canvasWidth * scale, canvasHeight * scale)
        )

        // 2. Draw Walkable Areas (可移动区域光环边框)
        mapConfig.walkableAreas.forEach { area ->
            val topLeft = Offset(area.left * scale + offset.x, area.top * scale + offset.y)
            val size = area.size * scale
            drawRect(
                color = Color(0xFF38BDF8).copy(alpha = 0.08f), // 柔和天蓝色底
                topLeft = topLeft,
                size = size
            )
            drawRect(
                color = Color(0xFF38BDF8).copy(alpha = 0.4f),
                topLeft = topLeft,
                size = size,
                style = Stroke(width = 2f * scale)
            )
        }

        // 3. Draw Tables (精美木质桌台与交互浮层)
        mapConfig.tables.forEach { table ->
            val isNearby = avatarState.nearbyItemId == table.id
            val tableTopLeft = Offset(table.rect.left * scale + offset.x, table.rect.top * scale + offset.y)
            val tableSize = table.rect.size * scale
            val cornerRadius = CornerRadius(16f * scale, 16f * scale)

            // 桌面主体颜色 (木质色调)
            val fillColor = if (isNearby) Color(0xFFD97706) else Color(0xFF78350F)
            val strokeColor = if (isNearby) Color(0xFFFBBF24) else Color(0xFFB45309)

            // 阴影底框
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = tableTopLeft + Offset(4f * scale, 4f * scale),
                size = tableSize,
                cornerRadius = cornerRadius
            )

            // 桌面主体
            drawRoundRect(
                color = fillColor.copy(alpha = if (isNearby) 0.85f else 0.7f),
                topLeft = tableTopLeft,
                size = tableSize,
                cornerRadius = cornerRadius
            )

            // 桌面高光边框
            drawRoundRect(
                color = strokeColor,
                topLeft = tableTopLeft,
                size = tableSize,
                cornerRadius = cornerRadius,
                style = Stroke(width = (if (isNearby) 4f else 2f) * scale)
            )

            // 桌面文字标识
            textPaint.textSize = 24f * scale
            textPaint.color = if (isNearby) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#FDE68A")
            val centerX = tableTopLeft.x + tableSize.width / 2f
            val centerY = tableTopLeft.y + tableSize.height / 2f + (8f * scale)
            drawContext.canvas.nativeCanvas.drawText(
                if (isNearby) "Table: ${table.id} (Active)" else "Table: ${table.id}",
                centerX,
                centerY,
                textPaint
            )
        }

        // 3b. Draw Private Areas (私密会议区玻璃拟态流光设计)
        mapConfig.privateAreas.forEach { area ->
            val areaTopLeft = Offset(area.rect.left * scale + offset.x, area.rect.top * scale + offset.y)
            val areaSize = area.rect.size * scale
            val cornerRadius = CornerRadius(24f * scale, 24f * scale)

            drawRoundRect(
                color = Color(0xFF8B5CF6).copy(alpha = 0.15f), // 幽雅紫水晶透明底
                topLeft = areaTopLeft,
                size = areaSize,
                cornerRadius = cornerRadius
            )
            drawRoundRect(
                color = Color(0xFFC084FC).copy(alpha = 0.5f),
                topLeft = areaTopLeft,
                size = areaSize,
                cornerRadius = cornerRadius,
                style = Stroke(
                    width = 2f * scale,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f * scale, 15f * scale))
                )
            )

            textPaint.textSize = 28f * scale
            textPaint.color = android.graphics.Color.parseColor("#DDD6FE")
            val centerX = areaTopLeft.x + areaSize.width / 2f
            val centerY = areaTopLeft.y + areaSize.height / 2f + (10f * scale)
            drawContext.canvas.nativeCanvas.drawText(
                "Private Zone: ${area.id}",
                centerX,
                centerY,
                textPaint
            )
        }

        // 4. Draw Remote Peers (远程协作伙伴)
        remotePeers.forEach { peer ->
            val peerCenter = Offset(peer.position.x * scale + offset.x, peer.position.y * scale + offset.y)
            val radius = avatarState.radius * scale

            // 外层雷达波动光环
            drawCircle(
                color = Color(0xFF10B981).copy(alpha = 0.25f),
                radius = radius * 1.6f,
                center = peerCenter
            )

            // 中层边框
            drawCircle(
                color = Color(0xFF059669),
                radius = radius * 1.1f,
                center = peerCenter
            )

            // 内层实体
            drawCircle(
                color = Color(0xFF34D399),
                radius = radius,
                center = peerCenter
            )

            // 名称标签底框与文字
            val labelText = peer.name
            textPaint.textSize = 20f * scale
            textPaint.color = android.graphics.Color.WHITE
            val textWidth = textPaint.measureText(labelText)
            val labelRectLeft = peerCenter.x - textWidth / 2f - (12f * scale)
            val labelRectTop = peerCenter.y - radius - (36f * scale)
            val labelRectRight = peerCenter.x + textWidth / 2f + (12f * scale)
            val labelRectBottom = peerCenter.y - radius - (8f * scale)

            drawContext.canvas.nativeCanvas.drawRoundRect(
                labelRectLeft, labelRectTop, labelRectRight, labelRectBottom,
                8f * scale, 8f * scale, labelBgPaint
            )
            drawContext.canvas.nativeCanvas.drawText(
                labelText,
                peerCenter.x,
                labelRectBottom - (6f * scale),
                textPaint
            )
        }

        // 5. Draw Local Avatar (本地玩家化身 - 炫酷拟物高光)
        val localCenter = Offset(avatarState.position.x * scale + offset.x, avatarState.position.y * scale + offset.y)
        val localRadius = avatarState.radius * scale

        // 外层耀眼青色波动圈
        drawCircle(
            color = Color(0xFF06B6D4).copy(alpha = 0.35f),
            radius = localRadius * 1.8f,
            center = localCenter
        )

        // 中层科技蓝光环边框
        drawCircle(
            color = Color(0xFF0284C7),
            radius = localRadius * 1.2f,
            center = localCenter
        )

        // 内层高光核心实体
        drawCircle(
            color = Color(0xFF38BDF8),
            radius = localRadius,
            center = localCenter
        )
        drawCircle(
            color = Color.White,
            radius = localRadius * 0.4f,
            center = localCenter
        )

        // 本地名称标签 `"Me (Local)"`
        val myLabel = "Me (Local)"
        textPaint.textSize = 22f * scale
        textPaint.color = android.graphics.Color.parseColor("#38BDF8")
        val myTextWidth = textPaint.measureText(myLabel)
        val myLabelLeft = localCenter.x - myTextWidth / 2f - (16f * scale)
        val myLabelTop = localCenter.y - localRadius - (40f * scale)
        val myLabelRight = localCenter.x + myTextWidth / 2f + (16f * scale)
        val myLabelBottom = localCenter.y - localRadius - (10f * scale)

        drawContext.canvas.nativeCanvas.drawRoundRect(
            myLabelLeft, myLabelTop, myLabelRight, myLabelBottom,
            12f * scale, 12f * scale, labelBgPaint
        )
        drawContext.canvas.nativeCanvas.drawText(
            myLabel,
            localCenter.x,
            myLabelBottom - (8f * scale),
            textPaint
        )
    }
}
