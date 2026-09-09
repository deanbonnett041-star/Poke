package com.aicardgrader.app.grading

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

data class CornerScore(val name: String, val whitening: Double, val sharpness: Double, val grade: Double)
data class CornerResult(val corners: List<CornerScore>, val grade: Double)

/**
 * Inspects each of the four corners for two damage signals:
 *  - whitening: fraying reveals lighter, desaturated cardboard right at
 *    the tip, compared against the card's own intact border color a bit
 *    further along the same diagonal (self-relative, so it doesn't matter
 *    what color this particular card's border is).
 *  - softness/rounding: a sharp corner has a strong, consistent
 *    background/card gradient all along the two edges right up to the
 *    tip; a worn, rounded corner shows a visibly weaker gradient in the
 *    last stretch closest to the tip than it does a bit further out along
 *    the same edges.
 */
object CornerAnalyzer {

    fun analyze(image: PixelImage, cardRect: Rect, borderThicknessPx: Int): CornerResult {
        val maxPatch = (min(cardRect.width, cardRect.height) * 0.12).roundToInt()
        val patch = (borderThicknessPx * 0.85).roundToInt().coerceIn(6, maxPatch.coerceAtLeast(6))

        val corners = listOf(
            score(image, cardRect, "Top-Left", cardRect.left, cardRect.top, dx = 1, dy = 1, patch),
            score(image, cardRect, "Top-Right", cardRect.right, cardRect.top, dx = -1, dy = 1, patch),
            score(image, cardRect, "Bottom-Left", cardRect.left, cardRect.bottom, dx = 1, dy = -1, patch),
            score(image, cardRect, "Bottom-Right", cardRect.right, cardRect.bottom, dx = -1, dy = -1, patch)
        )
        val avg = corners.sumOf { it.grade } / corners.size
        val worst = corners.minOf { it.grade }
        // The worst corner drags the overall score down more than a simple
        // average would, mirroring how a single damaged corner matters more
        // to a grader than the other three being pristine.
        val grade = roundToHalf(avg * 0.5 + worst * 0.5)
        return CornerResult(corners, grade)
    }

    private fun score(image: PixelImage, cardRect: Rect, name: String, cornerX: Int, cornerY: Int, dx: Int, dy: Int, patch: Int): CornerScore {
        val tipEnd = (patch * 0.35).roundToInt().coerceAtLeast(2)
        val refStart = (patch * 0.6).roundToInt().coerceAtLeast(tipEnd + 2).coerceAtMost(patch)

        val whitening = whiteningScore(image, cardRect, cornerX, cornerY, dx, dy, tipEnd, refStart, patch)
        val sharpness = sharpnessScore(image, cardRect, cornerX, cornerY, dx, dy, tipEnd, refStart, patch)

        val grade = roundToHalf(whitening * 0.55 + sharpness * 0.45)
        return CornerScore(name, roundToHalf(whitening), roundToHalf(sharpness), grade)
    }

    private fun whiteningScore(
        image: PixelImage, cardRect: Rect, cornerX: Int, cornerY: Int, dx: Int, dy: Int,
        tipEnd: Int, refStart: Int, patch: Int
    ): Double {
        var refLumaSum = 0; var refSatSum = 0f; var refCount = 0
        for (i in refStart..patch) {
            val x = (cornerX + dx * i).coerceIn(cardRect.left, cardRect.right)
            val y = (cornerY + dy * i).coerceIn(cardRect.top, cardRect.bottom)
            val px = image.at(x, y)
            refLumaSum += PixelImage.luma(px)
            refSatSum += PixelImage.saturation(px)
            refCount++
        }
        val refLuma = if (refCount == 0) 0 else refLumaSum / refCount
        val refSat = if (refCount == 0) 0f else refSatSum / refCount

        var whiteHits = 0; var tipCount = 0
        for (i in 1..tipEnd) {
            val x = (cornerX + dx * i).coerceIn(cardRect.left, cardRect.right)
            val y = (cornerY + dy * i).coerceIn(cardRect.top, cardRect.bottom)
            val luma = PixelImage.luma(image.at(x, y))
            val sat = PixelImage.saturation(image.at(x, y))
            tipCount++
            if (luma - refLuma > 14 && sat < refSat - 0.04f) whiteHits++
        }
        val whiteningFraction = if (tipCount == 0) 0.0 else whiteHits.toDouble() / tipCount
        return (10.0 - whiteningFraction * 22.0).coerceIn(0.0, 10.0)
    }

    private fun sharpnessScore(
        image: PixelImage, cardRect: Rect, cornerX: Int, cornerY: Int, dx: Int, dy: Int,
        tipEnd: Int, refStart: Int, patch: Int
    ): Double {
        // Walk along the two straight edges that meet at this corner
        // (not diagonally into the card), measuring how strong the
        // background/card gradient is close to the tip vs further away.
        val g = 2
        var nearSum = 0L; var nearCount = 0
        var farSum = 0L; var farCount = 0

        for (j in 1..patch) {
            // Edge running along X (varies cornerX + dx*j), boundary at cardY.
            val xh = (cornerX + dx * j).coerceIn(cardRect.left, cardRect.right)
            val yh = cornerY
            val gh = abs(PixelImage.luma(image.at(xh, (yh + g).coerceIn(cardRect.top, cardRect.bottom))) -
                PixelImage.luma(image.at(xh, (yh - g).coerceIn(0, image.height - 1))))

            // Edge running along Y (varies cornerY + dy*j), boundary at cardX.
            val xv = cornerX
            val yv = (cornerY + dy * j).coerceIn(cardRect.top, cardRect.bottom)
            val gv = abs(PixelImage.luma(image.at((xv + g).coerceIn(cardRect.left, cardRect.right), yv)) -
                PixelImage.luma(image.at((xv - g).coerceIn(0, image.width - 1), yv)))

            val combined = (gh + gv) / 2L
            if (j <= tipEnd) { nearSum += combined; nearCount++ }
            if (j >= refStart) { farSum += combined; farCount++ }
        }

        val near = if (nearCount == 0) 0.0 else nearSum.toDouble() / nearCount
        val far = if (farCount == 0) 0.0 else farSum.toDouble() / farCount
        if (far < 6.0) return 8.0 // boundary too low-contrast to judge reliably; don't punish

        val ratio = (near / far).coerceIn(0.0, 1.3)
        return when {
            ratio >= 0.85 -> 10.0
            ratio >= 0.70 -> 8.5
            ratio >= 0.55 -> 7.0
            ratio >= 0.40 -> 5.0
            ratio >= 0.25 -> 3.0
            else -> 1.5
        }
    }

    private fun roundToHalf(v: Double) = (v * 2).roundToInt() / 2.0
}
