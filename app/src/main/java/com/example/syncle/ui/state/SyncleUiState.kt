package com.example.syncle.ui.state

import com.example.syncle.ui.MeetingParticipant

/**
 * UI state contracts consumed by composables and produced by the ViewModel.
 *
 * #21: moved out of `com.example.syncle.model` so the UI layer owns its own
 * state types and the domain layer no longer reaches up into `ui.*`.
 * Re-homed here verbatim (no behavior change).
 */

enum class ConnectionStatus {
    CONNECTING, CONNECTED, RECONNECTING, DISCONNECTED, ERROR
}

data class LocalAvatarUi(
    val name: String,
    val nearbyItemId: String?
)

data class ConnectionUi(
    val status: ConnectionStatus,
    val url: String,
    val token: String,
    val isAutoFetching: Boolean,
    val startupError: String?,
    val lastConnectError: String? = null,
    /** Room name the user is joining / has joined. #40. */
    val room: String = "syncle-office",
    /** Inline validation error for the room field, if any. */
    val roomError: String? = null,
    /** Editable nickname shown on the join screen. */
    val nickname: String = "",
    /** Inline validation error for the nickname field, if any. */
    val nicknameError: String? = null,
    /** Selected accent color as a hex string ("#RRGGBB"). */
    val color: String = "#4F8EF7",
    /** Current reconnect attempt counter; 0 when not in RECONNECTING state. */
    val reconnectAttempt: Int = 0,
)

data class MeetingUi(
    val activeTableId: String?,
    val tableTitle: String?,
    val participants: List<MeetingParticipant>,
    val micEnabled: Boolean,
    val cameraEnabled: Boolean
)

data class SyncleUiState(
    val mapReady: Boolean = false,
    val connection: ConnectionUi = ConnectionUi(
        status = ConnectionStatus.DISCONNECTED,
        url = "",
        token = "",
        isAutoFetching = false,
        startupError = null
    ),
    val meeting: MeetingUi = MeetingUi(
        activeTableId = null,
        tableTitle = null,
        participants = emptyList(),
        micEnabled = true,
        cameraEnabled = false
    ),
    val localAvatar: LocalAvatarUi = LocalAvatarUi(name = "Me", nearbyItemId = null)
)
