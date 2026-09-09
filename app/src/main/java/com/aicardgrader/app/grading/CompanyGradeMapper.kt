package com.aicardgrader.app.grading

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Maps the four measured subgrades onto an estimated overall grade in the
 * style of each major grading company's public 1-10 scale and label
 * vocabulary. These weightings are independently derived approximations
 * based on each company's publicly described grading philosophy (e.g.
 * PSA's emphasis on centering, BGS/CGC's published subgrade format, SGC's
 * reputation for stricter corner/edge standards) — they are NOT the
 * companies' actual proprietary formulas, and these are not official,
 * submitted, or certified grades.
 */
object CompanyGradeMapper {

    fun mapAll(subGrades: SubGrades): List<CompanyEstimate> = listOf(
        psa(subGrades),
        bgs(subGrades),
        cgc(subGrades),
        sgc(subGrades)
    )

    private fun psa(s: SubGrades): CompanyEstimate {
        var overall = s.centering * 0.40 + s.corners * 0.25 + s.edges * 0.20 + s.surface * 0.15
        overall = min(overall, s.worst + 1.5)
        overall = roundPsaStyle(overall)
        return CompanyEstimate(
            company = Company.PSA,
            overall = overall,
            label = psaLabel(overall),
            subgrades = null,
            note = "PSA reports a single overall grade (no public subgrades)."
        )
    }

    private fun bgs(s: SubGrades): CompanyEstimate {
        val subs = listOf(s.centering, s.corners, s.edges, s.surface)
        val weighted = (2 * s.worst + subs.sum()) / 6.0
        var overall = roundToHalf(weighted)
        if (subs.all { it >= 9.5 } && overall > s.worst + 0.5) overall = roundToHalf(s.worst + 0.5)
        val label = when {
            subs.all { it == 10.0 } -> "Black Label 10 (Pristine, all four 10s)"
            overall >= 10.0 -> "Pristine 10"
            overall >= 9.5 -> "Gem Mint 9.5"
            overall >= 9.0 -> "Mint 9"
            overall >= 8.5 -> "NM-MT+ 8.5"
            overall >= 8.0 -> "NM-MT 8"
            overall >= 7.0 -> "NM 7"
            overall >= 6.0 -> "EX-MT 6"
            overall >= 5.0 -> "EX 5"
            overall >= 4.0 -> "VG-EX 4"
            overall >= 3.0 -> "VG 3"
            overall >= 2.0 -> "GOOD 2"
            else -> "POOR-FAIR"
        }
        return CompanyEstimate(
            company = Company.BGS,
            overall = overall,
            label = label,
            subgrades = SubGrades(roundToHalf(s.centering), roundToHalf(s.corners), roundToHalf(s.edges), roundToHalf(s.surface)),
            note = "BGS publishes all four subgrades alongside the overall grade."
        )
    }

    private fun cgc(s: SubGrades): CompanyEstimate {
        val subs = listOf(s.centering, s.corners, s.edges, s.surface)
        val weighted = (2 * s.worst + subs.sum()) / 6.0
        val overall = roundToHalf(weighted)
        val label = when {
            overall >= 10.0 -> "Pristine 10"
            overall >= 9.5 -> "Gem Mint 9.5"
            overall >= 9.0 -> "Mint 9"
            overall >= 8.0 -> "NM/Mint 8"
            overall >= 7.0 -> "NM 7"
            overall >= 6.0 -> "EX-NM 6"
            overall >= 5.0 -> "EX 5"
            overall >= 4.0 -> "VG-EX 4"
            overall >= 3.0 -> "VG 3"
            overall >= 2.0 -> "GOOD 2"
            else -> "FAIR/POOR"
        }
        return CompanyEstimate(
            company = Company.CGC,
            overall = overall,
            label = label,
            subgrades = SubGrades(roundToHalf(s.centering), roundToHalf(s.corners), roundToHalf(s.edges), roundToHalf(s.surface)),
            note = "CGC publishes all four subgrades alongside the overall grade."
        )
    }

    private fun sgc(s: SubGrades): CompanyEstimate {
        var overall = s.centering * 0.25 + s.corners * 0.30 + s.edges * 0.30 + s.surface * 0.15
        overall = min(overall, s.worst + 1.0)
        overall = roundToHalf(overall)
        val label = when {
            overall >= 10.0 -> "Pristine 10 / Gem Mint 10"
            overall >= 9.5 -> "Mint 9.5"
            overall >= 9.0 -> "Mint 9"
            overall >= 8.5 -> "NM/MT+ 8.5"
            overall >= 8.0 -> "NM/MT 8"
            overall >= 7.0 -> "NM 7"
            overall >= 6.0 -> "EX/NM 6"
            overall >= 5.0 -> "EX 5"
            overall >= 4.0 -> "VG-EX 4"
            overall >= 3.0 -> "VG 3"
            overall >= 2.0 -> "GOOD 2"
            else -> "FAIR/POOR"
        }
        return CompanyEstimate(
            company = Company.SGC,
            overall = overall,
            label = label,
            subgrades = null,
            note = "SGC's classic scale reports a single overall grade; SGC known for strict corner/edge standards."
        )
    }

    private fun psaLabel(overall: Double): String = when {
        overall >= 10.0 -> "Gem Mint 10"
        overall >= 9.5 -> "Mint 9.5"
        overall >= 9.0 -> "Mint 9"
        overall >= 8.0 -> "NM-MT 8"
        overall >= 7.0 -> "NM 7"
        overall >= 6.0 -> "EX-MT 6"
        overall >= 5.0 -> "EX 5"
        overall >= 4.0 -> "VG-EX 4"
        overall >= 3.0 -> "VG 3"
        overall >= 2.0 -> "GOOD 2"
        else -> "FAIR/POOR 1"
    }

    /** PSA only awards a whole-number 10; everything else can land on a half point. */
    private fun roundPsaStyle(v: Double): Double {
        val half = roundToHalf(v)
        return if (half > 9.5 && half < 10.0) 9.5 else half
    }

    private fun roundToHalf(v: Double) = (v * 2).roundToInt() / 2.0
}
