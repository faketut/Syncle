package com.example.syncle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.syncle.data.MapRepository
import com.example.syncle.data.ProfileStore
import com.example.syncle.domain.SyncleLog
import com.example.syncle.ui.SyncleScreenHost
import com.example.syncle.ui.state.*
import com.example.syncle.viewmodel.SyncleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.OptIn

class MainActivity : ComponentActivity() {
    private val viewModel: SyncleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    runCatching {
                        val jsonString = assets.open("map_config.json").bufferedReader().use { it.readText() }
                        MapRepository().parseJsonConfig(jsonString)
                    }
                }
            result.onSuccess { config -> viewModel.setMapConfig(config) }
                .onFailure { e ->
                    SyncleLog.e("loadMapConfig failed", e)
                    viewModel.reportStartupError("Failed to load map_config.json: ${e.message}")
                }
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    SyncleApp(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncleApp(viewModel: SyncleViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mapConfig = viewModel.getMapConfig()

    var permissionsGranted by remember { mutableStateOf(false) }
    var permissionCheckDone by remember { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
            onResult = { permissions ->
                val audioGranted = permissions[android.Manifest.permission.RECORD_AUDIO] ?: false
                val cameraGranted = permissions[android.Manifest.permission.CAMERA] ?: false
                permissionsGranted = audioGranted && cameraGranted
                permissionCheckDone = true
            },
        )

    LaunchedEffect(Unit) {
        val audioCheck = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
        val cameraCheck = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
        if (audioCheck == android.content.pm.PackageManager.PERMISSION_GRANTED &&
            cameraCheck == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permissionsGranted = true
            permissionCheckDone = true
        } else {
            permissionLauncher.launch(
                arrayOf(android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.CAMERA),
            )
        }
    }

    if (!permissionCheckDone) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (!permissionsGranted) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.permission_rationale),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                permissionLauncher.launch(
                    arrayOf(android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.CAMERA),
                )
            }) {
                Text(stringResource(R.string.permission_grant))
            }
        }
        return
    }

    LaunchedEffect(Unit) {
        viewModel.autoFetchSession(context)
    }

    if (!uiState.mapReady || mapConfig == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val connection = uiState.connection
    if (connection.startupError != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Critical Error: ${connection.startupError}", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    if (connection.status != ConnectionStatus.CONNECTED &&
        connection.status != ConnectionStatus.RECONNECTING
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Syncle - Spatial Workstream",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 32.dp),
            )

            if (connection.status == ConnectionStatus.CONNECTING || connection.isAutoFetching) {
                CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
                Text(if (connection.isAutoFetching) "Fetching Sandbox Token..." else "Connecting to LiveKit...")
            } else {
                OutlinedTextField(
                    value = connection.nickname,
                    onValueChange = { viewModel.setNickname(it) },
                    label = { Text("Nickname") },
                    isError = connection.nicknameError != null,
                    supportingText = {
                        connection.nicknameError?.let { Text(it) }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text("Character", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                CharacterPickerRow(
                    selectedId = connection.character,
                    onSelect = { viewModel.setCharacter(it) },
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = connection.room,
                    onValueChange = { viewModel.setRoom(it) },
                    label = { Text("Room") },
                    isError = connection.roomError != null,
                    supportingText = {
                        connection.roomError?.let { Text(it) }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = connection.url,
                    onValueChange = { viewModel.setUrl(it) },
                    label = { Text("LiveKit Server URL") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = connection.token,
                    onValueChange = { viewModel.setToken(it) },
                    label = { Text("Access Token") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Paste JWT Token here...") },
                )

                if (connection.status == ConnectionStatus.ERROR) {
                    Text(
                        text =
                            connection.lastConnectError
                                ?: "Connection failed. Check URL/token or server status.",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        if (connection.token.isNotEmpty()) viewModel.connect(context)
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled =
                        connection.token.isNotEmpty() &&
                            ProfileStore.isValidNickname(connection.nickname) &&
                            ProfileStore.isValidRoom(connection.room.trim()),
                ) {
                    Text("Join Room")
                }
            }
        }
    } else {
        SyncleScreenHost(mapConfig = mapConfig, viewModel = viewModel)
    }
}

/**
 * Tappable row of pixel-art characters (see [ProfileStore.CHARACTERS]). Each
 * tile shows the sprite tinted with that character's primary color; selecting
 * one publishes the choice into UI state and replaces the prior accent-color
 * picker.
 */
@Composable
private fun CharacterPickerRow(
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        com.example.syncle.data.ProfileStore.CHARACTERS.forEach { ch ->
            val isSelected = ch.id == selectedId
            val swatch =
                try {
                    androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(ch.color))
                } catch (_: IllegalArgumentException) {
                    MaterialTheme.colorScheme.primary
                }
            androidx.compose.foundation.layout.Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .background(
                            color = swatch.copy(alpha = 0.18f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        )
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color =
                                if (isSelected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        )
                        .clickable { onSelect(ch.id) },
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                com.example.syncle.ui.PixelSpriteView(
                    sprite = ch.sprite,
                    primary = swatch,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}
