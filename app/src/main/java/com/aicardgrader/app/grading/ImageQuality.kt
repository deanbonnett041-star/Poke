package com.aicardgrader.app.grading

data class QualityCheck(val blurScore: Double, val isBlurry: Boolean, val isTooDark: Boolean, val isTooBright: Boolean)

/** Cheap whole-image sanity checks used to set the confidence level shown to the user. */
object ImageQuality {

    fun check(image: PixelImage, region: Rect): QualityCheck {
        val step = maxOf(1, minOf(region.width, region.height) / 220)
        var lapSum = 0L
        var lapSumSq = 0L
        var count = 0L
        var brightSum = 0L

        var y = region.top + step
        while (y < region.bottom - step) {
            var x = region.left + step
            while (x < region.right - step) {
                val c = PixelImage.luma(image.at(x, y))
                val l = PixelImage.luma(image.at(x - step, y))
                val r = PixelImage.luma(image.at(x + step, y))
                val u = PixelImage.luma(image.at(x, y - step))
                val d = PixelImage.luma(image.at(x, y + step))
                val lap = 4 * c - l - r - u - d
                lapSum += lap
                lapSumSq += lap.toLong() * lap
                brightSum += c
                count++
                x += step
            }
            y += step
        }
        if (count == 0L) return QualityCheck(0.0, isBlurry = true, isTooDark = false, isTooBright = false)

        val mean = lapSum.toDouble() / count
        val variance = (lapSumSq.toDouble() / count) - mean * mean
        val avgBrightness = brightSum.toDouble() / count

        return QualityCheck(
            blurScore = variance,
            isBlurry = variance < 45.0,
            isTooDark = avgBrightness < 55.0,
            isTooBright = avgBrightness > 225.0
        )
    }
}
