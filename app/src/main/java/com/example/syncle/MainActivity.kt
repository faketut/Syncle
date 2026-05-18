package com.example.syncle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import kotlin.OptIn
import com.example.syncle.model.*
import com.example.syncle.ui.SyncleScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val mapConfig = try {
            val jsonString = assets.open("map_config.json").bufferedReader().use { it.readText() }
            MapRepository().parseJsonConfig(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            MapConfig(
                mapName = "Office Blueprint (Fallback)",
                backgroundImage = "room1.png",
                walkableAreas = listOf(Rect(0f, 0f, 2000f, 2000f)),
                tables = listOf(InteractableItem("table_1", Rect(300f, 300f, 450f, 400f))),
                privateAreas = listOf(PrivateArea("meeting_room", Rect(600f, 100f, 900f, 400f))),
                collisionSettings = CollisionSettings("AABB", true)
            )
        }

        val viewModel = ViewModelProvider(this)[SyncleViewModel::class.java]

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SyncleApp(mapConfig, viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncleApp(mapConfig: MapConfig, viewModel: SyncleViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var permissionsGranted by remember { mutableStateOf(false) }
    var permissionCheckDone by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val audioGranted = permissions[android.Manifest.permission.RECORD_AUDIO] ?: false
            val cameraGranted = permissions[android.Manifest.permission.CAMERA] ?: false
            permissionsGranted = audioGranted && cameraGranted
            permissionCheckDone = true
        }
    )

    // Auto check and request permissions on launch
    LaunchedEffect(Unit) {
        val audioCheck = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
        val cameraCheck = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
        if (audioCheck == android.content.pm.PackageManager.PERMISSION_GRANTED && cameraCheck == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionsGranted = true
            permissionCheckDone = true
        } else {
            permissionLauncher.launch(arrayOf(android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.CAMERA))
        }
    }

    if (!permissionCheckDone) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (!permissionsGranted && !viewModel.offlineMode) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Syncle 需要麦克风与相机权限以提供空间音视频协作功能",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { permissionLauncher.launch(arrayOf(android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.CAMERA)) }) {
                Text("授予权限")
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { viewModel.offlineMode = true }) {
                Text("离线模式 (预览)")
            }
        }
        return
    }

    // Auto-fetch connection details on launch
    LaunchedEffect(Unit) {
        viewModel.autoFetchSandboxDetails()
    }

    val status = viewModel.connectionStatus
    val startupError = viewModel.startupError

    if (startupError != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Critical Error: $startupError", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    if (!viewModel.offlineMode && status != ConnectionStatus.CONNECTED) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Syncle - Spatial Workstream",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            if (status == ConnectionStatus.CONNECTING || viewModel.isAutoFetching) {
                CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
                Text(if (viewModel.isAutoFetching) "Fetching Sandbox Token..." else "Connecting to LiveKit...")
            } else {
                OutlinedTextField(
                    value = viewModel.url,
                    onValueChange = { viewModel.url = it },
                    label = { Text("LiveKit Server URL") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = viewModel.token,
                    onValueChange = { viewModel.token = it },
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
                    onClick = { if (viewModel.token.isNotEmpty()) viewModel.connect(context, mapConfig) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = viewModel.token.isNotEmpty()
                ) {
                    Text("Join Room")
                }
                
                TextButton(
                    onClick = { viewModel.offlineMode = true },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Offline Mode (Preview Only)")
                }
            }
        }
    } else {
        SyncleScreen(
            mapConfig = mapConfig,
            avatarState = viewModel.avatarState,
            remotePeers = viewModel.remotePeers
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
fun SynclePreview() {
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
            SyncleApp(mockMapConfig, SyncleViewModel())
        }
    }
}
