package com.aicardgrader.app

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aicardgrader.app.data.CardRecord
import com.aicardgrader.app.data.GradingRepository
import com.aicardgrader.app.grading.GradingEngine
import com.aicardgrader.app.grading.GradingResult
import com.aicardgrader.app.grading.standardCardGuideRect
import com.aicardgrader.app.grading.toPixelImage
import com.aicardgrader.app.identify.CardIdentification
import com.aicardgrader.app.identify.LookupOutcome
import com.aicardgrader.app.identify.PokemonCardLookup
import com.aicardgrader.app.ocr.CardTextRecognizer
import com.aicardgrader.app.ximilar.ApiKeySettings
import com.aicardgrader.app.ximilar.XimilarGrade
import com.aicardgrader.app.ximilar.XimilarGradingClient
import com.aicardgrader.app.ximilar.XimilarOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CardGraderViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = GradingRepository(app)

    var frontBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var backBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var currentResult by mutableStateOf<GradingResult?>(null)
        private set
    var isGrading by mutableStateOf(false)
        private set
    var lastSavedRecordId by mutableStateOf<Long?>(null)
        private set
    var suggestedCardName by mutableStateOf<String?>(null)
        private set
    var identifiedCard by mutableStateOf<CardIdentification?>(null)
        private set
    var lookupDiagnostic by mutableStateOf<String?>(null)
        private set
    var ximilarGrade by mutableStateOf<XimilarGrade?>(null)
        private set
    var ximilarDiagnostic by mutableStateOf<String?>(null)
        private set

    val history: StateFlow<List<CardRecord>> = repository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun recordFlow(id: Long) = repository.observeRecord(id)

    fun setFront(bitmap: Bitmap) {
        frontBitmap = bitmap
    }

    fun setBack(bitmap: Bitmap?) {
        backBitmap = bitmap
    }

    fun resetCapture() {
        frontBitmap = null
        backBitmap = null
        currentResult = null
        lastSavedRecordId = null
        suggestedCardName = null
        identifiedCard = null
        lookupDiagnostic = null
        ximilarGrade = null
        ximilarDiagnostic = null
    }

    fun runGrading(onDone: () -> Unit) {
        val front = frontBitmap ?: return
        isGrading = true
        viewModelScope.launch {
            val gradingDeferred = async(Dispatchers.Default) {
                val pixelImage = front.toPixelImage()
                val guide = standardCardGuideRect(pixelImage.width, pixelImage.height)
                GradingEngine.grade(pixelImage, guide)
            }
            // OCR runs fully on-device; the set/card lookup that follows it
            // is a live network call and best-effort only -- if it fails
            // (no connection, no match) the name guess from OCR still
            // stands on its own, same as before this existed.
            val identificationDeferred = async(Dispatchers.Default) {
                val guess = runCatching { CardTextRecognizer.recognizeCard(front) }.getOrNull()
                val nameGuess = guess?.name
                val numberGuess = guess?.number
                val outcome = if (!nameGuess.isNullOrBlank()) {
                    runCatching { PokemonCardLookup.lookup(nameGuess, numberGuess) }
                        .getOrElse { LookupOutcome(null, "${it.javaClass.simpleName}: ${it.message}") }
                } else {
                    LookupOutcome(null, null)
                }
                Triple(guess, outcome.card, outcome.diagnostic)
            }
            // Optional second opinion from Ximilar's paid grading API --
            // only runs when the user entered their own key in Settings.
            // Never blocks or affects the free on-device estimate above.
            val ximilarKey = ApiKeySettings.getXimilarApiKey(getApplication())
            val ximilarDeferred = async(Dispatchers.Default) {
                if (ximilarKey.isNullOrBlank()) {
                    XimilarOutcome(null, null)
                } else {
                    runCatching { XimilarGradingClient.grade(ximilarKey, front, backBitmap) }
                        .getOrElse { XimilarOutcome(null, "${it.javaClass.simpleName}: ${it.message}") }
                }
            }

            currentResult = gradingDeferred.await()
            val (guess, card, diagnostic) = identificationDeferred.await()
            suggestedCardName = guess?.name
            identifiedCard = card
            lookupDiagnostic = diagnostic
            val ximilarOutcome = ximilarDeferred.await()
            ximilarGrade = ximilarOutcome.grade
            ximilarDiagnostic = ximilarOutcome.diagnostic
            isGrading = false
            onDone()
        }
    }

    fun saveToHistory(cardName: String, onSaved: (Long) -> Unit) {
        val front = frontBitmap ?: return
        val result = currentResult ?: return
        viewModelScope.launch {
            val id = repository.saveResult(cardName, front, backBitmap, result)
            lastSavedRecordId = id
            onSaved(id)
        }
    }

    fun deleteRecord(record: CardRecord) {
        viewModelScope.launch { repository.delete(record) }
    }
}
