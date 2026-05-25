package com.example.syncle.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectPolicyTest {

    @Test
    fun `delay grows roughly exponentially for early attempts`() {
        // random=0.5 -> jitter term = 0 -> exact base
        assertEquals(1_000L, ReconnectPolicy.delayMsForAttempt(1, random = 0.5))
        assertEquals(2_000L, ReconnectPolicy.delayMsForAttempt(2, random = 0.5))
        assertEquals(4_000L, ReconnectPolicy.delayMsForAttempt(3, random = 0.5))
        assertEquals(8_000L, ReconnectPolicy.delayMsForAttempt(4, random = 0.5))
        assertEquals(16_000L, ReconnectPolicy.delayMsForAttempt(5, random = 0.5))
    }

    @Test
    fun `delay caps at MAX_MS regardless of attempt`() {
        // 2^6 base would be 32_000 ms but MAX_MS = 30_000.
        assertEquals(30_000L, ReconnectPolicy.delayMsForAttempt(6, random = 0.5))
        assertEquals(30_000L, ReconnectPolicy.delayMsForAttempt(20, random = 0.5))
    }

    @Test
    fun `jitter stays within plus or minus 25 percent of the base`() {
        val base = 8_000L // attempt 4
        // random=0.0 -> -25% jitter -> 6000
        assertEquals(6_000L, ReconnectPolicy.delayMsForAttempt(4, random = 0.0))
        // random just under 1.0 -> +25% jitter, almost
        val high = ReconnectPolicy.delayMsForAttempt(4, random = 0.9999)
        assertTrue("expected ~10000, was $high", high in 9_990L..10_000L)
        // Random sample stays inside the band
        repeat(50) {
            val d = ReconnectPolicy.delayMsForAttempt(4)
            assertTrue("delay $d out of band for attempt 4", d in 6_000L..10_000L)
        }
        // suppress unused warning
        check(base == 8_000L)
    }

    @Test
    fun `delay is never negative`() {
        repeat(100) {
            val d = ReconnectPolicy.delayMsForAttempt(1)
            assertTrue("attempt 1 produced negative delay $d", d >= 0)
        }
    }

    @Test
    fun `shouldGiveUp respects MAX_ATTEMPTS`() {
        assertEquals(false, ReconnectPolicy.shouldGiveUp(1))
        assertEquals(false, ReconnectPolicy.shouldGiveUp(ReconnectPolicy.MAX_ATTEMPTS))
        assertEquals(true, ReconnectPolicy.shouldGiveUp(ReconnectPolicy.MAX_ATTEMPTS + 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `attempt zero is rejected`() {
        ReconnectPolicy.delayMsForAttempt(0)
    }
}
