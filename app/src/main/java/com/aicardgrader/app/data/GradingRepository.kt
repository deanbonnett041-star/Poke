package com.aicardgrader.app.data

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import com.aicardgrader.app.grading.Company
import com.aicardgrader.app.grading.Confidence
import com.aicardgrader.app.grading.GradingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Immutable
data class CompanyEstimateUi(val company: Company, val overall: Double, val label: String)

class GradingRepository(private val context: Context) {

    private val dao = AppDatabase.get(context).cardDao()

    fun observeHistory(): Flow<List<CardRecord>> = dao.observeAll()

    fun observeRecord(id: Long): Flow<CardRecord?> = dao.observeById(id)

    suspend fun saveResult(
        cardName: String,
        front: Bitmap,
        back: Bitmap?,
        result: GradingResult
    ): Long = withContext(Dispatchers.IO) {
        val frontPath = saveImage(front, "front")
        val backPath = back?.let { saveImage(it, "back") }

        fun estimate(company: Company) = result.estimates.first { it.company == company }

        val record = CardRecord(
            timestamp = System.currentTimeMillis(),
            cardName = cardName.ifBlank { "Ungraded card" },
            frontImagePath = frontPath,
            backImagePath = backPath,
            centering = result.subGrades.centering,
            corners = result.subGrades.corners,
            edges = result.subGrades.edges,
            surface = result.subGrades.surface,
            psaOverall = estimate(Company.PSA).overall,
            psaLabel = estimate(Company.PSA).label,
            bgsOverall = estimate(Company.BGS).overall,
            bgsLabel = estimate(Company.BGS).label,
            cgcOverall = estimate(Company.CGC).overall,
            cgcLabel = estimate(Company.CGC).label,
            sgcOverall = estimate(Company.SGC).overall,
            sgcLabel = estimate(Company.SGC).label,
            confidenceLevel = result.confidence.level.name
        )
        dao.insert(record)
    }

    suspend fun delete(record: CardRecord) = withContext(Dispatchers.IO) {
        File(record.frontImagePath).delete()
        record.backImagePath?.let { File(it).delete() }
        dao.delete(record)
    }

    private fun saveImage(bitmap: Bitmap, prefix: String): String {
        val dir = File(context.filesDir, "cards").apply { mkdirs() }
        val file = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return file.absolutePath
    }
}

fun CardRecord.toEstimates(): List<CompanyEstimateUi> = listOf(
    CompanyEstimateUi(Company.PSA, psaOverall, psaLabel),
    CompanyEstimateUi(Company.BGS, bgsOverall, bgsLabel),
    CompanyEstimateUi(Company.CGC, cgcOverall, cgcLabel),
    CompanyEstimateUi(Company.SGC, sgcOverall, sgcLabel)
)

fun GradingResult.toEstimatesUi(): List<CompanyEstimateUi> =
    estimates.map { CompanyEstimateUi(it.company, it.overall, it.label) }

fun Confidence.Level.displayName(): String = when (this) {
    Confidence.Level.HIGH -> "High confidence"
    Confidence.Level.MEDIUM -> "Medium confidence"
    Confidence.Level.LOW -> "Low confidence"
}
