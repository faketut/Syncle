package com.example.syncle.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext

/**
 * Decoded background with the *logical* (pre-subsample) pixel dimensions
 * preserved. World-space coordinates must use [logicalSize] so they don't
 * shift when the actual bitmap is decoded at a smaller [Bitmap.Config] /
 * sample size to save memory.
 */
data class MapBackground(
    val image: ImageBitmap,
    val logicalSize: Size,
)

/** Loads JPEG/PNG/WebP from `assets/` via [assetFileName] (e.g. `room1.jpg`). */
@Composable
fun rememberMapBackground(assetFileName: String): MapBackground? {
    val context = LocalContext.current
    val maxPixelDim = remember {
        val dm = context.resources.displayMetrics
        // Cap output to ~2x the larger screen dimension. MapCamera scale is
        // typically <= 2 so this keeps sampling crisp without OOMing on
        // entry-level devices when room1.jpg is 4k+.
        maxOf(dm.widthPixels, dm.heightPixels) * 2
    }
    return remember(assetFileName, maxPixelDim) {
        decodeAssetBackground(context, assetFileName, maxPixelDim)
    }
}

private fun decodeAssetBackground(
    context: Context,
    assetFileName: String,
    maxPixelDim: Int,
): MapBackground? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open(assetFileName).use { BitmapFactory.decodeStream(it, null, bounds) }
        val logicalW = bounds.outWidth
        val logicalH = bounds.outHeight
        if (logicalW <= 0 || logicalH <= 0) return null

        val opts = BitmapFactory.Options().apply {
            inSampleSize = computeInSampleSize(logicalW, logicalH, maxPixelDim)
            // Most room blueprints are opaque JPEGs; RGB_565 halves memory
            // with no visible loss. If the asset advertises alpha, the decoder
            // will already pick a config that preserves it.
            inPreferredConfig = if (bounds.outMimeType == "image/png" || bounds.outMimeType == "image/webp") {
                Bitmap.Config.ARGB_8888
            } else {
                Bitmap.Config.RGB_565
            }
        }
        val bitmap = context.assets.open(assetFileName).use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        MapBackground(
            image = bitmap.asImageBitmap(),
            logicalSize = Size(logicalW.toFloat(), logicalH.toFloat()),
        )
    } catch (_: Exception) {
        null
    }
}

private fun computeInSampleSize(srcW: Int, srcH: Int, maxDim: Int): Int {
    if (maxDim <= 0) return 1
    var sample = 1
    var w = srcW
    var h = srcH
    while (w / 2 >= maxDim || h / 2 >= maxDim) {
        sample *= 2
        w /= 2
        h /= 2
    }
    return sample
}

/** Backwards-compatible accessor for call sites that only need the ImageBitmap. */
@Composable
fun rememberMapBackgroundImage(assetFileName: String): ImageBitmap? =
    rememberMapBackground(assetFileName)?.image
