package com.example.syncle.domain

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import com.example.syncle.model.RemotePeer

class PeerRegistry {
    private val peersById = mutableMapOf<String, RemotePeer>()
    val observablePeers: SnapshotStateList<RemotePeer> = mutableStateListOf()

    fun get(id: String): RemotePeer? = peersById[id]

    fun getOrCreate(id: String, initialPosition: Offset = Offset.Zero): RemotePeer {
        return peersById.getOrPut(id) {
            RemotePeer(id = id, name = "Peer $id", initialPosition = initialPosition).also {
                observablePeers.add(it)
            }
        }
    }

    fun remove(id: String) {
        peersById.remove(id)?.let { observablePeers.remove(it) }
    }

    fun forEach(action: (RemotePeer) -> Unit) {
        peersById.values.forEach(action)
    }

    fun snapshot(): List<RemotePeer> = peersById.values.toList()

    fun clear() {
        peersById.clear()
        observablePeers.clear()
    }
}
