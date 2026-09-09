package com.aicardgrader.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("How this works", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "AI Card Grader analyzes your photos on-device to estimate four things graders look at: " +
                "centering, corner sharpness, edge wear, and surface condition. It then converts those into " +
                "an estimated grade formatted in the style of PSA, Beckett (BGS), CGC, and SGC's own 1–10 scales. " +
                "It also uses on-device text recognition to suggest a name for the card from the photo — always " +
                "shown as an editable suggestion, never a confirmed identification, since OCR on small printed " +
                "text can misread. The text-recognition model downloads once over the network the first time " +
                "it's used; after that, everything — grading and name suggestion — runs fully offline.",
            style = MaterialTheme.typography.bodyLarge
        )

        Text("Important disclaimer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "These are estimates from a photo, not official grades. This app is not affiliated with, " +
                "endorsed by, or connected to Professional Sports Authenticator (PSA), Beckett Grading Services " +
                "(BGS), Certified Guaranty Company (CGC), or Sportscard Guaranty Corporation (SGC). None of these " +
                "companies grade cards from photos — they physically inspect submitted cards. For an official, " +
                "certified grade, submit your card directly to the grading company. The scoring weights used " +
                "here are independently derived approximations of each company's publicly described grading " +
                "philosophy, not their real proprietary formulas.",
            style = MaterialTheme.typography.bodyMedium
        )

        Text("Tips for a more accurate estimate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        listOf(
            "Place the card on a flat, plain, dark surface for good contrast against the edges.",
            "Use soft, even, diffuse lighting — avoid direct flash or harsh point light sources.",
            "Angle the light to avoid glare and reflections off the card's surface.",
            "Fill the on-screen guide with the card, keeping it flat and square to the camera.",
            "Hold the camera steady and let it focus before capturing.",
            "Photograph both the front and back for a more complete edge/corner read."
        ).forEach { tip ->
            Text("•  $tip", style = MaterialTheme.typography.bodyLarge)
        }

        Text("What each category means", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Centering — how evenly the printed border frames the card front to back and side to side.", style = MaterialTheme.typography.bodyLarge)
        Text("Corners — sharpness of the four corners; whitening or softness indicates wear.", style = MaterialTheme.typography.bodyLarge)
        Text("Edges — whitening or nicks along the card's four edges.", style = MaterialTheme.typography.bodyLarge)
        Text("Surface — scratches, print lines, and glare on the card face. This is the least reliable category from a phone photo alone, since real graders use raking light and magnification.", style = MaterialTheme.typography.bodyLarge)
    }
}
