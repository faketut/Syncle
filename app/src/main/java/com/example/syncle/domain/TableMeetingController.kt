package com.example.syncle.domain

import com.example.syncle.data.LiveKitService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TableMeetingController(
    private val avatarState: AvatarState,
    private val liveKit: () -> LiveKitService?,
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

    fun tableDisplayName(
        tableId: String,
        mapConfig: MapConfig,
    ): String {
        return mapConfig.tablesById[tableId]?.displayName ?: tableId
    }

    fun peersAtTable(
        tableId: String,
        mapConfig: MapConfig,
        allPeers: List<RemotePeer>,
    ): List<RemotePeer> {
        return allPeers.filter { peer ->
            TablePresence.effectiveTableMeetingId(peer.tableMeetingId, peer.position, mapConfig) == tableId
        }
    }

    // #21: buildParticipants() used to live here and called into
    // ui.buildMeetingParticipants, which created a domain → ui import
    // (inversion). The ViewModel now composes peersAtTable() + the UI
    // builder directly.

    fun reset() {
        activeTableMeetingId = null
        _activeTableIdFlow.value = null
        meetingCameraEnabled = false
        meetingMicEnabled = true
    }
}
