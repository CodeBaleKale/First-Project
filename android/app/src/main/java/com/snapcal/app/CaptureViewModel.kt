package com.snapcal.app

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.snapcal.app.data.ExtractedItem
import com.snapcal.app.net.ClaudeExtractor
import com.snapcal.app.util.Images
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val MAX_IMAGES = 6

data class PickedImage(val uri: Uri, val thumbnail: Bitmap?)

/** One extracted item being reviewed/edited before saving. */
class ReviewItem(item: ExtractedItem) {
    var include by mutableStateOf(item.confidence != "low")
    var kind by mutableStateOf(item.kind)
    var title by mutableStateOf(item.title)
    var start by mutableStateOf(item.start)
    var allDay by mutableStateOf(item.allDay)
    var due by mutableStateOf(item.due)
    var location by mutableStateOf(item.location ?: "")
    val end = item.end
    val withPeople = item.withPeople
    val notes = item.notes
    val confidence = item.confidence
    val sourceQuote = item.sourceQuote

    fun toExtracted() = ExtractedItem(
        kind = kind,
        title = title.trim(),
        start = if (kind == "event") start else null,
        end = if (kind == "event") end else null,
        allDay = allDay,
        due = if (kind == "task") due else null,
        location = location.trim().ifEmpty { null },
        withPeople = withPeople,
        notes = notes,
        confidence = confidence,
        sourceQuote = sourceQuote,
    )
}

class CaptureViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<SnapCalApp>()
    private val extractor = ClaudeExtractor()

    var text by mutableStateOf("")
    val images = mutableStateListOf<PickedImage>()
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)
        private set
    var statusIsError by mutableStateOf(false)
        private set
    var summary by mutableStateOf<String?>(null)
        private set
    val review = mutableStateListOf<ReviewItem>()

    fun addImages(uris: List<Uri>) {
        val resolver = app.contentResolver
        for (uri in uris) {
            if (images.size >= MAX_IMAGES) break
            if (images.any { it.uri == uri }) continue
            images.add(PickedImage(uri, Images.thumbnail(resolver, uri)))
        }
    }

    fun removeImage(picked: PickedImage) {
        images.remove(picked)
    }

    fun receiveSharedText(shared: String) {
        text = if (text.isBlank()) shared else "$text\n\n$shared"
    }

    fun extract() {
        if (busy) return
        val currentText = text.trim()
        if (currentText.isEmpty() && images.isEmpty()) {
            setStatus("Paste some text or add a screenshot first", error = true)
            return
        }
        viewModelScope.launch {
            busy = true
            setStatus("Reading with Claude…", error = false)
            try {
                val apiKey = app.settings.apiKey.first()
                if (apiKey.isBlank()) {
                    setStatus("Add your Anthropic API key in Settings first", error = true)
                    return@launch
                }
                val model = app.settings.model.first()
                val encoded = withContext(Dispatchers.IO) {
                    images.map { Images.encode(app.contentResolver, it.uri) }
                }
                val result = extractor.extract(
                    text = currentText.ifEmpty { null },
                    images = encoded,
                    apiKey = apiKey,
                    model = model,
                )
                review.clear()
                review.addAll(result.items.map { ReviewItem(it) })
                summary = result.summary
                setStatus(
                    if (result.items.isEmpty()) "Nothing actionable found in that input."
                    else "Found ${result.items.size} item(s) — review and confirm below.",
                    error = false,
                )
            } catch (e: Exception) {
                setStatus(e.message ?: "Extraction failed", error = true)
            } finally {
                busy = false
            }
        }
    }

    fun saveSelected(onSaved: (events: Int, tasks: Int) -> Unit) {
        val selected = review.filter { it.include && it.title.isNotBlank() }.map { it.toExtracted() }
        if (selected.isEmpty()) {
            setStatus("Nothing selected", error = true)
            return
        }
        viewModelScope.launch {
            val stored = app.repository.add(selected)
            review.clear()
            summary = null
            text = ""
            images.clear()
            setStatus(null, error = false)
            onSaved(stored.count { it.kind == "event" }, stored.count { it.kind == "task" })
        }
    }

    private fun setStatus(message: String?, error: Boolean) {
        status = message
        statusIsError = error
    }
}
