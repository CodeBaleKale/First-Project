package com.snapcal.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.snapcal.app.SnapCalApp
import com.snapcal.app.data.StoredItem
import com.snapcal.app.util.CalendarIntents
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun CalendarScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as SnapCalApp
    val items by app.repository.items.collectAsState()
    val scope = rememberCoroutineScope()

    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDay by remember { mutableStateOf(LocalDate.now()) }

    val eventsByDay = items
        .filter { it.kind == "event" && it.start != null }
        .groupBy { it.start!!.substringBefore("T") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "previous month")
            }
            Text(
                month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { month = month.plusMonths(1) }) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "next month")
            }
            TextButton(onClick = {
                month = YearMonth.now()
                selectedDay = LocalDate.now()
            }) { Text("Today") }
        }

        MonthGrid(
            month = month,
            eventsByDay = eventsByDay,
            selectedDay = selectedDay,
            onSelect = { selectedDay = it },
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    selectedDay.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                    style = MaterialTheme.typography.titleMedium,
                )
                val dayEvents = (eventsByDay[selectedDay.toString()] ?: emptyList())
                    .sortedBy { it.start }
                if (dayEvents.isEmpty()) {
                    Text(
                        "Nothing scheduled.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                for (event in dayEvents) {
                    EventRow(
                        event = event,
                        onInsert = { CalendarIntents.insertEvent(context, event) },
                        onDelete = { scope.launch { app.repository.delete(event.id) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    eventsByDay: Map<String, List<StoredItem>>,
    selectedDay: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            for (label in listOf("S", "M", "T", "W", "T", "F", "S")) {
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // dayOfWeek: SUNDAY=7 in ISO; convert so Sunday is column 0.
        val firstColumn = month.atDay(1).dayOfWeek.value % 7
        var day = month.atDay(1).minusDays(firstColumn.toLong())
        val today = LocalDate.now()
        repeat(6) {
            Row(Modifier.fillMaxWidth()) {
                repeat(7) {
                    val current = day
                    val inMonth = YearMonth.from(current) == month
                    val hasEvents = eventsByDay.containsKey(current.toString())
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    current == selectedDay -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.background
                                }
                            )
                            .clickable { onSelect(current) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            current.dayOfMonth.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (current == today) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                !inMonth -> MaterialTheme.colorScheme.outline
                                current == today -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onBackground
                            },
                        )
                        if (hasEvents) {
                            Box(
                                Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                    day = day.plusDays(1)
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: StoredItem, onInsert: () -> Unit, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (event.allDay || event.start?.contains("T") != true) "all day"
            else event.start.substringAfter("T").take(5),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 10.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(event.title)
            event.location?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onInsert) {
            Icon(
                Icons.Default.Send,
                contentDescription = "Add to phone calendar",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}
