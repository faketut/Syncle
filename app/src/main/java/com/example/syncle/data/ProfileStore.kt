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

    private fun defaultNickname(): String {
        val suffix = (1..4).map { ALPHABET.random() }.joinToString("")
        return "Syncle-$suffix"
    }

    private fun defaultColor(): String = COLORS.random()

    private companion object {
        const val PREFS = "syncle.profile"
        const val KEY_NICK = "nickname"
        const val KEY_COLOR = "color"
        const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val COLORS = listOf(
            "#4F8EF7", "#F78E4F", "#7EC845", "#C8456F",
            "#9B59B6", "#1ABC9C", "#E67E22", "#34495E"
        )
    }
}
