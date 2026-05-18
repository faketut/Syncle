package com.example.gather.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
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

    // 记录相机偏移量与等比缩放状态供手势点击转换使用
    var cameraOffsetState by remember { mutableStateOf(Offset.Zero) }
    var scaleState by remember { mutableStateOf(1f) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)) // 深色科技感底层背景
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    // 屏幕点击坐标反向变换：(tapOffset + cameraOffset) / scale = 贴图原生像素坐标
                    val worldTap = (tapOffset + cameraOffsetState) / scaleState
                    val delta = worldTap - avatarState.position
                    onMove(delta)
                }
            }
    ) {
        val viewportWidth = this.size.width
        val viewportHeight = this.size.height

        val bgWidth = backgroundBitmap.width.toFloat()
        val bgHeight = backgroundBitmap.height.toFloat()

        // 1. 计算等比缩放铺满基准比例 (确保地图放大到完全铺满屏幕，绝无黑边)
        val scaleX = viewportWidth / bgWidth
        val scaleY = viewportHeight / bgHeight
        val scale = maxOf(scaleX, scaleY)
        scaleState = scale

        // 2. 核心 RPG 视角算法：将本地玩家化身的大世界缩放坐标死死锁定在屏幕正中央
        val avatarWorldX = avatarState.position.x * scale
        val avatarWorldY = avatarState.position.y * scale
        val cameraX = avatarWorldX - viewportWidth / 2f
        val cameraY = avatarWorldY - viewportHeight / 2f
        val cameraOffset = Offset(cameraX, cameraY)
        cameraOffsetState = cameraOffset

        // 利用 withTransform 将视口变换矩阵应用到整个绘制流程，内部全要素直接按贴图原生像素坐标渲染
        withTransform({
            translate(left = -cameraOffset.x, top = -cameraOffset.y)
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
        }) {
            // 1. Draw Background Image (贴图原生分辨率绘制，GPU自动等比放大并随移动反向平移)
            drawImage(
                image = backgroundBitmap,
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(bgWidth.toInt(), bgHeight.toInt())
            )

            // 1b. 绘制一层轻微的深色网格遮罩提升科技感与对比度
            drawRect(
                color = Color(0xFF0F172A).copy(alpha = 0.35f),
                topLeft = Offset.Zero,
                size = Size(bgWidth, bgHeight)
            )

            // 2. Draw Walkable Areas (可移动区域光环边框)
            mapConfig.walkableAreas.forEach { area ->
                val topLeft = Offset(area.left, area.top)
                val size = area.size
                drawRect(
                    color = Color(0xFF38BDF8).copy(alpha = 0.08f), // 柔和天蓝色底
                    topLeft = topLeft,
                    size = size
                )
                drawRect(
                    color = Color(0xFF38BDF8).copy(alpha = 0.4f),
                    topLeft = topLeft,
                    size = size,
                    style = Stroke(width = 2f)
                )
            }

            // 3. Draw Tables (精美木质桌台与交互浮层)
            mapConfig.tables.forEach { table ->
                val isNearby = avatarState.nearbyItemId == table.id
                val tableTopLeft = Offset(table.rect.left, table.rect.top)
                val tableSize = table.rect.size
                val cornerRadius = CornerRadius(16f, 16f)

                // 桌面主体颜色 (木质色调)
                val fillColor = if (isNearby) Color(0xFFD97706) else Color(0xFF78350F)
                val strokeColor = if (isNearby) Color(0xFFFBBF24) else Color(0xFFB45309)

                // 阴影底框
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.5f),
                    topLeft = tableTopLeft + Offset(4f, 4f),
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
                    style = Stroke(width = if (isNearby) 4f else 2f)
                )

                // 桌面文字标识
                textPaint.textSize = 24f
                textPaint.color = if (isNearby) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#FDE68A")
                val centerX = tableTopLeft.x + tableSize.width / 2f
                val centerY = tableTopLeft.y + tableSize.height / 2f + 8f
                drawContext.canvas.nativeCanvas.drawText(
                    if (isNearby) "Table: ${table.id} (Active)" else "Table: ${table.id}",
                    centerX,
                    centerY,
                    textPaint
                )
            }

            // 3b. Draw Private Areas (私密会议区玻璃拟态流光设计)
            mapConfig.privateAreas.forEach { area ->
                val areaTopLeft = Offset(area.rect.left, area.rect.top)
                val areaSize = area.rect.size
                val cornerRadius = CornerRadius(24f, 24f)

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
                        width = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f))
                    )
                )

                textPaint.textSize = 28f
                textPaint.color = android.graphics.Color.parseColor("#DDD6FE")
                val centerX = areaTopLeft.x + areaSize.width / 2f
                val centerY = areaTopLeft.y + areaSize.height / 2f + 10f
                drawContext.canvas.nativeCanvas.drawText(
                    "Private Zone: ${area.id}",
                    centerX,
                    centerY,
                    textPaint
                )
            }

            // 4. Draw Remote Peers (远程协作伙伴)
            remotePeers.forEach { peer ->
                val peerCenter = peer.position
                val radius = avatarState.radius

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
                textPaint.textSize = 20f
                textPaint.color = android.graphics.Color.WHITE
                val textWidth = textPaint.measureText(labelText)
                val labelRectLeft = peerCenter.x - textWidth / 2f - 12f
                val labelRectTop = peerCenter.y - radius - 36f
                val labelRectRight = peerCenter.x + textWidth / 2f + 12f
                val labelRectBottom = peerCenter.y - radius - 8f

                drawContext.canvas.nativeCanvas.drawRoundRect(
                    labelRectLeft, labelRectTop, labelRectRight, labelRectBottom,
                    8f, 8f, labelBgPaint
                )
                drawContext.canvas.nativeCanvas.drawText(
                    labelText,
                    peerCenter.x,
                    labelRectBottom - 6f,
                    textPaint
                )
            }

            // 5. Draw Local Avatar (本地玩家化身 - 永远死死固定在屏幕正中央！)
            val localCenter = avatarState.position
            val localRadius = avatarState.radius

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
            textPaint.textSize = 22f
            textPaint.color = android.graphics.Color.parseColor("#38BDF8")
            val myTextWidth = textPaint.measureText(myLabel)
            val myLabelLeft = localCenter.x - myTextWidth / 2f - 16f
            val myLabelTop = localCenter.y - localRadius - 40f
            val myLabelRight = localCenter.x + myTextWidth / 2f + 16f
            val myLabelBottom = localCenter.y - localRadius - 10f

            drawContext.canvas.nativeCanvas.drawRoundRect(
                myLabelLeft, myLabelTop, myLabelRight, myLabelBottom,
                12f, 12f, labelBgPaint
            )
            drawContext.canvas.nativeCanvas.drawText(
                myLabel,
                localCenter.x,
                myLabelBottom - 8f,
                textPaint
            )
        }
    }
}
