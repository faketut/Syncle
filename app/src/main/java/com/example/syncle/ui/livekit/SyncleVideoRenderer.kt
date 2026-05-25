package com.example.syncle.ui.livekit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.RemoteVideoTrack
import io.livekit.android.room.track.VideoTrack
import livekit.org.webrtc.RendererCommon

@Composable
fun SyncleVideoRenderer(
    room: Room,
    videoTrack: VideoTrack?,
    modifier: Modifier = Modifier,
    mirror: Boolean = false,
) {
    if (LocalView.current.isInEditMode) {
        Box(modifier = modifier.background(Color.Black))
        return
    }

    val videoSinkVisibility = remember(room, videoTrack) { ComposeVideoVisibility() }
    var boundVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var view: TextureViewRenderer? by remember { mutableStateOf(null) }

    fun cleanupVideoTrack() {
        view?.let { boundVideoTrack?.removeRenderer(it) }
        boundVideoTrack = null
    }

    fun setupVideoIfNeeded(
        track: VideoTrack?,
        renderer: TextureViewRenderer,
    ) {
        if (boundVideoTrack == track) return
        cleanupVideoTrack()
        boundVideoTrack = track
        if (track != null) {
            if (track is RemoteVideoTrack) {
                track.addRenderer(renderer, videoSinkVisibility)
            } else {
                track.addRenderer(renderer)
            }
        }
    }

    DisposableEffect(view, mirror) {
        view?.setMirror(mirror)
        onDispose { }
    }

    DisposableEffect(room, videoTrack) {
        onDispose {
            videoSinkVisibility.onDispose()
            cleanupVideoTrack()
        }
    }

    DisposableEffect(currentCompositeKeyHash) {
        onDispose { view?.release() }
    }

    AndroidView(
        factory = { context ->
            TextureViewRenderer(context).apply {
                room.initVideoRenderer(this)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setupVideoIfNeeded(videoTrack, this)
                view = this
            }
        },
        update = { renderer -> setupVideoIfNeeded(videoTrack, renderer) },
        modifier = modifier.onGloballyPositioned { videoSinkVisibility.onGloballyPositioned(it) },
    )
}
