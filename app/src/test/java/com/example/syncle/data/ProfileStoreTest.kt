package com.example.syncle.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure validation tests — exercise the companion-object validators that the
 * UI relies on for inline error messages and Join-button enablement. Keeps
 * the room/nickname contract honest without spinning up Android `Context`.
 */
class ProfileStoreTest {

    @Test
    fun roomRegex_acceptsCanonical() {
        assertTrue(ProfileStore.isValidRoom("syncle-office"))
        assertTrue(ProfileStore.isValidRoom("team-alpha-42"))
        assertTrue(ProfileStore.isValidRoom("abc"))
        assertTrue(ProfileStore.isValidRoom("a".repeat(64)))
    }

    @Test
    fun roomRegex_rejectsBoundaryAndIllegal() {
        // Too short
        assertFalse(ProfileStore.isValidRoom("ab"))
        // Too long (65)
        assertFalse(ProfileStore.isValidRoom("a".repeat(65)))
        // Uppercase
        assertFalse(ProfileStore.isValidRoom("UPPER"))
        // Whitespace
        assertFalse(ProfileStore.isValidRoom("bad room"))
        // Slash / other punctuation
        assertFalse(ProfileStore.isValidRoom("bad/slash"))
        assertFalse(ProfileStore.isValidRoom("dot.name"))
        // Empty
        assertFalse(ProfileStore.isValidRoom(""))
    }

    @Test
    fun nicknameValidator_trimsAndBounds() {
        assertTrue(ProfileStore.isValidNickname("Alice"))
        assertTrue(ProfileStore.isValidNickname("  Bob  ")) // trimmed
        assertTrue(ProfileStore.isValidNickname("a".repeat(ProfileStore.NICKNAME_MAX_LEN)))

        assertFalse(ProfileStore.isValidNickname(""))
        assertFalse(ProfileStore.isValidNickname("   "))
        assertFalse(ProfileStore.isValidNickname("a".repeat(ProfileStore.NICKNAME_MAX_LEN + 1)))
    }

    @Test
    fun palette_isNonEmptyAndHexFormatted() {
        val palette = ProfileStore.PALETTE
        assertTrue("palette must not be empty", palette.isNotEmpty())
        val hex = Regex("^#[0-9A-Fa-f]{6}$")
        palette.forEach { color ->
            assertTrue("palette entry $color must be #RRGGBB", hex.matches(color))
        }
        // No duplicates — the picker UI relies on this for stable selection.
        assertEquals(palette.size, palette.distinct().size)
    }

    @Test
    fun defaultRoom_satisfiesRegex() {
        assertTrue(ProfileStore.isValidRoom(ProfileStore.DEFAULT_ROOM))
    }
}
