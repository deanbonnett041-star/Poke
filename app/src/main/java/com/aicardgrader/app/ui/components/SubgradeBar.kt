package com.aicardgrader.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aicardgrader.app.grading.SubGrades

@Composable
fun SubgradeBars(subGrades: SubGrades, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        SubgradeRow("Centering", subGrades.centering)
        SubgradeRow("Corners", subGrades.corners)
        SubgradeRow("Edges", subGrades.edges)
        SubgradeRow("Surface", subGrades.surface)
    }
}

@Composable
private fun SubgradeRow(label: String, value: Double) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(formatGrade(value), style = MaterialTheme.typography.bodyMedium)
        }
        LinearProgressIndicator(
            progress = { (value / 10.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .padding(top = 4.dp)
        )
    }
}
