package com.snapcal.app.util

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import com.snapcal.app.data.StoredItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Hands an event to the user's real calendar app (Google Calendar etc.) via
 * ACTION_INSERT — pre-filled editor, no calendar permissions needed.
 */
object CalendarIntents {

    fun insertEvent(context: Context, item: StoredItem) {
        val start = item.start
        if (start == null) {
            Toast.makeText(context, "This item has no date", Toast.LENGTH_SHORT).show()
            return
        }

        val zone = ZoneId.systemDefault()
        val beginMillis: Long
        val endMillis: Long
        if (item.allDay || !start.contains("T")) {
            val day = LocalDate.parse(start.substringBefore("T"))
            beginMillis = day.atStartOfDay(zone).toInstant().toEpochMilli()
            endMillis = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        } else {
            val begin = LocalDateTime.parse(start)
            beginMillis = begin.atZone(zone).toInstant().toEpochMilli()
            endMillis = item.end?.takeIf { it.contains("T") }
                ?.let { LocalDateTime.parse(it).atZone(zone).toInstant().toEpochMilli() }
                ?: begin.plusHours(1).atZone(zone).toInstant().toEpochMilli()
        }

        val description = listOfNotNull(
            item.notes,
            item.withPeople.takeIf { it.isNotEmpty() }?.let { "With: ${it.joinToString(", ")}" },
        ).joinToString(" | ")

        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, item.title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, item.allDay)

        item.location?.let { intent.putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
        if (description.isNotEmpty()) {
            intent.putExtra(CalendarContract.Events.DESCRIPTION, description)
        }

        try {
            context.startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(context, "No calendar app found", Toast.LENGTH_SHORT).show()
        }
    }
}
