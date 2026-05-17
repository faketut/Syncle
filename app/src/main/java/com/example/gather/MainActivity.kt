package com.example.gather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import kotlin.OptIn
import com.example.gather.model.*
import com.example.gather.ui.GatherScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val defaultMapConfig = MapConfig(
            mapName = "Office Blueprint",
            backgroundImage = "room1.png",
            walkableAreas = listOf(Rect(0f, 0f, 2000f, 2000f)),
            tables = listOf(InteractableItem("table_1", Rect(300f, 300f, 450f, 400f))),
            privateAreas = listOf(PrivateArea("meeting_room", Rect(600f, 100f, 900f, 400f))),
            collisionSettings = CollisionSettings("AABB", true)
        )

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GatherApp(defaultMapConfig)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GatherApp(mapConfig: MapConfig) {
    var offlineMode by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var isAutoFetching by remember { mutableStateOf(false) }
    var startupError by remember { mutableStateOf<String?>(null) }
    
    val avatarState = remember { AvatarState(initialPosition = Offset(100f, 100f)) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val roomManager = remember { RoomManager(context, avatarState, mapConfig) }
    val authRepository = remember { AuthRepository() }

    // Auto-fetch connection details on launch
    LaunchedEffect(Unit) {
        try {
            if (url.isEmpty() && token.isEmpty()) {
                isAutoFetching = true
                println("Gather: Starting auto-fetch...")
                val details = authRepository.fetchSandboxConnectionDetails()
                if (details != null) {
                    url = details.serverUrl
                    token = details.token
                    println("Gather: Auto-fetch success: $url")
                } else {
                    println("Gather: Auto-fetch returned null (check local.properties)")
                }
                isAutoFetching = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            startupError = "Startup Error: ${e.message}"
            isAutoFetching = false
        }
    }

    val status = roomManager.connectionStatus

    if (startupError != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Critical Error: $startupError", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    if (!offlineMode && status != ConnectionStatus.CONNECTED) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Gather - Spatial Workstream",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            if (status == ConnectionStatus.CONNECTING || isAutoFetching) {
                CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
                Text(if (isAutoFetching) "Fetching Sandbox Token..." else "Connecting to LiveKit...")
            } else {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("LiveKit Server URL") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Access Token") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Paste JWT Token here...") }
                )

                if (status == ConnectionStatus.ERROR) {
                    Text(
                        text = "Connection Failed. Check URL/Token or Server status.",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { if (token.isNotEmpty()) roomManager.connect(url, token) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = token.isNotEmpty()
                ) {
                    Text("Join Room")
                }
                
                TextButton(
                    onClick = { offlineMode = true },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Offline Mode (Preview Only)")
                }
            }
        }
    } else {
        GatherScreen(
            mapConfig = mapConfig,
            avatarState = avatarState,
            remotePeers = roomManager.remotePeers
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
fun GatherPreview() {
    val mockMapConfig = MapConfig(
        mapName = "Office Blueprint",
        backgroundImage = "room1.png",
        walkableAreas = listOf(Rect(0f, 0f, 1000f, 1000f)),
        tables = listOf(InteractableItem("table_1", Rect(300f, 300f, 450f, 400f))),
        privateAreas = listOf(PrivateArea("meeting_room", Rect(600f, 100f, 900f, 400f))),
        collisionSettings = CollisionSettings("AABB", true)
    )

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            GatherApp(mockMapConfig)
        }
    }
}
