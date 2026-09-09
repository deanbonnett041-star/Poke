package com.aicardgrader.app.grading

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Snaps the on-screen capture guide (the rectangle the user was asked to
 * align the card to) onto the card's real physical edge, by searching for
 * the strongest brightness-gradient line near each guide edge. This is a
 * deliberately lightweight alternative to full contour/quad detection: it
 * relies on the user roughly aligning the card to the guide (enforced by
 * the capture UI) and only corrects small (+/- searchFraction) offsets.
 */
object CardBoundaryDetector {

    fun snap(
        image: PixelImage,
        guide: Rect,
        searchFraction: Double = 0.05,
        samples: Int = 24
    ): Rect {
        val searchX = (image.width * searchFraction).roundToInt().coerceAtLeast(4)
        val searchY = (image.height * searchFraction).roundToInt().coerceAtLeast(4)

        val left = snapVerticalEdge(image, guide.left, guide.top, guide.bottom, searchX, samples, towardsInterior = 1)
        val right = snapVerticalEdge(image, guide.right, guide.top, guide.bottom, searchX, samples, towardsInterior = -1)
        val top = snapHorizontalEdge(image, guide.top, guide.left, guide.right, searchY, samples, towardsInterior = 1)
        val bottom = snapHorizontalEdge(image, guide.bottom, guide.left, guide.right, searchY, samples, towardsInterior = -1)

        val l = left.coerceIn(0, image.width - 2)
        val r = right.coerceIn(l + 1, image.width - 1)
        val t = top.coerceIn(0, image.height - 2)
        val b = bottom.coerceIn(t + 1, image.height - 1)
        return Rect(l, t, r, b)
    }

    /** Finds the column with the strongest horizontal gradient near [guideX]. */
    private fun snapVerticalEdge(
        image: PixelImage, guideX: Int, yStart: Int, yEnd: Int, search: Int, samples: Int, towardsInterior: Int
    ): Int {
        val votes = HashMap<Int, Int>()
        val step = ((yEnd - yStart).coerceAtLeast(1)) / samples.coerceAtLeast(1)
        var y = yStart
        while (y < yEnd) {
            var bestX = guideX
            var bestGrad = -1
            var x = (guideX - search).coerceAtLeast(1)
            val xMax = (guideX + search).coerceAtMost(image.width - 2)
            while (x <= xMax) {
                val g = abs(PixelImage.luma(image.at(x + 1, y)) - PixelImage.luma(image.at(x - 1, y)))
                if (g > bestGrad) {
                    bestGrad = g
                    bestX = x
                }
                x++
            }
            if (bestGrad > 12) votes[bestX] = (votes[bestX] ?: 0) + 1
            y += step.coerceAtLeast(1)
        }
        return votes.maxByOrNull { it.value }?.key ?: guideX
    }

    /** Finds the row with the strongest vertical gradient near [guideY]. */
    private fun snapHorizontalEdge(
        image: PixelImage, guideY: Int, xStart: Int, xEnd: Int, search: Int, samples: Int, towardsInterior: Int
    ): Int {
        val votes = HashMap<Int, Int>()
        val step = ((xEnd - xStart).coerceAtLeast(1)) / samples.coerceAtLeast(1)
        var x = xStart
        while (x < xEnd) {
            var bestY = guideY
            var bestGrad = -1
            var y = (guideY - search).coerceAtLeast(1)
            val yMax = (guideY + search).coerceAtMost(image.height - 2)
            while (y <= yMax) {
                val g = abs(PixelImage.luma(image.at(x, y + 1)) - PixelImage.luma(image.at(x, y - 1)))
                if (g > bestGrad) {
                    bestGrad = g
                    bestY = y
                }
                y++
            }
            if (bestGrad > 12) votes[bestY] = (votes[bestY] ?: 0) + 1
            x += step.coerceAtLeast(1)
        }
        return votes.maxByOrNull { it.value }?.key ?: guideY
    }
}
