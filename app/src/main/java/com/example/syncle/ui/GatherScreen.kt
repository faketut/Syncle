package com.example.syncle.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.syncle.model.AvatarState
import com.example.syncle.model.MapConfig
import com.example.syncle.model.RemotePeer

@Composable
fun SyncleScreen(
    mapConfig: MapConfig,
    avatarState: AvatarState,
    remotePeers: List<RemotePeer>,
    onJoinRoom: (roomId: String) -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Bottom Layer: Spatial Canvas
        SpatialCanvas(
            mapConfig = mapConfig,
            avatarState = avatarState,
            remotePeers = remotePeers,
            onMove = { delta -> avatarState.move(delta, mapConfig) },
            onJoinRoom = onJoinRoom,
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
