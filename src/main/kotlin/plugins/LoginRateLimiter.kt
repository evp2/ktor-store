package com.github.evp2.plugins

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple in-memory IP-based rate limiter for the /login endpoint.
 * - Allows up to maxAttempts incorrect tries within window
 * - On reaching the limit, bans the IP for banDuration
 */
object LoginRateLimiter {
    data class State(
        var attempts: Int = 0,
        var firstAttemptAt: Instant = Instant.EPOCH,
        var bannedUntil: Instant = Instant.EPOCH
    )

    // Defaults; may be overridden by application config if desired
    @Volatile var maxAttempts: Int = 3
    @Volatile var window: Duration = Duration.ofMinutes(10)
    @Volatile var banDuration: Duration = Duration.ofMinutes(15)

    private val states = ConcurrentHashMap<String, State>()

    fun isBanned(ip: String): Boolean {
        val now = Instant.now()
        val st = states[ip] ?: return false
        return st.bannedUntil.isAfter(now)
    }

    fun remainingBanSeconds(ip: String): Long {
        val now = Instant.now()
        val st = states[ip] ?: return 0
        return if (st.bannedUntil.isAfter(now)) Duration.between(now, st.bannedUntil).seconds else 0
    }

    fun recordFailure(ip: String) {
        val now = Instant.now()
        val st = states.computeIfAbsent(ip) { State() }
        synchronized(st) {
            // If outside window, reset
            if (st.firstAttemptAt == Instant.EPOCH || Duration.between(st.firstAttemptAt, now) > window) {
                st.firstAttemptAt = now
                st.attempts = 0
            }
            st.attempts += 1
            if (st.attempts >= maxAttempts) {
                st.bannedUntil = now.plus(banDuration)
                // Reset attempts after ban to avoid immediate re-ban after ban expiry unless more failures occur
                st.attempts = 0
                st.firstAttemptAt = Instant.EPOCH
            }
        }
    }

    fun recordSuccess(ip: String) {
        val st = states[ip] ?: return
        synchronized(st) {
            st.attempts = 0
            st.firstAttemptAt = Instant.EPOCH
        }
    }

    /**
     * Test support: clears all stored limiter state. Use only from tests to avoid cross-test interference.
     */
    fun resetAll() {
        states.clear()
    }
}
