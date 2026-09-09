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

/** Best-effort text read off a card photo: a name guess and a set-number guess (e.g. "14/132"). */
data class CardTextGuess(val name: String?, val number: String?)

/**
 * Reads a card name and set-relative number by running on-device OCR
 * (Google ML Kit) over the front photo: the name from the largest text
 * near the top of the card, the number from a "x/y" pattern anywhere on
 * it (almost always printed along the bottom). Both are best-effort
 * suggestions, always shown as editable rather than treated as a
 * confirmed identification: OCR on a phone photo of small, often
 * stylized card text is unreliable enough that it should never be
 * silently trusted.
 *
 * The recognition model downloads once, on first use, over the network;
 * after that it runs fully on-device with no further network access.
 */
object CardTextRecognizer {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun recognizeCard(front: Bitmap): CardTextGuess {
        val cropped = cropToGuide(front) ?: front
        val visionText = runCatching { recognize(cropped) }.getOrNull() ?: return CardTextGuess(null, null)
        return CardTextGuess(
            name = pickLikelyName(visionText, cropped.height),
            number = pickLikelyNumber(visionText)
        )
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

    private val cardNumberPattern = Regex("""(\d{1,4})\s*/\s*(\d{1,4})""")
    private val nonNameLine = Regex("""^HP\s*\d+$""", RegexOption.IGNORE_CASE)
    private val pureNumberLine = Regex("""^\d+$""")

    /** Card name is printed near the top of the card, larger than the HP/type text around it. */
    private fun pickLikelyName(visionText: Text, imageHeight: Int): String? {
        val topZoneLimit = imageHeight * 0.30

        val lines = visionText.textBlocks.flatMap { it.lines }
            .filter { line ->
                val box = line.boundingBox ?: return@filter false
                val text = line.text.trim()
                box.top < topZoneLimit && text.length in 2..28
            }
            .filterNot { line ->
                val text = line.text.trim()
                nonNameLine.matches(text) || cardNumberPattern.containsMatchIn(text) || pureNumberLine.matches(text)
            }

        val best = lines.maxByOrNull { it.boundingBox?.height() ?: 0 } ?: return null
        return cleanUpName(best.text)
    }

    /** Set-relative card number (e.g. "14/132") is printed along the bottom, anywhere in the full text. */
    private fun pickLikelyNumber(visionText: Text): String? {
        val fullText = visionText.textBlocks.flatMap { it.lines }.joinToString(" ") { it.text }
        val match = cardNumberPattern.find(fullText) ?: return null
        return "${match.groupValues[1]}/${match.groupValues[2]}"
    }

    // Many cards print the HP value right after the name on the same
    // physical line (e.g. "Sabrina's Gengar   80 HP"), so ML Kit often
    // returns them as one OCR line -- strip that trailing contamination
    // before it becomes part of the name guess (and breaks the exact-phrase
    // lookup against the card API, which won't match "Sabrina's Gengar Hp").
    private val trailingHpPattern = Regex("""\s*\d{1,3}\s*hp\.?\s*$""", RegexOption.IGNORE_CASE)

    private fun cleanUpName(raw: String): String {
        val withoutHp = raw.replace(trailingHpPattern, "")
        val source = if (withoutHp.trim().length >= 2) withoutHp else raw
        val cleaned = source.replace(Regex("[^A-Za-z'’\\-.\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.length < 2) return raw.trim()
        val words = cleaned.split(" ").toMutableList()
        while (words.isNotEmpty() && words.last().equals("hp", ignoreCase = true)) {
            words.removeAt(words.lastIndex)
        }
        if (words.isEmpty()) return raw.trim()
        return words.joinToString(" ") { word ->
            word.substring(0, 1).uppercase() + word.substring(1).lowercase()
        }
    }
}
