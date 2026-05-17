package com.example.gather.model

import com.example.gather.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ConnectionDetails(
    val serverUrl: String,
    val token: String
)

class AuthRepository {
    
    suspend fun fetchSandboxConnectionDetails(
        roomName: String = "gather-office",
        participantName: String = "User_${(100..999).random()}"
    ): ConnectionDetails? = withContext(Dispatchers.IO) {
        try {
            val sandboxId = BuildConfig.LIVEKIT_SANDBOX_ID
            if (sandboxId.isEmpty()) return@withContext null

            val url = URL("https://cloud-api.livekit.io/api/sandbox/connection-details")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("X-Sandbox-ID", sandboxId)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val body = JSONObject().apply {
                put("room_name", roomName)
                put("participant_name", participantName)
            }

            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                ConnectionDetails(
                    serverUrl = json.getString("serverUrl"),
                    token = json.getString("participantToken")
                )
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
