package com.example.syncle.domain

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PeerRegistry {
    private val peersById = mutableMapOf<String, RemotePeer>()
    val observablePeers: SnapshotStateList<RemotePeer> = mutableStateListOf()

    // Monotonic revision counter. Bumped on every structural change (add/remove)
    // and whenever a caller signals a peer field mutation via markChanged().
    // Collectors can `combine` this with other state flows to drive reactive
    // UI rebuilds without relying on every mutation site to remember calling
    // pushUiState() manually.
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    fun get(id: String): RemotePeer? = peersById[id]

    fun getOrCreate(
        id: String,
        initialPosition: Offset = Offset.Zero,
    ): RemotePeer {
        val existing = peersById[id]
        if (existing != null) return existing
        val created = RemotePeer(id = id, name = "Peer $id", initialPosition = initialPosition)
        peersById[id] = created
        observablePeers.add(created)
        bump()
        return created
    }

    fun remove(id: String) {
        peersById.remove(id)?.let {
            observablePeers.remove(it)
            bump()
        }
    }

    fun forEach(action: (RemotePeer) -> Unit) {
        peersById.values.forEach(action)
    }

    fun snapshot(): List<RemotePeer> = peersById.values.toList()

    fun clear() {
        if (peersById.isEmpty() && observablePeers.isEmpty()) return
        peersById.clear()
        observablePeers.clear()
        bump()
    }

    /** Signal that a peer's observable fields changed; bumps [revision]. */
    fun markChanged() {
        bump()
    }

    private fun bump() {
        _revision.value = _revision.value + 1
    }
}
