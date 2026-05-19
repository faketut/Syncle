package com.example.syncle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.syncle.model.AvatarState
import com.example.syncle.model.MapConfig
import com.example.syncle.model.RemotePeer
import com.example.syncle.model.SyncleUiState
import com.example.syncle.model.SyncleViewModel
import io.livekit.android.room.Room

@Composable
fun SyncleScreen(
    mapConfig: MapConfig,
    uiState: SyncleUiState,
    avatarState: AvatarState,
    remotePeers: List<RemotePeer>,
    onMove: (androidx.compose.ui.geometry.Offset) -> Unit,
    onJoinTableMeeting: (String) -> Unit,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onLeaveMeeting: () -> Unit,
    liveKitRoom: Room? = null
) {
    val meeting = uiState.meeting
    val activeTableId = meeting.activeTableId
    val backgroundImage = rememberMapBackgroundImage(mapConfig.backgroundImage)
    val logicWorldSize = remember(mapConfig, backgroundImage) {
        backgroundImage?.let { Size(it.width.toFloat(), it.height.toFloat()) }
            ?: mapConfig.mapDrawSize
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SpatialCanvas(
            mapConfig = mapConfig,
            logicWorldSize = logicWorldSize,
            backgroundImage = backgroundImage,
            avatarState = avatarState,
            remotePeers = remotePeers,
            onMove = onMove,
            onJoinRoom = onJoinTableMeeting,
            modifier = Modifier.fillMaxSize()
        )

        if (activeTableId == null) {
            PeerVideoOverlay(
                room = liveKitRoom,
                localAvatar = avatarState,
                remotePeers = remotePeers,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            )
            TableMeetingOverlay(
                tableTitle = meeting.tableTitle ?: activeTableId,
                participants = meeting.participants,
                micEnabled = meeting.micEnabled,
                cameraEnabled = meeting.cameraEnabled,
                room = liveKitRoom,
                onToggleMic = onToggleMic,
                onToggleCamera = onToggleCamera,
                onLeave = onLeaveMeeting,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
fun SyncleScreenHost(
    mapConfig: MapConfig,
    viewModel: SyncleViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SyncleScreen(
        mapConfig = mapConfig,
        uiState = uiState,
        avatarState = viewModel.avatarState,
        remotePeers = viewModel.remotePeers,
        onMove = { delta -> viewModel.onMove(delta) },
        onJoinTableMeeting = { viewModel.joinTableMeeting(it) },
        onToggleMic = { viewModel.toggleMeetingMic() },
        onToggleCamera = { viewModel.toggleMeetingCamera() },
        onLeaveMeeting = { viewModel.leaveTableMeeting() },
        liveKitRoom = viewModel.getLiveKitRoom()
    )
}
