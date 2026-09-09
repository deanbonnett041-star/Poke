package com.aicardgrader.app.grading

import kotlin.math.roundToInt

data class EdgeSideResult(val name: String, val anomalyFraction: Double, val grade: Double)
data class EdgeResult(val sides: List<EdgeSideResult>, val grade: Double)

/**
 * Samples along each of the four edges (skipping the corner zones, which
 * CornerAnalyzer already covers) looking for whitening/nicks: a bright,
 * desaturated blip right at the edge relative to the card's own border
 * color a few pixels in.
 */
object EdgeAnalyzer {

    fun analyze(image: PixelImage, cardRect: Rect, borderThicknessPx: Int): EdgeResult {
        val cornerSkip = 0.10 // fraction of each edge length reserved for corners
        val refOffset = (borderThicknessPx / 3).coerceAtLeast(3)
        val sides = listOf(
            sampleEdge(image, "Top", horizontal = true, fixed = cardRect.top, along = cardRect.left..cardRect.right, inward = 1, cornerSkip, refOffset),
            sampleEdge(image, "Bottom", horizontal = true, fixed = cardRect.bottom, along = cardRect.left..cardRect.right, inward = -1, cornerSkip, refOffset),
            sampleEdge(image, "Left", horizontal = false, fixed = cardRect.left, along = cardRect.top..cardRect.bottom, inward = 1, cornerSkip, refOffset),
            sampleEdge(image, "Right", horizontal = false, fixed = cardRect.right, along = cardRect.top..cardRect.bottom, inward = -1, cornerSkip, refOffset)
        )
        val avg = sides.sumOf { it.grade } / sides.size
        val worst = sides.minOf { it.grade }
        val grade = roundToHalf(avg * 0.6 + worst * 0.4)
        return EdgeResult(sides, grade)
    }

    private fun sampleEdge(
        image: PixelImage, name: String, horizontal: Boolean, fixed: Int,
        along: IntRange, inward: Int, cornerSkip: Double, refOffset: Int
    ): EdgeSideResult {
        val length = along.last - along.first
        val skip = (length * cornerSkip).roundToInt()
        val start = along.first + skip
        val end = along.last - skip
        val sampleCount = 30
        val step = ((end - start).coerceAtLeast(1)) / sampleCount
        var anomalies = 0
        var total = 0
        var p = start
        while (p < end) {
            val edgeLuma: Int
            val edgeSat: Float
            val refLuma: Int
            val refSat: Float
            if (horizontal) {
                edgeLuma = PixelImage.luma(image.at(p, fixed))
                edgeSat = PixelImage.saturation(image.at(p, fixed))
                refLuma = PixelImage.luma(image.at(p, fixed + inward * refOffset))
                refSat = PixelImage.saturation(image.at(p, fixed + inward * refOffset))
            } else {
                edgeLuma = PixelImage.luma(image.at(fixed, p))
                edgeSat = PixelImage.saturation(image.at(fixed, p))
                refLuma = PixelImage.luma(image.at(fixed + inward * refOffset, p))
                refSat = PixelImage.saturation(image.at(fixed + inward * refOffset, p))
            }
            total++
            if (edgeLuma - refLuma > 13 && edgeSat < refSat - 0.04f) anomalies++
            p += step.coerceAtLeast(1)
        }
        val fraction = if (total == 0) 0.0 else anomalies.toDouble() / total
        val grade = (10.0 - fraction * 26.0).coerceIn(0.0, 10.0)
        return EdgeSideResult(name, fraction, roundToHalf(grade))
    }

    private fun roundToHalf(v: Double) = (v * 2).roundToInt() / 2.0
}
