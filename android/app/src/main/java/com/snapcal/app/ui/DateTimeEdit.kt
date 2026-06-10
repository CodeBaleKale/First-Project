package com.snapcal.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** "2026-06-14T15:30" / "2026-06-14" -> (LocalDate?, LocalTime?) */
fun parseStart(value: String?): Pair<LocalDate?, LocalTime?> {
    if (value.isNullOrBlank()) return null to null
    return try {
        if (value.contains("T")) {
            val date = LocalDate.parse(value.substringBefore("T"))
            val time = LocalTime.parse(value.substringAfter("T"))
            date to time
        } else {
            LocalDate.parse(value) to null
        }
    } catch (_: Exception) {
        null to null
    }
}

fun combineStart(date: LocalDate?, time: LocalTime?, allDay: Boolean): String? = when {
    date == null -> null
    allDay || time == null -> date.toString()
    else -> "${date}T${time.format(DateTimeFormatter.ofPattern("HH:mm"))}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateButton(date: LocalDate?, label: String, onChange: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) {
        Text(date?.format(DateTimeFormatter.ofPattern("EEE, MMM d")) ?: label)
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: LocalDate.now())
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeButton(time: LocalTime?, enabled: Boolean, onChange: (LocalTime) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, enabled = enabled) {
        Text(time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "time")
    }
    if (open) {
        val state = rememberTimePickerState(
            initialHour = time?.hour ?: 12,
            initialMinute = time?.minute ?: 0,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(LocalTime.of(state.hour, state.minute))
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
            text = { TimePicker(state = state) },
        )
    }
}
