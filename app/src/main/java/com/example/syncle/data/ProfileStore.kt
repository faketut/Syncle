package com.example.syncle.data

import android.content.Context

data class Profile(val nickname: String, val color: String)

/**
 * User-visible profile (nickname, accent color). Populated with sensible
 * defaults on first launch; persisted across sessions so the same user keeps
 * the same display identity. Editable later via UI.
 */
class ProfileStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(): Profile {
        val nick = prefs.getString(KEY_NICK, null) ?: defaultNickname().also {
            prefs.edit().putString(KEY_NICK, it).apply()
        }
        val color = prefs.getString(KEY_COLOR, null) ?: defaultColor().also {
            prefs.edit().putString(KEY_COLOR, it).apply()
        }
        return Profile(nick, color)
    }

    fun update(profile: Profile) {
        prefs.edit()
            .putString(KEY_NICK, profile.nickname)
            .putString(KEY_COLOR, profile.color)
            .apply()
    }

    /** Last room the user joined. Defaults to [DEFAULT_ROOM] for back-compat. */
    fun getRoom(): String =
        prefs.getString(KEY_ROOM, null)?.takeIf { isValidRoom(it) } ?: DEFAULT_ROOM

    /**
     * Persist the room iff it matches [ROOM_REGEX]. Returns true when stored,
     * false when the input was rejected so callers can surface a validation error.
     */
    fun setRoom(room: String): Boolean {
        if (!isValidRoom(room)) return false
        prefs.edit().putString(KEY_ROOM, room).apply()
        return true
    }

    private fun defaultNickname(): String {
        val suffix = (1..4).map { ALPHABET.random() }.joinToString("")
        return "Syncle-$suffix"
    }

    private fun defaultColor(): String = COLORS.random()

    companion object {
        const val DEFAULT_ROOM = "syncle-office"
        val ROOM_REGEX = Regex("^[a-z0-9-]{3,64}$")
        fun isValidRoom(room: String): Boolean = ROOM_REGEX.matches(room)

        /** Visible nickname must be 1..32 chars after trimming. */
        const val NICKNAME_MAX_LEN = 32
        fun isValidNickname(nick: String): Boolean {
            val t = nick.trim()
            return t.isNotEmpty() && t.length <= NICKNAME_MAX_LEN
        }

        /** Accent color palette exposed for the profile editor UI. */
        val PALETTE: List<String> = listOf(
            "#4F8EF7", "#F78E4F", "#7EC845", "#C8456F",
            "#9B59B6", "#1ABC9C", "#E67E22", "#34495E"
        )

        private const val PREFS = "syncle.profile"
        private const val KEY_NICK = "nickname"
        private const val KEY_COLOR = "color"
        private const val KEY_ROOM = "room"
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private val COLORS = PALETTE
    }
}
