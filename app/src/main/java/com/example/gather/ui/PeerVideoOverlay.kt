package com.example.gather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gather.model.AvatarState
import com.example.gather.model.RemotePeer
import io.livekit.android.room.track.VideoTrack

@Composable
fun PeerVideoOverlay(
    localAvatar: AvatarState,
    remotePeers: List<RemotePeer>,
    modifier: Modifier = Modifier
) {
    val proximityThreshold = 300f
    val nearbyPeers = remotePeers.filter { peer ->
        (localAvatar.position - peer.position).getDistance() < proximityThreshold
        && peer.videoTrack != null // Only show if video is available
    }

    Box(modifier = modifier.fillMaxSize().padding(16.dp)) {
        LazyRow(
            modifier = Modifier.align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(nearbyPeers) { peer ->
                VideoBox(peer)
            }
        }
    }
}

@Composable
fun VideoBox(peer: RemotePeer) {
    Box(
        modifier = Modifier
            .size(width = 120.dp, height = 160.dp)
            .background(Color.DarkGray, RoundedCornerShape(8.dp))
            .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.BottomCenter
    ) {
        // LiveKit Video Renderer Integration
        peer.videoTrack?.let { track ->
            // In a real LiveKit app:
            // VideoTrackView(track = track)
            Text(
                text = "Live: ${peer.name}",
                color = Color.Green,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        } ?: run {
            Text(
                text = "No Video",
                color = Color.Gray,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Text(
            text = peer.name,
            color = Color.White,
            fontSize = 10.sp,
            modifier = Modifier.padding(4.dp).background(Color.Black.copy(alpha = 0.5f))
        )
    }
}
