package com.aicardgrader.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decodes a JPEG from disk off the main thread, downsampled for display. */
@Composable
fun rememberBitmapFromFile(path: String?, maxDimension: Int = 900): Bitmap? {
    val state = produceState<Bitmap?>(initialValue = null, key1 = path) {
        value = if (path == null) null else withContext(Dispatchers.IO) {
            decodeSampled(path, maxDimension)
        }
    }
    return state.value
}

private fun decodeSampled(path: String, maxDimension: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while ((bounds.outWidth / sample) > maxDimension || (bounds.outHeight / sample) > maxDimension) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(path, options)
}
