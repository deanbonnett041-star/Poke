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
    val rarity: String?,
    /** Real ungraded ("raw") market price in USD, from TCGplayer via the lookup API -- not an estimate. */
    val rawPriceUsd: Double?,
    /** Which printing the price above is for (e.g. "Holofoil", "Normal") -- cards often have several. */
    val rawPriceVariant: String?
)

/**
 * Looks a card up against the public Pokemon TCG API (pokemontcg.io) by
 * the name (and, when OCR found one, the set-relative number) read off
 * the photo, to answer "what card/set is this, and what's it worth
 * ungraded" -- neither of which any amount of on-device image analysis
 * can answer, since both are lookups against real card/market data
 * rather than something visible in pixel patterns.
 *
 * Deliberately does NOT attempt to estimate a graded (PSA 10, PSA 9,
 * etc.) price: there's no free API with real graded sale data behind
 * this app, and a generic multiplier applied to the raw price would be
 * fabricated per-card guesswork that varies wildly by card and could
 * mislead someone actually buying or selling -- see
 * gradedPriceCheckUrl() for the honest alternative (a link to real,
 * current sold listings for that exact card and grade).
 *
 * Unlike grading and the OCR name suggestion, this genuinely needs
 * network access every time (it's a live lookup, not a one-time model
 * download), so it's always a best-effort enrichment shown only when it
 * succeeds -- grading itself never depends on it or waits long for it.
 */
/**
 * Result of a lookup attempt. [diagnostic] is null on success (or when
 * there was no name to search at all); when [card] is null it names why
 * -- an HTTP status, an exception type/message, or "no card matched" --
 * so a real failure reason is visible instead of every kind of failure
 * collapsing into the same silent "nothing found".
 */
data class LookupOutcome(val card: CardIdentification?, val diagnostic: String?)

object PokemonCardLookup {

    private const val BASE_URL = "https://api.pokemontcg.io/v2/cards"
    private const val TIMEOUT_MS = 6000

    suspend fun lookup(nameGuess: String?, numberGuess: String?): LookupOutcome {
        val name = nameGuess?.trim().orEmpty()
        if (name.length < 2) return LookupOutcome(null, null)

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
                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    return@withContext LookupOutcome(null, "server returned HTTP $code")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val card = parseFirstCard(body)
                if (card == null) LookupOutcome(null, "no card matched") else LookupOutcome(card, null)
            } catch (e: Exception) {
                LookupOutcome(null, "${e.javaClass.simpleName}: ${e.message}")
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
        val price = extractRawPrice(card)

        return CardIdentification(
            name = card.optString("name").ifBlank { return null },
            setName = set?.optString("name").orEmpty(),
            setSeries = set?.optString("series").orEmpty(),
            number = card.optString("number"),
            printedTotal = printedTotal,
            rarity = card.optString("rarity").takeIf { it.isNotBlank() },
            rawPriceUsd = price?.first,
            rawPriceVariant = price?.second
        )
    }

    // Checked in this order: the variants most Pokemon cards actually use,
    // most-common-and-most-collected first. Whichever is present first
    // (with a usable price) wins; anything else found is a last-resort
    // fallback so we still show a price for oddball printings.
    private val preferredVariants = listOf(
        "holofoil", "reverseHolofoil", "normal",
        "1stEditionHolofoil", "1stEditionNormal", "unlimitedHolofoil", "unlimited"
    )

    private fun extractRawPrice(card: JSONObject): Pair<Double, String>? {
        val prices = card.optJSONObject("tcgplayer")?.optJSONObject("prices") ?: return null

        for (variant in preferredVariants) {
            prices.optJSONObject(variant)?.let { priceForVariant(it) }?.let { return it to variantLabel(variant) }
        }
        val keys = prices.keys()
        while (keys.hasNext()) {
            val variant = keys.next()
            prices.optJSONObject(variant)?.let { priceForVariant(it) }?.let { return it to variantLabel(variant) }
        }
        return null
    }

    private fun priceForVariant(variantPrices: JSONObject): Double? {
        val market = variantPrices.optDouble("market", Double.NaN)
        if (!market.isNaN() && market > 0) return market
        val mid = variantPrices.optDouble("mid", Double.NaN)
        if (!mid.isNaN() && mid > 0) return mid
        return null
    }

    private fun variantLabel(variant: String): String = when (variant) {
        "normal" -> "Normal"
        "holofoil" -> "Holofoil"
        "reverseHolofoil" -> "Reverse Holofoil"
        "1stEditionHolofoil" -> "1st Edition Holofoil"
        "1stEditionNormal" -> "1st Edition Normal"
        "unlimitedHolofoil" -> "Unlimited Holofoil"
        "unlimited" -> "Unlimited"
        else -> variant.replaceFirstChar { it.uppercase() }
    }

    /**
     * A link to real, current eBay sold/completed listings for this exact
     * card and grade -- the honest alternative to guessing a graded price:
     * no API key needed, and it's genuine market data rather than a
     * generic multiplier applied to the raw price.
     */
    fun gradedPriceCheckUrl(card: CardIdentification, gradeLabel: String): String {
        val terms = listOfNotNull(
            card.name,
            card.setName.takeIf { it.isNotBlank() },
            card.number.takeIf { it.isNotBlank() },
            gradeLabel
        ).joinToString(" ")
        val encoded = URLEncoder.encode(terms, "UTF-8")
        return "https://www.ebay.com/sch/i.html?_nkw=$encoded&LH_Sold=1&LH_Complete=1"
    }
}
