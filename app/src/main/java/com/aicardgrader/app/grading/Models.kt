package com.aicardgrader.app.grading

/** The four category subgrades every grading company's estimate is derived from. */
data class SubGrades(
    val centering: Double,
    val corners: Double,
    val edges: Double,
    val surface: Double
) {
    val worst: Double get() = minOf(centering, corners, edges, surface)
}

enum class Company(val displayName: String, val shortName: String) {
    PSA("Professional Sports Authenticator", "PSA"),
    BGS("Beckett Grading Services", "BGS"),
    CGC("Certified Guaranty Company", "CGC"),
    SGC("Sportscard Guaranty Corporation", "SGC")
}

data class CompanyEstimate(
    val company: Company,
    val overall: Double,
    val label: String,
    val subgrades: SubGrades?, // null for companies that don't publish subgrades (e.g. classic SGC)
    val note: String
)

data class GradingResult(
    val subGrades: SubGrades,
    val estimates: List<CompanyEstimate>,
    val confidence: Confidence
)

data class Confidence(
    val level: Level,
    val reasons: List<String>
) {
    enum class Level { HIGH, MEDIUM, LOW }
}
