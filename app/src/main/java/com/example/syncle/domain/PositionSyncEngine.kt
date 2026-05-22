package com.example.syncle.domain

import androidx.compose.ui.geometry.Offset
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PositionSyncEngine {
    private val encodeBuffer = ByteBuffer.allocate(PACKET_SIZE).order(ByteOrder.LITTLE_ENDIAN)
    private var lastBroadcastPosition: Offset? = null
    // Monotonic per-instance counter; immune to wall-clock jumps.
    private var nextSeq: Long = 0L

    data class PositionPacket(val x: Float, val y: Float, val seq: Long)

    /** Allocates the next monotonic sequence id for an outgoing packet. */
    fun nextSequence(): Long = nextSeq++

    fun encodeIfMoved(position: Offset, seq: Long): ByteArray? {
        val last = lastBroadcastPosition
        if (last != null && last.x == position.x && last.y == position.y) {
            return null
        }
        lastBroadcastPosition = position
        encodeBuffer.clear()
        encodeBuffer.put(TYPE_POSITION)
        encodeBuffer.putFloat(position.x)
        encodeBuffer.putFloat(position.y)
        encodeBuffer.putLong(seq)
        encodeBuffer.flip()
        val out = ByteArray(PACKET_SIZE)
        encodeBuffer.get(out)
        return out
    }

    fun decode(data: ByteArray): PositionPacket? {
        if (data.isNotEmpty() && data[0] == TYPE_POSITION && data.size >= PACKET_SIZE) {
            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            buf.get()
            val x = buf.float
            val y = buf.float
            val seq = buf.long
            return PositionPacket(x, y, seq)
        }
        return null
    }

    fun reset() {
        lastBroadcastPosition = null
        nextSeq = 0L
    }

    companion object {
        const val TYPE_POSITION: Byte = 1
        const val PACKET_SIZE = 1 + 4 + 4 + 8 // 17 bytes
    }
}
