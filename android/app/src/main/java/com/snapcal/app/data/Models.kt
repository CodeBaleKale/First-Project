package com.snapcal.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExtractedItem(
    val kind: String, // "event" | "task"
    val title: String,
    // Events: local ISO 8601 without timezone, e.g. "2026-06-14T15:30",
    // or a bare date "2026-06-14" when allDay is true.
    val start: String? = null,
    val end: String? = null,
    @SerialName("all_day") val allDay: Boolean = false,
    // Tasks: optional deadline, same format as start.
    val due: String? = null,
    val location: String? = null,
    @SerialName("with_people") val withPeople: List<String> = emptyList(),
    val notes: String? = null,
    val confidence: String = "medium", // "high" | "medium" | "low"
    @SerialName("source_quote") val sourceQuote: String? = null,
)

@Serializable
data class ExtractionResult(
    val items: List<ExtractedItem>,
    val summary: String,
)

@Serializable
data class StoredItem(
    val id: Long,
    val kind: String,
    val title: String,
    val start: String? = null,
    val end: String? = null,
    val allDay: Boolean = false,
    val due: String? = null,
    val location: String? = null,
    val withPeople: List<String> = emptyList(),
    val notes: String? = null,
    val done: Boolean = false,
)

fun ExtractedItem.toStored(id: Long) = StoredItem(
    id = id,
    kind = kind,
    title = title,
    start = start,
    end = end,
    allDay = allDay,
    due = due,
    location = location,
    withPeople = withPeople,
    notes = notes,
)
