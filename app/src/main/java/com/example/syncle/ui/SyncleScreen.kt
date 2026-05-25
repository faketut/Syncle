package com.example.syncle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.syncle.data.ProfileStore
import com.example.syncle.domain.AvatarState
import com.example.syncle.domain.MapConfig
import com.example.syncle.domain.RemotePeer
import com.example.syncle.ui.state.ConnectionStatus
import com.example.syncle.ui.state.SyncleUiState
import com.example.syncle.viewmodel.SyncleViewModel
import io.livekit.android.room.Room
import kotlinx.coroutines.launch

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
    onApplyProfileEdit: (String, String) -> Boolean = { _, _ -> false },
    liveKitRoom: Room? = null,
) {
    val meeting = uiState.meeting
    val activeTableId = meeting.activeTableId
    val backgroundImage = rememberMapBackground(mapConfig.backgroundImage)
    val logicWorldSize =
        remember(mapConfig, backgroundImage) {
            backgroundImage?.logicalSize ?: mapConfig.mapDrawSize
        }
    var settingsOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        SpatialCanvas(
            mapConfig = mapConfig,
            logicWorldSize = logicWorldSize,
            backgroundImage = backgroundImage?.image,
            avatarState = avatarState,
            remotePeers = remotePeers,
            onMove = onMove,
            onJoinRoom = onJoinTableMeeting,
            modifier = Modifier.fillMaxSize(),
        )

        if (activeTableId == null) {
            PeerVideoOverlay(
                room = liveKitRoom,
                localAvatar = avatarState,
                remotePeers = remotePeers,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
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
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
            )
        }

        if (uiState.connection.status == ConnectionStatus.RECONNECTING) {
            ReconnectBanner(
                attempt = uiState.connection.reconnectAttempt,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
            )
        }

        // #50: in-meeting profile editor. Hidden while a table meeting overlay
        // owns the bottom of the screen to avoid covering meeting controls.
        if (activeTableId == null) {
            IconButton(
                onClick = { settingsOpen = true },
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
            ) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
        }
    }

    if (settingsOpen) {
        ProfileSettingsSheet(
            initialNickname = uiState.connection.nickname,
            initialColor = uiState.connection.color,
            nicknameError = uiState.connection.nicknameError,
            onApply = onApplyProfileEdit,
            onDismiss = { settingsOpen = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSettingsSheet(
    initialNickname: String,
    initialColor: String,
    nicknameError: String?,
    onApply: (String, String) -> Boolean,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var nickname by remember { mutableStateOf(initialNickname) }
    var color by remember { mutableStateOf(initialColor) }
    var localError by remember { mutableStateOf(nicknameError) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text("Edit profile", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = nickname,
                onValueChange = {
                    nickname = it
                    localError =
                        if (ProfileStore.isValidNickname(it)) {
                            null
                        } else {
                            "Nickname required (max ${ProfileStore.NICKNAME_MAX_LEN})"
                        }
                },
                label = { Text("Nickname") },
                isError = localError != null,
                supportingText = { localError?.let { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text("Accent color", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            SettingsColorRow(selected = color, onSelect = { color = it })
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (onApply(nickname, color)) {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                        }
                    } else {
                        localError = "Nickname required (max ${ProfileStore.NICKNAME_MAX_LEN})"
                    }
                },
                enabled = ProfileStore.isValidNickname(nickname),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("Save")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SettingsColorRow(
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProfileStore.PALETTE.forEach { hex ->
            val swatch =
                try {
                    Color(android.graphics.Color.parseColor(hex))
                } catch (_: IllegalArgumentException) {
                    MaterialTheme.colorScheme.primary
                }
            val isSelected = hex.equals(selected, ignoreCase = true)
            Box(
                modifier =
                    Modifier
                        .size(32.dp)
                        .background(swatch, shape = CircleShape)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color =
                                if (isSelected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                            shape = CircleShape,
                        )
                        .clickable { onSelect(hex) },
            )
        }
    }
}

@Composable
private fun ReconnectBanner(
    attempt: Int,
    modifier: Modifier = Modifier,
) {
    val label = if (attempt > 0) "Reconnecting… (attempt $attempt)" else "Reconnecting…"
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(
            text = label,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun SyncleScreenHost(
    mapConfig: MapConfig,
    viewModel: SyncleViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
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
        onApplyProfileEdit = { nick, color -> viewModel.applyProfileEdit(context, nick, color) },
        liveKitRoom = viewModel.getLiveKitRoom(),
    )
}
