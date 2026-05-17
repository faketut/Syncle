package com.example.gather.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.gather.model.AvatarState
import com.example.gather.model.MapConfig
import com.example.gather.model.RemotePeer

@Composable
fun GatherScreen(
    mapConfig: MapConfig,
    avatarState: AvatarState,
    remotePeers: List<RemotePeer>
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Bottom Layer: Spatial Canvas
        SpatialCanvas(
            mapConfig = mapConfig,
            avatarState = avatarState,
            remotePeers = remotePeers,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Top Layer: Video PIP Overlays
        PeerVideoOverlay(
            localAvatar = avatarState,
            remotePeers = remotePeers,
            modifier = Modifier.fillMaxSize()
        )
    }
}
