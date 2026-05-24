package com.example.syncle.domain

import com.example.syncle.model.AvatarState
import com.example.syncle.model.LiveKitService
import com.example.syncle.model.MapConfig
import com.example.syncle.model.RemotePeer
import com.example.syncle.model.TablePresence
import com.example.syncle.ui.MeetingParticipant
import com.example.syncle.ui.buildMeetingParticipants
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TableMeetingController(
    private val avatarState: AvatarState,
    private val liveKit: () -> LiveKitService?
) {
    private val _activeTableIdFlow = MutableStateFlow<String?>(null)
    val activeTableIdFlow: StateFlow<String?> = _activeTableIdFlow.asStateFlow()

    var activeTableMeetingId: String? = null
        private set

    var meetingMicEnabled: Boolean = true
        private set

    var meetingCameraEnabled: Boolean = false
        private set

    fun join(tableId: String): Boolean {
        if (avatarState.nearbyItemId != tableId) return false
        activeTableMeetingId = tableId
        _activeTableIdFlow.value = tableId
        meetingCameraEnabled = true
        liveKit()?.setLocalAttributes(mapOf(TablePresence.ATTR_TABLE_ID to tableId))
        liveKit()?.setCameraEnabled(true)
        return true
    }

    fun leave() {
        activeTableMeetingId = null
        _activeTableIdFlow.value = null
        meetingCameraEnabled = false
        liveKit()?.setLocalAttributes(mapOf(TablePresence.ATTR_TABLE_ID to ""))
        liveKit()?.setCameraEnabled(false)
    }

    fun toggleMic() {
        meetingMicEnabled = !meetingMicEnabled
        liveKit()?.setMicrophoneEnabled(meetingMicEnabled)
    }

    fun toggleCamera() {
        meetingCameraEnabled = !meetingCameraEnabled
        liveKit()?.setCameraEnabled(meetingCameraEnabled)
    }

    fun syncPresence(mapCache: MapConfigCache) {
        val active = activeTableMeetingId ?: return
        val atTable = mapCache.nearestTableId(avatarState.position)
        if (atTable != active) leave()
    }

    fun tableDisplayName(tableId: String, mapConfig: MapConfig): String {
        return mapConfig.tablesById[tableId]?.displayName ?: tableId
    }

    fun peersAtTable(tableId: String, mapConfig: MapConfig, allPeers: List<RemotePeer>): List<RemotePeer> {
        return allPeers.filter { peer ->
            TablePresence.effectiveTableMeetingId(peer.tableMeetingId, peer.position, mapConfig) == tableId
        }
    }

    fun buildParticipants(
        mapCache: MapConfigCache,
        allPeers: List<RemotePeer>,
        localIdentity: String?,
        localVideoTrack: VideoTrack?
    ): List<MeetingParticipant> {
        val tableId = activeTableMeetingId ?: return emptyList()
        val mapConfig = mapCache.config
        val tablePeers = peersAtTable(tableId, mapConfig, allPeers)
        return buildMeetingParticipants(
            localAvatar = avatarState,
            localIdentity = localIdentity,
            localMicEnabled = meetingMicEnabled,
            localCameraEnabled = meetingCameraEnabled,
            localVideoTrack = localVideoTrack,
            tablePeers = tablePeers
        )
    }

    fun reset() {
        activeTableMeetingId = null
        _activeTableIdFlow.value = null
        meetingCameraEnabled = false
        meetingMicEnabled = true
    }
}
