package com.example.syncle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.syncle.domain.AvatarState
import com.example.syncle.domain.RemotePeer
import com.example.syncle.ui.livekit.SyncleVideoRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack

data class MeetingParticipant(
    val id: String,
    val displayName: String,
    val isLocal: Boolean,
    val isSpeaking: Boolean,
    val isMicMuted: Boolean,
    val hasVideo: Boolean,
    val videoTrack: VideoTrack? = null,
    /** Accent color hex (e.g. "#4F8EF7"). Drives the avatar background. */
    val color: String = "#9E9E9E",
    /** Pixel-art character id (see ProfileStore.CHARACTERS). Null falls back to initial. */
    val character: String? = null,
)

@Composable
fun TableMeetingOverlay(
    tableTitle: String,
    participants: List<MeetingParticipant>,
    micEnabled: Boolean,
    cameraEnabled: Boolean,
    room: Room?,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .fillMaxHeight(0.58f)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(Color(0xFF1B1B1F)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tableTitle,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${participants.size} 人在会议中",
                    color = Color(0xFFB0B0B0),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onLeave) {
                Text("离开", color = Color(0xFFFF8A80))
            }
        }

        HorizontalDivider(color = Color(0xFF2E2E34))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(participants, key = { it.id }) { p ->
                MeetingParticipantTile(participant = p, room = room)
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF121216))
                    .padding(vertical = 14.dp, horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MeetingControlButton(
                onClick = onToggleMic,
                containerColor = if (micEnabled) Color(0xFF3A3A42) else Color(0xFFB3261E),
                icon = {
                    Icon(
                        imageVector = if (micEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = null,
                        tint = Color.White,
                    )
                },
            )
            MeetingControlButton(
                onClick = onToggleCamera,
                containerColor = if (cameraEnabled) Color(0xFF3A3A42) else Color(0xFFB3261E),
                icon = {
                    Icon(
                        imageVector = if (cameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        contentDescription = null,
                        tint = Color.White,
                    )
                },
            )
            MeetingControlButton(
                onClick = onLeave,
                containerColor = Color(0xFFB3261E),
                icon = {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = null,
                        tint = Color.White,
                    )
                },
            )
        }
    }
}

@Composable
private fun MeetingControlButton(
    onClick: () -> Unit,
    containerColor: Color,
    icon: @Composable () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(52.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = containerColor),
    ) {
        icon()
    }
}

@Composable
private fun MeetingParticipantTile(
    participant: MeetingParticipant,
    room: Room?,
) {
    val borderColor = if (participant.isSpeaking) Color(0xFF4CAF50) else Color(0xFF3A3A42)
    val showVideo = room != null && participant.videoTrack != null
    Box(
        modifier =
            Modifier
                .aspectRatio(1.15f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF25252C))
                .border(2.dp, borderColor, RoundedCornerShape(10.dp)),
    ) {
        if (showVideo && room != null) {
            SyncleVideoRenderer(
                room = room,
                videoTrack = participant.videoTrack,
                modifier = Modifier.fillMaxSize(),
                mirror = participant.isLocal,
            )
        }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            if (!showVideo) {
                val avatarColor =
                    try {
                        Color(android.graphics.Color.parseColor(participant.color))
                    } catch (_: IllegalArgumentException) {
                        Color(0xFF9E9E9E)
                    }
                val sprite =
                    participant.character?.let { id ->
                        com.example.syncle.data.ProfileStore.CHARACTERS.firstOrNull { it.id == id }?.sprite
                    }
                        ?: com.example.syncle.data.ProfileStore.characterByColor(participant.color)?.sprite
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(avatarColor.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (sprite != null) {
                        com.example.syncle.ui.PixelSpriteView(
                            sprite = sprite,
                            primary = avatarColor,
                            modifier = Modifier.size(42.dp),
                        )
                    } else {
                        Text(
                            text = participant.displayName.take(1).uppercase(),
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(48.dp))
            }
            Text(
                text = if (participant.isLocal) "${participant.displayName} (你)" else participant.displayName,
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!participant.isMicMuted && participant.isLocal) {
                    Text("开麦", color = Color(0xFF81C784), fontSize = 11.sp)
                }
                if (participant.hasVideo) {
                    Text("视频", color = Color(0xFF64B5F6), fontSize = 11.sp)
                }
            }
        }
    }
}

fun buildMeetingParticipants(
    localAvatar: AvatarState,
    localIdentity: String?,
    localMicEnabled: Boolean,
    localCameraEnabled: Boolean,
    localVideoTrack: VideoTrack?,
    tablePeers: List<RemotePeer>,
    localColor: String = "#00E5FF",
    localCharacter: String? = null,
): List<MeetingParticipant> {
    val localId = localIdentity ?: "local"
    val list =
        mutableListOf(
            MeetingParticipant(
                id = localId,
                displayName = localAvatar.name,
                isLocal = true,
                isSpeaking = localAvatar.isSpeaking,
                isMicMuted = !localMicEnabled,
                hasVideo = localCameraEnabled && localVideoTrack != null,
                videoTrack = if (localCameraEnabled) localVideoTrack else null,
                color = localColor,
                character = localCharacter,
            ),
        )
    tablePeers.forEach { peer ->
        list.add(
            MeetingParticipant(
                id = peer.id,
                displayName = peer.displayName,
                isLocal = false,
                isSpeaking = peer.isSpeaking,
                isMicMuted = peer.status == com.example.syncle.domain.UserStatus.QUIET_MODE,
                hasVideo = peer.videoTrack != null,
                videoTrack = peer.videoTrack,
                color = peer.color,
                character = peer.character,
            ),
        )
    }
    return list
}
