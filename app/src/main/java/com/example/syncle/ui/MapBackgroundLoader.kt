package com.example.syncle.ui

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext

/** Loads JPEG/PNG/WebP from `assets/` via [assetFileName] (e.g. `room1.jpg`). */
@Composable
fun rememberMapBackgroundImage(assetFileName: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(assetFileName) {
        try {
            context.assets.open(assetFileName).use { input ->
                BitmapFactory.decodeStream(input)?.asImageBitmap()
            }
        } catch (_: Exception) {
            null
        }
    }
}
