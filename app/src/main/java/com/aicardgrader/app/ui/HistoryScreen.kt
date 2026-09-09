package com.aicardgrader.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aicardgrader.app.CardGraderViewModel
import com.aicardgrader.app.data.CardRecord
import com.aicardgrader.app.ui.components.formatGrade
import com.aicardgrader.app.ui.components.rememberBitmapFromFile
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(viewModel: CardGraderViewModel, onOpenRecord: (Long) -> Unit) {
    val records by viewModel.history.collectAsState()

    if (records.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No graded cards yet.\nGrade a card and save it to see it here.",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(records, key = { it.id }) { record ->
            HistoryRow(record = record, onClick = { onOpenRecord(record.id) })
        }
    }
}

@Composable
private fun HistoryRow(record: CardRecord, onClick: () -> Unit) {
    val thumb = rememberBitmapFromFile(record.frontImagePath, maxDimension = 240)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (thumb != null) {
            Image(
                bitmap = thumb.asImageBitmap(),
                contentDescription = record.cardName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.background)
            )
        }
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(record.cardName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
            Text(
                DateFormat.getDateInstance().format(Date(record.timestamp)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("PSA ${formatGrade(record.psaOverall)}", style = MaterialTheme.typography.bodyMedium)
            Text("BGS ${formatGrade(record.bgsOverall)}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
