package com.example.syncle.data

import android.content.Context
import com.example.syncle.ui.PixelSprite

data class Profile(
    val nickname: String,
    val color: String,
    /** Pixel-art character id (see [ProfileStore.CHARACTERS]). */
    val character: String,
)

/**
 * Lightweight catalog of pixel-art characters shown in the pre-join picker.
 * Each entry bundles the sprite grid with its primary color so picking a
 * character also fixes the user's accent color (no separate picker).
 */
data class Character(
    val id: String,
    val label: String,
    val color: String,
    val sprite: PixelSprite,
)

/**
 * User-visible profile (nickname, accent color). Populated with sensible
 * defaults on first launch; persisted across sessions so the same user keeps
 * the same display identity. Editable later via UI.
 */
class ProfileStore(context: Context) {
    private val prefs =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(): Profile {
        val nick =
            prefs.getString(KEY_NICK, null) ?: defaultNickname().also {
                prefs.edit().putString(KEY_NICK, it).apply()
            }
        val character =
            prefs.getString(KEY_CHARACTER, null)
                ?.takeIf { id -> CHARACTERS.any { it.id == id } }
                ?: defaultCharacter().id.also {
                    prefs.edit().putString(KEY_CHARACTER, it).apply()
                }
        val color = characterById(character).color
        return Profile(nick, color, character)
    }

    fun update(profile: Profile) {
        prefs.edit()
            .putString(KEY_NICK, profile.nickname)
            .putString(KEY_CHARACTER, profile.character)
            .putString(KEY_COLOR, profile.color)
            .apply()
    }

    /** Last room the user joined. Defaults to [DEFAULT_ROOM] for back-compat. */
    fun getRoom(): String = prefs.getString(KEY_ROOM, null)?.takeIf { isValidRoom(it) } ?: DEFAULT_ROOM

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

    private fun defaultCharacter(): Character = CHARACTERS.random()

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

        /** Pixel-art characters offered on the pre-join screen. */
        val CHARACTERS: List<Character> =
            listOf(
                Character(
                    id = "knight",
                    label = "Knight",
                    color = "#4F8EF7",
                    sprite =
                        PixelSprite(
                            listOf(
                                "..3333..",
                                ".311113.",
                                "31222213",
                                "31232213",
                                "31111113",
                                "31111113",
                                ".311113.",
                                "..3.3...",
                            ),
                        ),
                ),
                Character(
                    id = "wizard",
                    label = "Wizard",
                    color = "#9B59B6",
                    sprite =
                        PixelSprite(
                            listOf(
                                "...3....",
                                "..313...",
                                ".31113..",
                                "3111113.",
                                "31222213",
                                "31232213",
                                "31111113",
                                ".3.3.3..",
                            ),
                        ),
                ),
                Character(
                    id = "robot",
                    label = "Robot",
                    color = "#1ABC9C",
                    sprite =
                        PixelSprite(
                            listOf(
                                "...32...",
                                "..3113..",
                                "33111133",
                                "31232213",
                                "31222213",
                                "31111113",
                                "33111133",
                                "3.3..3.3",
                            ),
                        ),
                ),
                Character(
                    id = "cat",
                    label = "Cat",
                    color = "#F78E4F",
                    sprite =
                        PixelSprite(
                            listOf(
                                "31...13.",
                                "311.113.",
                                "3111113.",
                                "31232213",
                                "31222213",
                                "31111113",
                                "31111113",
                                ".3...3..",
                            ),
                        ),
                ),
                Character(
                    id = "ninja",
                    label = "Ninja",
                    color = "#34495E",
                    sprite =
                        PixelSprite(
                            listOf(
                                "..3333..",
                                ".311113.",
                                "31111113",
                                "32222223",
                                "31232213",
                                "31111113",
                                ".311113.",
                                "..3.3...",
                            ),
                        ),
                ),
                Character(
                    id = "fox",
                    label = "Fox",
                    color = "#E67E22",
                    sprite =
                        PixelSprite(
                            listOf(
                                "31...13.",
                                "311.1133",
                                "3111113.",
                                "31222213",
                                "31223213",
                                "31222213",
                                ".311113.",
                                "..3.3...",
                            ),
                        ),
                ),
            )

        fun characterById(id: String): Character {
            return CHARACTERS.firstOrNull { it.id == id } ?: CHARACTERS.first()
        }

        /**
         * Reverse-lookup: find the character whose accent matches [color] (hex,
         * case-insensitive). Used as a fallback when a remote peer's character
         * id hasn't arrived yet but we already have their color from the
         * snapshot, so we can still draw their pixel sprite.
         */
        fun characterByColor(color: String?): Character? {
            val c = color?.takeIf { it.isNotBlank() } ?: return null
            return CHARACTERS.firstOrNull { it.color.equals(c, ignoreCase = true) }
        }

        private const val PREFS = "syncle.profile"
        private const val KEY_NICK = "nickname"
        private const val KEY_COLOR = "color"
        private const val KEY_CHARACTER = "character"
        private const val KEY_ROOM = "room"
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }
}
