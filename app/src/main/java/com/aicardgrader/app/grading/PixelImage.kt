package com.aicardgrader.app.grading

import kotlin.math.max
import kotlin.math.min

/**
 * Plain-JVM pixel buffer, ARGB8888 packed ints (same layout as
 * android.graphics.Bitmap.getPixels()/setPixels()). Kept free of any
 * android.* imports so the whole grading engine can be unit tested on a
 * desktop JVM and reused unmodified inside the Android app module.
 */
class PixelImage(val width: Int, val height: Int, val pixels: IntArray) {

    init {
        require(pixels.size == width * height) {
            "pixels size ${pixels.size} does not match $width x $height"
        }
    }

    inline fun at(x: Int, y: Int): Int {
        val cx = x.coerceIn(0, width - 1)
        val cy = y.coerceIn(0, height - 1)
        return pixels[cy * width + cx]
    }

    companion object {
        fun red(argb: Int) = (argb shr 16) and 0xFF
        fun green(argb: Int) = (argb shr 8) and 0xFF
        fun blue(argb: Int) = argb and 0xFF

        /** Perceptual luma, 0..255. */
        fun luma(argb: Int): Int {
            val r = red(argb); val g = green(argb); val b = blue(argb)
            return ((r * 299 + g * 587 + b * 114) / 1000)
        }

        fun saturation(argb: Int): Float {
            val r = red(argb) / 255f; val g = green(argb) / 255f; val b = blue(argb) / 255f
            val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
            return if (mx <= 0f) 0f else (mx - mn) / mx
        }
    }
}

data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}
