package com.example.syncle.domain

import kotlin.math.min
import kotlin.random.Random

/**
 * Pure exponential back-off with jitter for LiveKit reconnect attempts (#39).
 *
 * - attempt 1 -> ~1s
 * - attempt 2 -> ~2s
 * - ...
 * - attempts >= 6 -> capped at 30s
 *
 * Jitter is `±JITTER_RATIO * base`, so two devices that drop at the same
 * instant don't thunder back into the server in lockstep.
 *
 * After [MAX_ATTEMPTS] consecutive failures the ViewModel should give up
 * and surface an ERROR state so the user can retry manually.
 */
object ReconnectPolicy {
    const val BASE_MS: Long = 1_000
    const val MAX_MS: Long = 30_000
    const val MAX_ATTEMPTS: Int = 10
    const val JITTER_RATIO: Double = 0.25

    /**
     * Returns the delay (in milliseconds) before reconnect [attempt] should
     * fire. [attempt] is 1-based: the first retry is attempt=1.
     *
     * [random] is a Double in [0.0, 1.0) — exposed for deterministic tests.
     */
    fun delayMsForAttempt(
        attempt: Int,
        random: Double = Random.nextDouble(),
    ): Long {
        require(attempt >= 1) { "attempt must be >= 1, got $attempt" }
        val capped = min(attempt, 6) // 2^6 = 64s, but we clamp to MAX_MS below
        val base = BASE_MS shl (capped - 1) // 1s, 2s, 4s, 8s, 16s, 32s
        val clamped = min(base, MAX_MS) // -> 30s ceiling
        // Symmetric jitter: random in [-1, +1) * JITTER_RATIO * clamped
        val jitter = ((random * 2.0) - 1.0) * JITTER_RATIO * clamped
        return (clamped + jitter).toLong().coerceAtLeast(0L)
    }

    fun shouldGiveUp(attempt: Int): Boolean = attempt > MAX_ATTEMPTS
}
