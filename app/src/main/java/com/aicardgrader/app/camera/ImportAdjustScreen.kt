package com.aicardgrader.app.camera

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val CARD_ASPECT = 2.5f / 3.5f // width / height
private const val MIN_USER_SCALE = 1f
private const val MAX_USER_SCALE = 6f

/**
 * Lets the user pan/zoom an imported photo (e.g. saved from an eBay
 * listing) inside a fixed card-shaped frame before it's cropped, so a
 * photo that isn't already tightly cropped to the card gets fed into
 * grading the same way a guided camera capture is: with the card filling
 * the frame. Confirming produces a new bitmap containing only what's
 * visible inside the frame, at a fixed output resolution.
 */
@Composable
fun ImportAdjustScreen(
    source: Bitmap,
    title: String,
    onConfirm: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    var frameSizePx by remember { mutableStateOf(IntSize.Zero) }
    var userScale by remember { mutableStateOf(MIN_USER_SCALE) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                "Pinch to zoom and drag to position the card inside the frame.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                // Gesture detection covers the whole available area, not just
                // the visual frame below: a real two-finger pinch usually
                // spreads wider than a ~300dp box, so if only the frame were
                // listening, one finger would often land outside it and the
                // gesture would degrade to a single-finger pan.
                .pointerInput(source) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (userScale * zoom).coerceIn(MIN_USER_SCALE, MAX_USER_SCALE)
                        userScale = newScale
                        offset = clampOffset(offset + pan, frameSizePx, source, newScale)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val frameWidthDp = 300.dp
            val frameHeightDp = frameWidthDp / CARD_ASPECT

            Box(
                modifier = Modifier
                    .size(frameWidthDp, frameHeightDp)
                    .onSizeChanged { frameSizePx = it }
                    .clipToBounds()
                    .background(Color.Black)
            ) {
                if (frameSizePx.width > 0 && frameSizePx.height > 0) {
                    val baseScale = coverScale(frameSizePx, source)
                    val totalScale = baseScale * userScale
                    val dispWidthPx = source.width * totalScale
                    val dispHeightPx = source.height * totalScale
                    val dispWidthDp = with(density) { dispWidthPx.toDp() }
                    val dispHeightDp = with(density) { dispHeightPx.toDp() }

                    Image(
                        bitmap = source.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(dispWidthDp, dispHeightDp)
                            .align(Alignment.Center)
                            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                    )
                }
            }

            // Frame border, drawn over the clipped image.
            Canvas(
                modifier = Modifier.size(frameWidthDp, frameHeightDp)
            ) {
                drawRect(
                    color = Color(0xFFFFC94A),
                    size = Size(size.width, size.height),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 3.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))
                    )
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    val cropped = cropToFrame(source, frameSizePx, userScale, offset)
                    onConfirm(cropped)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Use this photo")
            }
        }
    }
}

/** Scale at which the bitmap fully covers the frame (like ContentScale.Crop), before user zoom. */
private fun coverScale(frame: IntSize, bitmap: Bitmap): Float {
    if (frame.width == 0 || frame.height == 0) return 1f
    val scaleX = frame.width.toFloat() / bitmap.width
    val scaleY = frame.height.toFloat() / bitmap.height
    return maxOf(scaleX, scaleY)
}

/** Keeps the frame fully covered by the image at the given scale. */
private fun clampOffset(raw: Offset, frame: IntSize, bitmap: Bitmap, userScale: Float): Offset {
    if (frame.width == 0 || frame.height == 0) return Offset.Zero
    val totalScale = coverScale(frame, bitmap) * userScale
    val dispWidth = bitmap.width * totalScale
    val dispHeight = bitmap.height * totalScale
    val maxX = ((dispWidth - frame.width) / 2f).coerceAtLeast(0f)
    val maxY = ((dispHeight - frame.height) / 2f).coerceAtLeast(0f)
    return Offset(raw.x.coerceIn(-maxX, maxX), raw.y.coerceIn(-maxY, maxY))
}

/** Renders exactly what's visible inside the frame into a new, fixed-resolution bitmap. */
private fun cropToFrame(source: Bitmap, frame: IntSize, userScale: Float, offset: Offset): Bitmap {
    if (frame.width == 0 || frame.height == 0) return source

    val totalScale = coverScale(frame, source) * userScale
    val dispWidth = source.width * totalScale
    val dispHeight = source.height * totalScale
    // Top-left of the displayed (scaled) image, relative to the frame's top-left.
    val imageLeft = frame.width / 2f - dispWidth / 2f + offset.x
    val imageTop = frame.height / 2f - dispHeight / 2f + offset.y

    val srcLeft = (-imageLeft / totalScale)
    val srcTop = (-imageTop / totalScale)
    val srcRight = srcLeft + frame.width / totalScale
    val srcBottom = srcTop + frame.height / totalScale

    val left = srcLeft.roundToInt().coerceIn(0, source.width - 1)
    val top = srcTop.roundToInt().coerceIn(0, source.height - 1)
    val right = srcRight.roundToInt().coerceIn(left + 1, source.width)
    val bottom = srcBottom.roundToInt().coerceIn(top + 1, source.height)

    val cropped = Bitmap.createBitmap(source, left, top, right - left, bottom - top)

    val outputWidth = 1000
    val outputHeight = (outputWidth / CARD_ASPECT).roundToInt()
    val scaled = Bitmap.createScaledBitmap(cropped, outputWidth, outputHeight, true)
    if (scaled !== cropped) cropped.recycle()
    return scaled
}
