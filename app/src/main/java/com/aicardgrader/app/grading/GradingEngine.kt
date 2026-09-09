package com.aicardgrader.app.grading

/**
 * Orchestrates the whole photo -> estimated-grades pipeline:
 * snap the capture guide to the real card edge, derive the four
 * category subgrades, run basic photo-quality checks, then map the
 * subgrades onto each grading company's public scale.
 */
object GradingEngine {

    fun grade(image: PixelImage, guideRect: Rect): GradingResult {
        val cardRect = CardBoundaryDetector.snap(image, guideRect)

        val centering = CenteringAnalyzer.analyze(image, cardRect)
        val corners = CornerAnalyzer.analyze(image, cardRect, centering.avgBorderPx)
        val edges = EdgeAnalyzer.analyze(image, cardRect, centering.avgBorderPx)

        val innerLeft = (cardRect.left + centering.leftPx).coerceIn(cardRect.left, cardRect.right)
        val innerRight = (cardRect.right - centering.rightPx).coerceIn(innerLeft, cardRect.right)
        val innerTop = (cardRect.top + centering.topPx).coerceIn(cardRect.top, cardRect.bottom)
        val innerBottom = (cardRect.bottom - centering.bottomPx).coerceIn(innerTop, cardRect.bottom)
        val innerRect = Rect(innerLeft, innerTop, innerRight, innerBottom)
        val surface = SurfaceAnalyzer.analyze(image, innerRect)

        val subGrades = SubGrades(
            centering = centering.grade,
            corners = corners.grade,
            edges = edges.grade,
            surface = surface.grade
        )

        val quality = ImageQuality.check(image, cardRect)
        val confidence = buildConfidence(quality, surface)

        val estimates = CompanyGradeMapper.mapAll(subGrades)

        return GradingResult(subGrades, estimates, confidence)
    }

    private fun buildConfidence(quality: QualityCheck, surface: SurfaceResult): Confidence {
        val reasons = ArrayList<String>()
        if (quality.isBlurry) reasons.add("Photo looks a little soft/out of focus — hold steady and let the camera focus before capturing.")
        if (quality.isTooDark) reasons.add("Photo is quite dark — use even, diffuse lighting.")
        if (quality.isTooBright) reasons.add("Photo is overexposed — reduce direct light/flash.")
        if (surface.lowConfidence) reasons.add("Glare detected on the card surface — angle the light source to avoid reflections.")

        val level = when {
            reasons.size >= 2 -> Confidence.Level.LOW
            reasons.size == 1 -> Confidence.Level.MEDIUM
            else -> Confidence.Level.HIGH
        }
        return Confidence(level, reasons)
    }
}
