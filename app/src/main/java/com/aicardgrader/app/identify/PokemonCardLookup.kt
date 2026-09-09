package com.aicardgrader.app.identify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** What set a card is from, looked up by name (and set number, when known). */
data class CardIdentification(
    val name: String,
    val setName: String,
    val setSeries: String,
    val number: String,
    val printedTotal: Int?,
    val rarity: String?
)

/**
 * Looks a card up against the public Pokemon TCG API (pokemontcg.io) by
 * the name (and, when OCR found one, the set-relative number) read off
 * the photo, to answer "what card/set is this" -- something no amount of
 * on-device image analysis can do, since it's a lookup against real card
 * data rather than something visible in pixel patterns.
 *
 * Unlike grading and the OCR name suggestion, this genuinely needs
 * network access every time (it's a live lookup, not a one-time model
 * download), so it's always a best-effort enrichment shown only when it
 * succeeds -- grading itself never depends on it or waits long for it.
 */
object PokemonCardLookup {

    private const val BASE_URL = "https://api.pokemontcg.io/v2/cards"
    private const val TIMEOUT_MS = 3000

    suspend fun lookup(nameGuess: String?, numberGuess: String?): CardIdentification? {
        val name = nameGuess?.trim().orEmpty()
        if (name.length < 2) return null

        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("$BASE_URL?q=${buildQuery(name, numberGuess)}&pageSize=1")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    setRequestProperty("Accept", "application/json")
                }
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                parseFirstCard(body)
            } catch (_: Exception) {
                null
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun buildQuery(name: String, number: String?): String {
        val nameTerm = "name:\"${name.replace("\"", "")}\""
        val numberPart = number?.substringBefore("/")?.trim()
        val query = if (!numberPart.isNullOrEmpty()) "$nameTerm number:$numberPart" else nameTerm
        return URLEncoder.encode(query, "UTF-8")
    }

    private fun parseFirstCard(body: String): CardIdentification? {
        val data = JSONObject(body).optJSONArray("data") ?: return null
        if (data.length() == 0) return null
        val card = data.getJSONObject(0)
        val set = card.optJSONObject("set")
        val printedTotal = set?.optInt("printedTotal", -1)?.takeIf { it > 0 }

        return CardIdentification(
            name = card.optString("name").ifBlank { return null },
            setName = set?.optString("name").orEmpty(),
            setSeries = set?.optString("series").orEmpty(),
            number = card.optString("number"),
            printedTotal = printedTotal,
            rarity = card.optString("rarity").takeIf { it.isNotBlank() }
        )
    }
}
