package com.example.syncle.ui.livekit

import androidx.compose.ui.layout.LayoutCoordinates
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.video.VideoSinkVisibility

/** Visibility helper for [SyncleVideoRenderer] (from LiveKit compose sample). */
class ComposeVideoVisibility : VideoSinkVisibility() {
    private var coordinates: LayoutCoordinates? = null
    private var lastVisible = isVisible()
    private var lastSize = size()

    override fun isVisible(): Boolean {
        val c = coordinates
        return c?.isAttached == true && c.size.width != 0 && c.size.height != 0
    }

    override fun size(): Track.Dimensions {
        val c = coordinates
        return Track.Dimensions(c?.size?.width ?: 0, c?.size?.height ?: 0)
    }

    fun onGloballyPositioned(layoutCoordinates: LayoutCoordinates) {
        coordinates = layoutCoordinates
        val visible = isVisible()
        val size = size()
        if (lastVisible != visible || lastSize != size) {
            notifyChanged()
        }
        lastVisible = visible
        lastSize = size
    }

    fun onDispose() {
        if (coordinates == null) return
        coordinates = null
        notifyChanged()
    }
}
