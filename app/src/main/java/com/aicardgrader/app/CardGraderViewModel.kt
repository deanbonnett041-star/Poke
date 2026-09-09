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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    }

    fun runGrading(onDone: () -> Unit) {
        val front = frontBitmap ?: return
        isGrading = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                val pixelImage = front.toPixelImage()
                val guide = standardCardGuideRect(pixelImage.width, pixelImage.height)
                GradingEngine.grade(pixelImage, guide)
            }
            currentResult = result
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
