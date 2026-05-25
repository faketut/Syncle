package com.example.syncle.data

import com.example.syncle.BuildConfig
import com.example.syncle.domain.SyncleLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ConnectionDetails(
    val serverUrl: String,
    val token: String,
    val userId: String,
    val nickname: String,
    val color: String,
    /** Epoch millis when the JWT expires. 0 if the backend didn't return one. */
    val expiresAt: Long,
)

/**
 * Talks to the Syncle backend ([server/]) to exchange a stable deviceId +
 * desired nickname for a LiveKit JWT bound to a persistent userId. Replaces
 * the previous LiveKit Sandbox flow.
 */
class AuthRepository(
    private val backendUrl: String = BuildConfig.SYNCLE_BACKEND_URL,
    private val client: OkHttpClient = defaultClient,
) {
    suspend fun fetchSession(
        deviceId: String,
        nickname: String,
        color: String,
        room: String,
    ): ConnectionDetails? = withContext(Dispatchers.IO) {
        val base = backendUrl.trim().ifEmpty {
            SyncleLog.w("SYNCLE_BACKEND_URL is empty. Set syncle.backend_url in local.properties.")
            return@withContext null
        }
        val sessionsUrl = base.trimEnd('/') + "/v1/sessions"
        if (sessionsUrl.toHttpUrlOrNull() == null) {
            SyncleLog.w("SYNCLE_BACKEND_URL is not a valid URL: $base")
            return@withContext null
        }

        val body = JSONObject().apply {
            put("deviceId", deviceId)
            put("nickname", nickname)
            put("color", color)
            put("room", room)
        }
        val request = Request.Builder()
            .url(sessionsUrl)
            .post(body.toString().toRequestBody(JSON))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val payload = response.body?.string()
                if (!response.isSuccessful || payload.isNullOrEmpty()) {
                    SyncleLog.w("fetchSession failed http=${response.code} body=${payload ?: "<empty>"}")
                    return@withContext null
                }
                val json = JSONObject(payload)
                ConnectionDetails(
                    serverUrl = json.getString("serverUrl"),
                    token = json.getString("token"),
                    userId = json.getString("userId"),
                    nickname = json.optString("nickname", nickname),
                    color = json.optString("color", color),
                    expiresAt = json.optLong("expiresAt", 0L),
                )
            }
        } catch (e: Exception) {
            SyncleLog.e("fetchSession failed", e)
            null
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
