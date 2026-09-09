package com.aicardgrader.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicardgrader.app.data.CompanyEstimateUi
import com.aicardgrader.app.ui.theme.GradeAmber
import com.aicardgrader.app.ui.theme.GradeGreen
import com.aicardgrader.app.ui.theme.GradeRed

private fun colorFor(grade: Double): Color = when {
    grade >= 9.0 -> GradeGreen
    grade >= 7.0 -> GradeAmber
    else -> GradeRed
}

@Composable
fun GradeBadge(estimate: CompanyEstimateUi, modifier: Modifier = Modifier) {
    val accent = colorFor(estimate.overall)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            estimate.company.shortName,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            formatGrade(estimate.overall),
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = accent
        )
        Text(
            estimate.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

fun formatGrade(grade: Double): String =
    if (grade == grade.toLong().toDouble()) grade.toLong().toString() else grade.toString()
