package com.example.syncle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.syncle.domain.AvatarState
import com.example.syncle.domain.RemotePeer
import com.example.syncle.ui.livekit.SyncleVideoRenderer
import io.livekit.android.room.Room

@Composable
fun PeerVideoOverlay(
    room: Room?,
    localAvatar: AvatarState,
    remotePeers: List<RemotePeer>,
    modifier: Modifier = Modifier
) {
    val proximityThreshold = 300f
    // No remember key: read all inputs (localAvatar.position, the peer list,
    // and each peer.position / peer.videoTrack) inside the lambda so Compose's
    // snapshot system tracks them. Keying on localAvatar.position re-created
    // the derivedStateOf on every avatar tick, defeating the API.
    val nearbyPeers by remember {
        derivedStateOf {
            val origin = localAvatar.position
            remotePeers.filter { peer ->
                (origin - peer.position).getDistance() < proximityThreshold &&
                    peer.videoTrack != null
            }
        }
    }

    if (room == null || nearbyPeers.isEmpty()) return

    Box(modifier = modifier.fillMaxSize().padding(16.dp)) {
        LazyRow(
            modifier = Modifier.align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(nearbyPeers, key = { it.id }) { peer ->
                VideoBox(room = room, peer = peer)
            }
        }
    }
}

@Composable
fun VideoBox(room: Room, peer: RemotePeer) {
    Box(
        modifier = Modifier
            .size(width = 120.dp, height = 160.dp)
            .background(Color.DarkGray, RoundedCornerShape(8.dp))
            .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.BottomCenter
    ) {
        peer.videoTrack?.let { track ->
            SyncleVideoRenderer(
                room = room,
                videoTrack = track,
                modifier = Modifier.fillMaxSize()
            )
        }
        Text(
            text = peer.displayName,
            color = Color.White,
            fontSize = 10.sp,
            modifier = Modifier.padding(4.dp).background(Color.Black.copy(alpha = 0.5f))
        )
    }
}
