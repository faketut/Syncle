package com.example.syncle.data

import android.content.Context
import java.util.UUID

/**
 * Persists a stable per-install device identifier used by the Syncle backend
 * to derive a deterministic userId across app restarts. Cleared on app data
 * wipe / uninstall — a fresh deviceId then yields a fresh userId.
 */
class DeviceIdStore(context: Context) {
    private val prefs =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getOrCreate(): String {
        prefs.getString(KEY, null)?.let { return it }
        val generated = "dev-" + UUID.randomUUID().toString()
        prefs.edit().putString(KEY, generated).apply()
        return generated
    }

    private companion object {
        const val PREFS = "syncle.device"
        const val KEY = "device_id"
    }
}
