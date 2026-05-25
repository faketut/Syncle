package com.example.syncle.data

import com.example.syncle.BuildConfig
import com.example.syncle.domain.SyncleLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class PeerSnapshot(
    val userId: String,
    val nickname: String,
    val color: String,
    val tableId: String?,
    val x: Float,
    val y: Float,
    val lastSeen: Long,
)

/**
 * Reads the persistent room state cached on the Syncle backend so a freshly
 * connecting client immediately sees the world (positions, tableIds, profiles)
 * instead of waiting for every peer to broadcast their next position packet.
 */
class SnapshotApi(
    private val backendUrl: String = BuildConfig.SYNCLE_BACKEND_URL,
    private val client: OkHttpClient = defaultClient,
) {
    suspend fun fetch(room: String): List<PeerSnapshot> =
        withContext(Dispatchers.IO) {
            val base = backendUrl.trim().trimEnd('/').ifEmpty { return@withContext emptyList() }
            val url = "$base/v1/rooms/$room/snapshot"
            try {
                client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                    val body = response.body?.string()
                    if (!response.isSuccessful || body.isNullOrEmpty()) {
                        SyncleLog.w("snapshot fetch failed http=${response.code}")
                        return@withContext emptyList()
                    }
                    val arr = JSONObject(body).optJSONArray("peers") ?: return@withContext emptyList()
                    buildList(arr.length()) {
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            add(
                                PeerSnapshot(
                                    userId = o.getString("userId"),
                                    nickname = o.optString("nickname", o.getString("userId")),
                                    color = o.optString("color", "#888888"),
                                    tableId = o.optString("tableId").takeIf { it.isNotEmpty() && it != "null" },
                                    x = o.getDouble("x").toFloat(),
                                    y = o.getDouble("y").toFloat(),
                                    lastSeen = o.optLong("lastSeen", 0L),
                                ),
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                SyncleLog.e("snapshot fetch failed", e)
                emptyList()
            }
        }

    private companion object {
        val defaultClient: OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
    }
}
