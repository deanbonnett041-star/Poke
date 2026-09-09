package com.aicardgrader.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aicardgrader.app.ximilar.ApiKeySettings

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf(ApiKeySettings.getXimilarApiKey(context).orEmpty()) }
    var saved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("AI grading (optional)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Slabrate's built-in grading runs fully on-device and free. If you have your own " +
                "Ximilar (ximilar.com) card-grading API key, Slabrate will also request a second, " +
                "AI-model-based grade from their service for every card and show it alongside the " +
                "on-device estimate.",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "Your key is stored only on this device and sent directly to Ximilar -- it's never " +
                "included in the app itself, so nobody else who installs Slabrate gets access to it. " +
                "Using it will consume your Ximilar API credits/plan for every card graded.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = {
                apiKey = it
                saved = false
            },
            label = { Text("Ximilar API key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                ApiKeySettings.setXimilarApiKey(context, apiKey)
                saved = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save")
        }

        if (saved) {
            Text(
                if (apiKey.isBlank()) "Cleared -- AI grading is off." else "Saved -- AI grading is on for future cards.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
