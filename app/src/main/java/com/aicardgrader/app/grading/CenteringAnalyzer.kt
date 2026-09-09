package com.aicardgrader.app.grading

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class CenteringResult(
    val leftRightRatio: Pair<Int, Int>,
    val topBottomRatio: Pair<Int, Int>,
    val grade: Double,
    val leftPx: Int,
    val rightPx: Int,
    val topPx: Int,
    val bottomPx: Int
) {
    val avgBorderPx: Int get() = (leftPx + rightPx + topPx + bottomPx) / 4
}

/**
 * Finds the printed border thickness on all four sides of the card (the
 * transition from the outer frame color to the inner art/text panel) and
 * scores how close the card is to perfectly centered, using the same
 * "worst axis dominates" spirit as the published grading-company centering
 * charts (e.g. 50/50=10 ... 90/10=1).
 */
object CenteringAnalyzer {

    fun analyze(image: PixelImage, cardRect: Rect): CenteringResult {
        val w = cardRect.width
        val h = cardRect.height
        val maxSearch = (min(w, h) * 0.16).roundToInt().coerceAtLeast(6)

        val left = borderThickness(image, cardRect, maxSearch, Side.LEFT)
        val right = borderThickness(image, cardRect, maxSearch, Side.RIGHT)
        val top = borderThickness(image, cardRect, maxSearch, Side.TOP)
        val bottom = borderThickness(image, cardRect, maxSearch, Side.BOTTOM)

        val lr = ratioPercent(left, right)
        val tb = ratioPercent(top, bottom)

        val gradeLR = centeringGradeForSplit(max(lr.first, lr.second))
        val gradeTB = centeringGradeForSplit(max(tb.first, tb.second))
        // Worst axis weighs more heavily, matching how a badly off-center
        // card is capped by its worse axis in practice.
        val grade = (min(gradeLR, gradeTB) * 0.65) + (max(gradeLR, gradeTB) * 0.35)

        return CenteringResult(lr, tb, roundToHalf(grade), left, right, top, bottom)
    }

    private enum class Side { LEFT, RIGHT, TOP, BOTTOM }

    private fun borderThickness(image: PixelImage, r: Rect, maxSearch: Int, side: Side): Int {
        val lineSamples = 9
        val thicknesses = ArrayList<Int>(lineSamples)
        for (i in 0 until lineSamples) {
            val t = (i + 1.0) / (lineSamples + 1.0)
            val thickness = when (side) {
                Side.LEFT -> scanIn(image, r.left, r.left + maxSearch, (r.top + t * r.height).roundToInt(), horizontal = true)
                Side.RIGHT -> scanIn(image, r.right, r.right - maxSearch, (r.top + t * r.height).roundToInt(), horizontal = true)
                Side.TOP -> scanIn(image, r.top, r.top + maxSearch, (r.left + t * r.width).roundToInt(), horizontal = false)
                Side.BOTTOM -> scanIn(image, r.bottom, r.bottom - maxSearch, (r.left + t * r.width).roundToInt(), horizontal = false)
            }
            thicknesses.add(thickness)
        }
        thicknesses.sort()
        return thicknesses[thicknesses.size / 2] // median, robust to outliers
    }

    /**
     * Walks inward from [from] towards [to] along the fixed cross-axis
     * coordinate [cross], looking for a strong, sustained brightness/color
     * change (the border -> inner-panel transition). Returns distance
     * travelled from [from].
     *
     * The card-edge detection this rect comes from is never pixel-perfect
     * (real photos especially), so [from] can sit a few pixels outside or
     * inside the card's true physical edge. If the baseline color were
     * sampled right at [from], being even 1px outside the true edge would
     * make the baseline "background", and the very next step -- the real
     * background -> border jump -- would be mistaken for the border ->
     * art transition, collapsing the measured border to ~1px. Skipping a
     * small margin before sampling the baseline, and requiring the jump to
     * hold for two consecutive steps (not just one noisy pixel), avoids
     * that failure mode.
     */
    private fun scanIn(
        image: PixelImage, from: Int, to: Int, cross: Int, horizontal: Boolean
    ): Int {
        val dir = if (to >= from) 1 else -1
        val steps = abs(to - from)
        val skip = 4
        if (steps <= skip + 2) return steps

        fun lumaAt(i: Int): Int {
            val pos = from + dir * i
            return if (horizontal) PixelImage.luma(image.at(pos, cross)) else PixelImage.luma(image.at(cross, pos))
        }

        val baseline = lumaAt(skip)
        var i = skip + 1
        while (i < steps - 1) {
            val luma = lumaAt(i)
            val nextLuma = lumaAt(i + 1)
            if (abs(luma - baseline) > 26 && abs(nextLuma - baseline) > 22) {
                return i
            }
            i++
        }
        return steps
    }

    private fun ratioPercent(a: Int, b: Int): Pair<Int, Int> {
        val total = (a + b).coerceAtLeast(1)
        val pa = ((a * 100.0) / total).roundToInt()
        return Pair(pa, 100 - pa)
    }

    /** Approximate industry-style centering chart: split% (worse side) -> grade. */
    private fun centeringGradeForSplit(worseSidePercent: Int): Double = when {
        worseSidePercent <= 50 -> 10.0
        worseSidePercent <= 55 -> 9.5
        worseSidePercent <= 60 -> 9.0
        worseSidePercent <= 65 -> 8.0
        worseSidePercent <= 70 -> 6.5
        worseSidePercent <= 75 -> 5.0
        worseSidePercent <= 80 -> 4.0
        worseSidePercent <= 85 -> 3.0
        worseSidePercent <= 90 -> 2.0
        else -> 1.0
    }

    private fun roundToHalf(v: Double) = (v * 2).roundToInt() / 2.0
}
