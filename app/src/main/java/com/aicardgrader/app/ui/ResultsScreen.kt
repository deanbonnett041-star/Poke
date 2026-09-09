package com.aicardgrader.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicardgrader.app.CardGraderViewModel
import com.aicardgrader.app.data.displayName
import com.aicardgrader.app.data.toEstimatesUi
import com.aicardgrader.app.identify.CardIdentification
import com.aicardgrader.app.identify.PokemonCardLookup
import com.aicardgrader.app.ui.components.GradeBadge
import com.aicardgrader.app.ui.components.SubgradeBars
import com.aicardgrader.app.ui.components.formatGrade

@Composable
fun ResultsScreen(
    viewModel: CardGraderViewModel,
    onGradeAnother: () -> Unit,
    onDone: () -> Unit
) {
    val result = viewModel.currentResult ?: run {
        Text("No result available.", modifier = Modifier.padding(24.dp))
        return
    }
    val context = LocalContext.current
    val front = viewModel.frontBitmap
    val back = viewModel.backBitmap
    val suggestedName = viewModel.suggestedCardName
    val identifiedCard = viewModel.identifiedCard
    val lookupDiagnostic = viewModel.lookupDiagnostic
    val ximilarGrade = viewModel.ximilarGrade
    val ximilarDiagnostic = viewModel.ximilarDiagnostic
    val prefillName = identifiedCard?.name ?: suggestedName
    var cardName by remember(prefillName) { mutableStateOf(prefillName ?: "") }
    var saved by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(Modifier.height(12.dp)) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                front?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Front of card",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
                back?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Back of card",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        }

        if (identifiedCard != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "Identified card",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(identifiedCard.name, style = MaterialTheme.typography.titleMedium)
                        val setLine = buildString {
                            if (identifiedCard.setName.isNotBlank()) append(identifiedCard.setName)
                            if (identifiedCard.setSeries.isNotBlank() && identifiedCard.setSeries != identifiedCard.setName) {
                                if (isNotEmpty()) append(" — ")
                                append(identifiedCard.setSeries)
                            }
                        }
                        if (setLine.isNotBlank()) {
                            Text(setLine, style = MaterialTheme.typography.bodyMedium)
                        }
                        val numberLine = buildString {
                            if (identifiedCard.number.isNotBlank()) {
                                append(identifiedCard.number)
                                if (identifiedCard.printedTotal != null) append("/${identifiedCard.printedTotal}")
                            }
                            identifiedCard.rarity?.let {
                                if (isNotEmpty()) append(" · ")
                                append(it)
                            }
                        }
                        if (numberLine.isNotBlank()) {
                            Text(
                                numberLine,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        Text(
                            "Matched from a live lookup — double-check against the physical card.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Market price",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (identifiedCard.rawPriceUsd != null) {
                            Text(
                                "Raw: $%.2f (%s, TCGplayer market price)".format(
                                    identifiedCard.rawPriceUsd,
                                    identifiedCard.rawPriceVariant ?: "ungraded"
                                ),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        } else {
                            Text(
                                "No raw price found for this printing.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Text(
                            "No reliable free source for graded (PSA 10, PSA 9, etc.) prices — " +
                                "these open real, current sold listings for this exact card instead of guessing.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("PSA 10", "PSA 9", "Raw").forEach { gradeLabel ->
                                OutlinedButton(
                                    onClick = {
                                        val url = PokemonCardLookup.gradedPriceCheckUrl(identifiedCard, gradeLabel)
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text(gradeLabel) }
                            }
                        }
                    }
                }
            }
        }

        if (identifiedCard == null && !suggestedName.isNullOrBlank()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "Card lookup",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Searched online for \"$suggestedName\" — no match. Pricing needs a successful match.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        if (!lookupDiagnostic.isNullOrBlank()) {
                            Text(
                                "Reason: $lookupDiagnostic",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }

        if (result.confidence.reasons.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "${result.confidence.level.displayName()} — tips for a better read:",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        result.confidence.reasons.forEach {
                            Text("• $it", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        item {
            Text("Category breakdown", style = MaterialTheme.typography.titleMedium)
            SubgradeBars(result.subGrades)
        }

        if (ximilarGrade != null || !ximilarDiagnostic.isNullOrBlank()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "AI grading (Ximilar)",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (ximilarGrade != null) {
                            ximilarGrade.final?.let {
                                Text(formatGrade(it), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                            }
                            ximilarGrade.condition?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium)
                            }
                            val subLines = listOfNotNull(
                                ximilarGrade.centering?.let { "Centering: ${formatGrade(it)}" },
                                ximilarGrade.corners?.let { "Corners: ${formatGrade(it)}" },
                                ximilarGrade.edges?.let { "Edges: ${formatGrade(it)}" },
                                ximilarGrade.surface?.let { "Surface: ${formatGrade(it)}" }
                            )
                            if (subLines.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                subLines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                Spacer(Modifier.height(4.dp))
                            }
                            Text(
                                "An independent second opinion from Ximilar's AI grading model — separate from the on-device estimate above.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        } else {
                            Text(
                                "Couldn't get an AI grade for this card.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            if (!ximilarDiagnostic.isNullOrBlank()) {
                                Text(
                                    "Reason: $ximilarDiagnostic",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("Estimated grades by company", style = MaterialTheme.typography.titleMedium)
        }

        items(result.toEstimatesUi()) { estimate ->
            GradeBadge(estimate)
        }

        item {
            Text(
                "Estimates only, generated by on-device image analysis. Not official, submitted, or certified grades, and not affiliated with or endorsed by PSA, Beckett/BGS, CGC, or SGC.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        item {
            if (!saved) {
                OutlinedTextField(
                    value = cardName,
                    onValueChange = { cardName = it },
                    label = { Text("Name this card (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (!prefillName.isNullOrBlank()) {
                    val hint = if (identifiedCard != null) {
                        "From the card lookup above — edit if it's not quite right."
                    } else {
                        "Detected on-device from the photo — edit if it's not quite right."
                    }
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.saveToHistory(cardName) { saved = true }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save to history") }
            } else {
                Text("Saved to history.", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onGradeAnother, modifier = Modifier.fillMaxWidth()) {
                Text("Grade another card")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
