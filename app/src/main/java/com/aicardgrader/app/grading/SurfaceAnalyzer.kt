package com.aicardgrader.app.grading

import kotlin.math.roundToInt

data class SurfaceResult(
    val glareFraction: Double,
    val scratchFraction: Double,
    val grade: Double,
    val lowConfidence: Boolean
)

/**
 * Looks at the inner card face (outside the printed border) for two
 * things a phone photo can plausibly pick up: blown-out glare hotspots,
 * and long thin high-gradient streaks consistent with a surface scratch.
 * Surface is the least reliable of the four categories from a photo alone
 * (real graders use raking light and magnification), so this also flags
 * lowConfidence when glare is high enough that the read is unreliable.
 */
object SurfaceAnalyzer {

    fun analyze(image: PixelImage, innerRect: Rect): SurfaceResult {
        val maxDim = 260
        val scaleX = maxOf(1, innerRect.width / maxDim)
        val scaleY = maxOf(1, innerRect.height / maxDim)
        val scale = maxOf(scaleX, scaleY)

        val xs = innerRect.left..innerRect.right step scale
        val ys = innerRect.top..innerRect.bottom step scale
        val gridW = xs.count()
        val gridH = ys.count()
        if (gridW < 3 || gridH < 3) {
            return SurfaceResult(0.0, 0.0, 8.0, lowConfidence = true)
        }
        val luma = IntArray(gridW * gridH)
        var idx = 0
        var glareHits = 0
        for (y in ys) {
            for (x in xs) {
                val px = image.at(x, y)
                luma[idx] = PixelImage.luma(px)
                val sat = PixelImage.saturation(px)
                if (luma[idx] > 240 && sat < 0.12f) glareHits++
                idx++
            }
        }
        val glareFraction = glareHits.toDouble() / (gridW * gridH)

        // Simple streak detector: count grid cells whose gradient magnitude
        // sits far above the local neighborhood median (a scratch stands
        // out as a sharp linear anomaly against smooth print).
        var streakHits = 0
        var samples = 0
        for (gy in 1 until gridH - 1) {
            for (gx in 1 until gridW - 1) {
                val c = luma[gy * gridW + gx]
                val l = luma[gy * gridW + gx - 1]
                val r = luma[gy * gridW + gx + 1]
                val u = luma[(gy - 1) * gridW + gx]
                val d = luma[(gy + 1) * gridW + gx]
                val laplacian = kotlin.math.abs(4 * c - l - r - u - d)
                samples++
                if (laplacian > 90) streakHits++
            }
        }
        val scratchFraction = if (samples == 0) 0.0 else streakHits.toDouble() / samples

        val glarePenalty = (glareFraction * 30.0).coerceAtMost(6.0)
        // Even a thin scratch is a real defect regardless of how little of
        // the total surface area it covers, so weight by raw hit count
        // (relative to a fixed reference grid size) rather than by the
        // fraction of the whole surface it occupies.
        val scratchPenalty = (streakHits / 16.0).coerceAtMost(7.0)
        val grade = (10.0 - glarePenalty - scratchPenalty).coerceIn(0.0, 10.0)

        return SurfaceResult(
            glareFraction = glareFraction,
            scratchFraction = scratchFraction,
            grade = roundToHalf(grade),
            lowConfidence = glareFraction > 0.15
        )
    }

    private fun roundToHalf(v: Double) = (v * 2).roundToInt() / 2.0
}
