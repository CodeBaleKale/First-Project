package com.snapcal.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.snapcal.app.SnapCalApp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TasksScreen(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as SnapCalApp
    val items by app.repository.items.collectAsState()
    val scope = rememberCoroutineScope()
    var showDone by remember { mutableStateOf(false) }

    val tasks = items
        .filter { it.kind == "task" && (showDone || !it.done) }
        .sortedWith(compareBy(nullsLast()) { it.due })
    val today = LocalDate.now().toString()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "To-dos",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Text("show completed", style = MaterialTheme.typography.bodySmall)
            Switch(
                checked = showDone,
                onCheckedChange = { showDone = it },
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        if (tasks.isEmpty()) {
            Text(
                "No to-dos yet — capture a text to get started.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 18.dp),
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tasks, key = { it.id }) { task ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = task.done,
                            onCheckedChange = {
                                scope.launch { app.repository.setDone(task.id, it) }
                            },
                        )
                        Text(
                            task.title,
                            modifier = Modifier.weight(1f),
                            textDecoration = if (task.done) TextDecoration.LineThrough else null,
                            color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        task.due?.let { due ->
                            val dueDay = due.substringBefore("T")
                            val overdue = !task.done && dueDay < today
                            Text(
                                "due " + LocalDate.parse(dueDay)
                                    .format(DateTimeFormatter.ofPattern("MMM d")),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (overdue) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.secondary,
                            )
                        }
                        IconButton(onClick = { scope.launch { app.repository.delete(task.id) } }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }
}
