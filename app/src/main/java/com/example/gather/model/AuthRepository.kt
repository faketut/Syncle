package com.example.gather.model

import com.example.gather.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ConnectionDetails(
    val serverUrl: String,
    val token: String
)

class AuthRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun fetchSandboxConnectionDetails(
        roomName: String = "gather-office",
        participantName: String = "User_${(100..999).random()}"
    ): ConnectionDetails? = withContext(Dispatchers.IO) {
        try {
            val sandboxId = BuildConfig.LIVEKIT_SANDBOX_ID
            if (sandboxId.isEmpty()) return@withContext null

            val bodyJson = JSONObject().apply {
                put("room_name", roomName)
                put("participant_name", participantName)
            }

            val request = Request.Builder()
                .url("https://cloud-api.livekit.io/api/sandbox/connection-details")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .header("X-Sandbox-ID", sandboxId)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    val responseString = response.body!!.string()
                    val json = JSONObject(responseString)
                    ConnectionDetails(
                        serverUrl = json.getString("serverUrl"),
                        token = json.getString("participantToken")
                    )
                } else {
                    throw RuntimeException("HTTP ${response.code}: ${response.message}")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
