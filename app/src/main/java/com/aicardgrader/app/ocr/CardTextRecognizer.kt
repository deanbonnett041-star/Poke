package com.aicardgrader.app.ocr

import android.graphics.Bitmap
import com.aicardgrader.app.grading.standardCardGuideRect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Suggests a card name by running on-device OCR (Google ML Kit) over the
 * front photo and picking out the largest text near the top of the card --
 * where a Pokémon card's name is printed. This is a best-effort suggestion,
 * always shown as an editable, pre-filled text field rather than treated as
 * a confirmed identification: OCR on a phone photo of a small-font, often
 * stylized card name is unreliable enough that it should never be silently
 * trusted.
 *
 * The recognition model downloads once, on first use, over the network;
 * after that it runs fully on-device with no further network access.
 */
object CardTextRecognizer {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun suggestCardName(front: Bitmap): String? {
        val cropped = cropToGuide(front) ?: front
        val visionText = runCatching { recognize(cropped) }.getOrNull() ?: return null
        return pickLikelyName(visionText, cropped.height)
    }

    private fun cropToGuide(bitmap: Bitmap): Bitmap? {
        return try {
            val guide = standardCardGuideRect(bitmap.width, bitmap.height)
            val left = guide.left.coerceIn(0, bitmap.width - 1)
            val top = guide.top.coerceIn(0, bitmap.height - 1)
            val width = guide.width.coerceIn(1, bitmap.width - left)
            val height = guide.height.coerceIn(1, bitmap.height - top)
            Bitmap.createBitmap(bitmap, left, top, width, height)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun recognize(bitmap: Bitmap): Text = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result -> if (cont.isActive) cont.resume(result) }
            .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
    }

    /** Card name is printed near the top of the card, larger than the HP/type text around it. */
    private fun pickLikelyName(visionText: Text, imageHeight: Int): String? {
        val topZoneLimit = imageHeight * 0.30

        val nonNameLine = Regex("""^HP\s*\d+$""", RegexOption.IGNORE_CASE)
        val cardNumberLine = Regex("""\d+\s*/\s*\d+""")
        val pureNumberLine = Regex("""^\d+$""")

        val lines = visionText.textBlocks.flatMap { it.lines }
            .filter { line ->
                val box = line.boundingBox ?: return@filter false
                val text = line.text.trim()
                box.top < topZoneLimit && text.length in 2..28
            }
            .filterNot { line ->
                val text = line.text.trim()
                nonNameLine.matches(text) || cardNumberLine.containsMatchIn(text) || pureNumberLine.matches(text)
            }

        val best = lines.maxByOrNull { it.boundingBox?.height() ?: 0 } ?: return null
        return cleanUp(best.text)
    }

    private fun cleanUp(raw: String): String {
        val cleaned = raw.replace(Regex("[^A-Za-z'’\\-.\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.length < 2) return raw.trim()
        return cleaned.split(" ").joinToString(" ") { word ->
            if (word.isEmpty()) word else word.substring(0, 1).uppercase() + word.substring(1).lowercase()
        }
    }
}
