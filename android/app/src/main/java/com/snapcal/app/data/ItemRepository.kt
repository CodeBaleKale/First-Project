package com.snapcal.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Simple JSON-file-backed store. Plenty for a personal list of events/tasks;
 * swap for Room if the data ever outgrows a single file.
 */
class ItemRepository(context: Context) {
    private val file = File(context.filesDir, "items.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val mutex = Mutex()

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<StoredItem>> = _items

    private fun load(): List<StoredItem> = try {
        if (file.exists()) json.decodeFromString(file.readText()) else emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    private suspend fun persist(items: List<StoredItem>) = withContext(Dispatchers.IO) {
        val tmp = File(file.parentFile, "items.json.tmp")
        tmp.writeText(json.encodeToString(items))
        tmp.renameTo(file)
    }

    suspend fun add(newItems: List<ExtractedItem>): List<StoredItem> = mutex.withLock {
        var nextId = (_items.value.maxOfOrNull { it.id } ?: 0L) + 1
        val stored = newItems.map { it.toStored(nextId++) }
        val updated = _items.value + stored
        persist(updated)
        _items.value = updated
        stored
    }

    suspend fun setDone(id: Long, done: Boolean) = mutex.withLock {
        val updated = _items.value.map { if (it.id == id) it.copy(done = done) else it }
        persist(updated)
        _items.value = updated
    }

    suspend fun delete(id: Long) = mutex.withLock {
        val updated = _items.value.filterNot { it.id == id }
        persist(updated)
        _items.value = updated
    }
}
