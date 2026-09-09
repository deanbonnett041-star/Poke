package com.aicardgrader.app.ximilar

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** A subgrade or overall grade pulled out of Ximilar's response; null when that field wasn't present. */
data class XimilarGrade(
    val final: Double?,
    val condition: String?,
    val centering: Double?,
    val corners: Double?,
    val edges: Double?,
    val surface: Double?
)

/** [diagnostic] is null on success; otherwise names why no grade came back (HTTP status, exception, or unexpected response shape). */
data class XimilarOutcome(val grade: XimilarGrade?, val diagnostic: String?)

/**
 * Optional, opt-in second opinion from Ximilar's paid card-grading API
 * (ximilar.com) -- a real trained computer-vision model, as opposed to
 * this app's own from-scratch on-device heuristics. Only called when the
 * user has entered their own API key in Settings; that key lives only in
 * this device's local app storage (see ApiKeySettings) and is never built
 * into the app or committed anywhere, since a key baked into a
 * redistributed debug APK could be extracted and billed against the
 * owner's account by anyone who downloads it.
 *
 * The exact response JSON shape isn't in this codebase's control and
 * isn't fully nailed down from documentation alone, so parsing is
 * deliberately defensive (see findGradesObject/numericValue): if the
 * shape doesn't match what's expected, this surfaces a diagnostic with a
 * snippet of the real response rather than silently doing nothing or
 * crashing -- the same approach that already paid off for the Pokemon
 * TCG API lookup.
 */
object XimilarGradingClient {

    private const val URL_STR = "https://api.ximilar.com/card-grader/v2/grade"
    // Uploading two photos is heavier than a text lookup, so this gets a
    // longer budget than PokemonCardLookup's.
    private const val TIMEOUT_MS = 20000
    private const val JPEG_QUALITY = 85

    suspend fun grade(apiKey: String, front: Bitmap, back: Bitmap?): XimilarOutcome {
        if (apiKey.isBlank()) return XimilarOutcome(null, null)

        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val records = JSONArray().apply {
                    put(JSONObject().put("_base64", toBase64Jpeg(front)))
                    if (back != null) put(JSONObject().put("_base64", toBase64Jpeg(back)))
                }
                val requestBody = JSONObject().put("records", records).toString()

                connection = (URL(URL_STR).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Token $apiKey")
                }
                connection.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }

                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

                if (code !in 200..299) {
                    return@withContext XimilarOutcome(null, "server returned HTTP $code: ${responseBody.take(160)}")
                }
                val grade = parseGrade(responseBody)
                if (grade == null) {
                    XimilarOutcome(null, "unexpected response shape: ${responseBody.take(160)}")
                } else {
                    XimilarOutcome(grade, null)
                }
            } catch (e: Exception) {
                XimilarOutcome(null, "${e.javaClass.simpleName}: ${e.message}")
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun toBase64Jpeg(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun parseGrade(body: String): XimilarGrade? {
        val root = try {
            JSONObject(body)
        } catch (_: Exception) {
            return null
        }
        val grades = findGradesObject(root, depth = 0) ?: return null
        return XimilarGrade(
            final = numericValue(grades.opt("final")),
            condition = grades.optString("condition").takeIf { it.isNotBlank() },
            centering = numericValue(grades.opt("centering")),
            corners = numericValue(grades.opt("corners")),
            edges = numericValue(grades.opt("edges")),
            surface = numericValue(grades.opt("surface"))
        )
    }

    private val subgradeKeys = setOf("corners", "edges", "surface", "centering")

    /** Breadth-limited search for an object shaped like the grades summary: a "final" grade alongside at least two subgrade fields. */
    private fun findGradesObject(obj: JSONObject, depth: Int): JSONObject? {
        if (depth > 3) return null
        val keys = obj.keys().asSequence().toSet()
        if (obj.has("final") && keys.count { it in subgradeKeys } >= 2) return obj
        for (key in keys) {
            when (val child = obj.opt(key)) {
                is JSONObject -> findGradesObject(child, depth + 1)?.let { return it }
                is JSONArray -> for (i in 0 until child.length()) {
                    (child.opt(i) as? JSONObject)?.let { found -> findGradesObject(found, depth + 1)?.let { return it } }
                }
            }
        }
        return null
    }

    /** A grade field might be a plain number, or an object/array of sub-scores (e.g. one per corner) -- average whatever numeric leaves are found. */
    private fun numericValue(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is JSONObject -> {
            if (value.has("grade")) numericValue(value.opt("grade"))
            else {
                val leaves = value.keys().asSequence().mapNotNull { numericValue(value.opt(it)) }.toList()
                if (leaves.isEmpty()) null else leaves.average()
            }
        }
        is JSONArray -> {
            val leaves = (0 until value.length()).mapNotNull { numericValue(value.opt(it)) }
            if (leaves.isEmpty()) null else leaves.average()
        }
        else -> null
    }
}
