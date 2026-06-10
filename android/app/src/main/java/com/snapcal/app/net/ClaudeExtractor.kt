package com.snapcal.app.net

import com.snapcal.app.data.ExtractionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Calls the Claude Messages API over HTTPS (vision + structured outputs).
 *
 * The official Anthropic Java SDK targets server-side JVMs and doesn't document
 * Android support, so this client speaks the documented REST shape directly:
 * https://platform.claude.com/docs — POST /v1/messages with image blocks and
 * output_config json_schema.
 */
class ClaudeExtractor(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .build(),
) {
    class ApiException(message: String) : Exception(message)

    data class EncodedImage(val mediaType: String, val base64Data: String)

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun extract(
        text: String?,
        images: List<EncodedImage>,
        apiKey: String,
        model: String,
    ): ExtractionResult = withContext(Dispatchers.IO) {
        require(!text.isNullOrBlank() || images.isNotEmpty()) { "Nothing to extract" }

        val body = buildRequestBody(text, images, model)
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(errorMessage(response.code, payload))
            parseResult(payload)
        }
    }

    private fun errorMessage(code: Int, payload: String): String {
        val detail = try {
            json.parseToJsonElement(payload)
                .jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
        return when (code) {
            401 -> "Claude rejected the API key — check it in Settings"
            429 -> "Rate limited — try again in a moment"
            else -> "Claude API error ($code): ${detail ?: "unknown"}"
        }
    }

    private fun parseResult(payload: String): ExtractionResult {
        val content = json.parseToJsonElement(payload).jsonObject["content"]?.jsonArray
            ?: throw ApiException("Malformed API response")
        val textBlock = content.firstOrNull {
            it.jsonObject["type"]?.jsonPrimitive?.content == "text"
        } ?: throw ApiException("No text block in API response")
        val resultJson = textBlock.jsonObject["text"]?.jsonPrimitive?.content
            ?: throw ApiException("Empty text block in API response")
        return json.decodeFromString(resultJson)
    }

    private fun buildRequestBody(text: String?, images: List<EncodedImage>, model: String): JsonObject {
        val now = ZonedDateTime.now()
        val nowStr = now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' HH:mm", Locale.US))
        val tzName = now.zone.id

        val instruction = buildString {
            append("Current date and time: $nowStr (timezone: $tzName).")
            if (images.isNotEmpty()) {
                append("\n\nExtract events and tasks from the ${images.size} screenshot(s) above.")
            }
            if (!text.isNullOrBlank()) {
                append("\n\nExtract events and tasks from this text:\n\n$text")
            }
        }

        return buildJsonObject {
            put("model", model)
            put("max_tokens", 16000)
            putJsonObject("thinking") { put("type", "adaptive") }
            put("system", SYSTEM_PROMPT)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        for (image in images) {
                            addJsonObject {
                                put("type", "image")
                                putJsonObject("source") {
                                    put("type", "base64")
                                    put("media_type", image.mediaType)
                                    put("data", image.base64Data)
                                }
                            }
                        }
                        addJsonObject {
                            put("type", "text")
                            put("text", instruction)
                        }
                    })
                }
            }
            putJsonObject("output_config") {
                putJsonObject("format") {
                    put("type", "json_schema")
                    put("schema", extractionSchema())
                }
            }
        }
    }

    /** JSON schema mirroring [ExtractionResult]; nullables use anyOf with null. */
    private fun extractionSchema(): JsonObject {
        fun nullable(type: String) = buildJsonObject {
            putJsonArray("anyOf") {
                addJsonObject { put("type", type) }
                addJsonObject { put("type", "null") }
            }
        }

        val itemSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("kind") {
                    put("type", "string")
                    putJsonArray("enum") { add("event"); add("task") }
                }
                putJsonObject("title") { put("type", "string") }
                put("start", nullable("string"))
                put("end", nullable("string"))
                putJsonObject("all_day") { put("type", "boolean") }
                put("due", nullable("string"))
                put("location", nullable("string"))
                putJsonObject("with_people") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                }
                put("notes", nullable("string"))
                putJsonObject("confidence") {
                    put("type", "string")
                    putJsonArray("enum") { add("high"); add("medium"); add("low") }
                }
                put("source_quote", nullable("string"))
            }
            putJsonArray("required") {
                add("kind"); add("title"); add("start"); add("end"); add("all_day")
                add("due"); add("location"); add("with_people"); add("notes")
                add("confidence"); add("source_quote")
            }
            put("additionalProperties", false)
        }

        return buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("items") {
                    put("type", "array")
                    put("items", itemSchema)
                }
                putJsonObject("summary") { put("type", "string") }
            }
            putJsonArray("required") { add("items"); add("summary") }
            put("additionalProperties", false)
        }
    }

    companion object {
        // Mirrors app/extractor.py in the web backend — keep the two in sync.
        val SYSTEM_PROMPT = """
            You are the extraction engine for SnapCal, a calendar and to-do app. You receive raw text (SMS/chat messages, emails, notes) and/or screenshots (of conversations, flyers, emails, booking confirmations). Extract every actionable calendar event and to-do task for the user.

            Rules:
            - Use the provided current date, time, and timezone to resolve relative dates ("tomorrow", "next Friday", "this weekend", "in two weeks"). An upcoming commitment must never resolve to a past date; if a named weekday already passed this week, use its next occurrence.
            - kind="event" for anything happening at a specific date (appointments, meetings, dinners, flights, games, parties). kind="task" for things to do with no fixed occurrence time (errands, "don't forget to...", homework, bills) — set "due" when a deadline is stated or clearly implied.
            - Dates/times: local ISO 8601 with no timezone suffix, e.g. "2026-06-14T15:30". If only a date is known, set all_day=true and use the bare date "2026-06-14". Leave end null unless an end time or duration is stated.
            - title: short and specific, written from the user's perspective ("Dinner with Sam", "Dentist appointment", "Pick up dry cleaning").
            - location only if stated or unambiguous. with_people: names of the other participants.
            - source_quote: the exact words that triggered the item, kept short.
            - confidence: "high" = explicit date and time; "medium" = some inference was needed; "low" = vague intent ("we should hang out soon") — skip these entirely unless the intent to schedule is clear.
            - Do NOT invent details. Skip pleasantries, past events, and declined or cancelled plans. If a plan is rescheduled within the conversation, emit one item with the final time and mention the change in notes.
            - Screenshots: read all visible text. In chat screenshots, right-aligned bubbles are usually the user ("me"); infer who is committing to what.
            - summary: one short sentence describing what was found. If nothing is actionable, return an empty items list and say so in summary.
        """.trimIndent()
    }
}
