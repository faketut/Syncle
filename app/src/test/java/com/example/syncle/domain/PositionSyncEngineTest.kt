package com.example.syncle.domain

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PositionSyncEngineTest {
    @Test
    fun encode_skipsWhenPositionUnchanged() {
        val engine = PositionSyncEngine()
        val pos = Offset(10f, 20f)
        assertNotNull(engine.encodeIfMoved(pos, 1L))
        assertNull(engine.encodeIfMoved(pos, 2L))
    }

    @Test
    fun roundTrip_binaryPacket() {
        val engine = PositionSyncEngine()
        val pos = Offset(42.5f, -12.25f)
        val seq = 99L
        val encoded = engine.encodeIfMoved(pos, seq)!!
        val decoded = engine.decode(encoded)
        assertNotNull(decoded)
        assertEquals(pos.x, decoded!!.x, 0.001f)
        assertEquals(pos.y, decoded.y, 0.001f)
        assertEquals(seq, decoded.seq)
    }

    @Test
    fun decode_nonBinaryReturnsNull() {
        val engine = PositionSyncEngine()
        val json = """{"type":"position","x":1.5,"y":2.5,"seq":7}""".toByteArray(Charsets.UTF_8)
        assertNull(engine.decode(json))
        assertNull(engine.decode(byteArrayOf()))
        assertNull(engine.decode(byteArrayOf(99)))
    }

    @Test
    fun nextSequence_isMonotonicAndResets() {
        val engine = PositionSyncEngine()
        val first = engine.nextSequence()
        val second = engine.nextSequence()
        val third = engine.nextSequence()
        assertEquals(first + 1, second)
        assertEquals(second + 1, third)
        engine.reset()
        assertEquals(first, engine.nextSequence())
    }
}
