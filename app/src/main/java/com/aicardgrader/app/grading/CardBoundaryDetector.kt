package com.aicardgrader.app.grading

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Snaps the on-screen capture guide (the rectangle the user was asked to
 * align the card to) onto the card's real physical edge, by searching for
 * the column/row with the strongest *aggregate* brightness-gradient across
 * many sampled lines near each guide edge (a projection-profile edge
 * detector). Aggregating gradient strength across all sample lines, rather
 * than taking each line's individual best point and majority-voting, makes
 * this far more tolerant of a card that isn't placed exactly on the guide,
 * and of noisy/textured backgrounds — a single stray strong edge on one
 * scan line can no longer hijack the result.
 */
object CardBoundaryDetector {

    fun snap(
        image: PixelImage,
        guide: Rect,
        searchFraction: Double = 0.14,
        samples: Int = 40
    ): Rect {
        val searchX = (image.width * searchFraction).roundToInt().coerceAtLeast(6)
        val searchY = (image.height * searchFraction).roundToInt().coerceAtLeast(6)

        val left = snapVerticalEdge(image, guide.left, guide.top, guide.bottom, searchX, samples)
        val right = snapVerticalEdge(image, guide.right, guide.top, guide.bottom, searchX, samples)
        val top = snapHorizontalEdge(image, guide.top, guide.left, guide.right, searchY, samples)
        val bottom = snapHorizontalEdge(image, guide.bottom, guide.left, guide.right, searchY, samples)

        val l = left.coerceIn(0, image.width - 2)
        val r = right.coerceIn(l + 1, image.width - 1)
        val t = top.coerceIn(0, image.height - 2)
        val b = bottom.coerceIn(t + 1, image.height - 1)
        return Rect(l, t, r, b)
    }

    /** Finds the column with the strongest aggregate horizontal gradient near [guideX]. */
    private fun snapVerticalEdge(
        image: PixelImage, guideX: Int, yStart: Int, yEnd: Int, search: Int, samples: Int
    ): Int {
        val xMin = (guideX - search).coerceAtLeast(1)
        val xMax = (guideX + search).coerceAtMost(image.width - 2)
        if (xMin >= xMax) return guideX

        val scores = IntArray(xMax - xMin + 1)
        val step = ((yEnd - yStart).coerceAtLeast(1)) / samples.coerceAtLeast(1)
        var y = yStart
        while (y < yEnd) {
            var x = xMin
            while (x <= xMax) {
                val g = abs(PixelImage.luma(image.at(x + 1, y)) - PixelImage.luma(image.at(x - 1, y)))
                scores[x - xMin] += g
                x++
            }
            y += step.coerceAtLeast(1)
        }
        val bestIndex = scores.indices.maxByOrNull { scores[it] } ?: return guideX
        return xMin + bestIndex
    }

    /** Finds the row with the strongest aggregate vertical gradient near [guideY]. */
    private fun snapHorizontalEdge(
        image: PixelImage, guideY: Int, xStart: Int, xEnd: Int, search: Int, samples: Int
    ): Int {
        val yMin = (guideY - search).coerceAtLeast(1)
        val yMax = (guideY + search).coerceAtMost(image.height - 2)
        if (yMin >= yMax) return guideY

        val scores = IntArray(yMax - yMin + 1)
        val step = ((xEnd - xStart).coerceAtLeast(1)) / samples.coerceAtLeast(1)
        var x = xStart
        while (x < xEnd) {
            var y = yMin
            while (y <= yMax) {
                val g = abs(PixelImage.luma(image.at(x, y + 1)) - PixelImage.luma(image.at(x, y - 1)))
                scores[y - yMin] += g
                y++
            }
            x += step.coerceAtLeast(1)
        }
        val bestIndex = scores.indices.maxByOrNull { scores[it] } ?: return guideY
        return yMin + bestIndex
    }
}
