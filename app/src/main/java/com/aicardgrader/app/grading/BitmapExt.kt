package com.aicardgrader.app.grading

import android.graphics.Bitmap
import kotlin.math.roundToInt

/** Longest side an analysis bitmap is downscaled to before grading — keeps the
 *  pixel-array analyzers fast and memory-light without hurting estimate quality. */
private const val MAX_ANALYSIS_DIMENSION = 1400

fun Bitmap.toPixelImage(): PixelImage {
    val scaled = downscaleForAnalysis(this)
    val w = scaled.width
    val h = scaled.height
    val pixels = IntArray(w * h)
    scaled.getPixels(pixels, 0, w, 0, 0, w, h)
    if (scaled !== this) scaled.recycle()
    return PixelImage(w, h, pixels)
}

private fun downscaleForAnalysis(bitmap: Bitmap): Bitmap {
    val longest = maxOf(bitmap.width, bitmap.height)
    if (longest <= MAX_ANALYSIS_DIMENSION) return bitmap
    val scale = MAX_ANALYSIS_DIMENSION.toDouble() / longest
    val w = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
    val h = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, w, h, true)
}

/**
 * The capture-guide rectangle drawn on screen is a centered box matching a
 * standard trading card's 2.5:3.5 aspect ratio, covering [guideCoverage] of
 * the shorter image dimension. This mirrors that same box in pixel space so
 * the grading engine knows where the user was told to align the card.
 */
fun standardCardGuideRect(imageWidth: Int, imageHeight: Int, guideCoverage: Double = 0.90): Rect {
    val cardAspect = 2.5 / 3.5 // width / height
    val imageAspect = imageWidth.toDouble() / imageHeight.toDouble()

    var guideW: Double
    var guideH: Double
    if (imageAspect > cardAspect) {
        // Image is relatively wider than the card -> height is the limiting dimension.
        guideH = imageHeight * guideCoverage
        guideW = guideH * cardAspect
    } else {
        guideW = imageWidth * guideCoverage
        guideH = guideW / cardAspect
    }
    val left = (imageWidth - guideW) / 2.0
    val top = (imageHeight - guideH) / 2.0
    return Rect(
        left = left.roundToInt(),
        top = top.roundToInt(),
        right = (left + guideW).roundToInt(),
        bottom = (top + guideH).roundToInt()
    )
}
