package com.snapcal.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.snapcal.app.CaptureViewModel
import com.snapcal.app.MAX_IMAGES
import com.snapcal.app.ReviewItem

@Composable
fun CaptureScreen(
    viewModel: CaptureViewModel,
    modifier: Modifier = Modifier,
    onSaved: (events: Int, tasks: Int) -> Unit,
) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES),
    ) { uris -> viewModel.addImages(uris) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Paste a text, or share a screenshot from any app — SnapCal pulls out the events and to-dos.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = viewModel.text,
            onValueChange = { viewModel.text = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            placeholder = { Text("e.g. “dentist moved my cleaning to next thursday 2:30pm, and don’t forget mom’s gift before saturday”") },
        )

        if (viewModel.images.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (picked in viewModel.images) {
                    Box {
                        picked.thumbnail?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "screenshot",
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        } ?: Box(Modifier.size(96.dp)) { Text("image") }
                        IconButton(
                            onClick = { viewModel.removeImage(picked) },
                            modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "remove")
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text("Add screenshots") }

            Button(onClick = { viewModel.extract() }, enabled = !viewModel.busy) {
                if (viewModel.busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Extract")
                }
            }
        }

        viewModel.status?.let {
            Text(
                it,
                color = if (viewModel.statusIsError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        viewModel.summary?.let {
            Text(it, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        for (item in viewModel.review) {
            ReviewCard(item)
        }

        if (viewModel.review.isNotEmpty()) {
            Button(
                onClick = { viewModel.saveSelected(onSaved) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add selected to SnapCal") }
        }
    }
}

@Composable
private fun ReviewCard(item: ReviewItem) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = item.include, onCheckedChange = { item.include = it })
                FilterChip(
                    selected = item.kind == "event",
                    onClick = { item.kind = "event" },
                    label = { Text("Event") },
                )
                FilterChip(
                    selected = item.kind == "task",
                    onClick = { item.kind = "task" },
                    label = { Text("Task") },
                )
                AssistChip(onClick = {}, label = { Text(item.confidence) })
            }

            OutlinedTextField(
                value = item.title,
                onValueChange = { item.title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Title") },
            )

            if (item.kind == "event") {
                val (date, time) = parseStart(item.start)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DateButton(date, "date") { item.start = combineStart(it, time, item.allDay) }
                    TimeButton(time, enabled = !item.allDay) {
                        item.start = combineStart(date, it, item.allDay)
                    }
                    Checkbox(checked = item.allDay, onCheckedChange = {
                        item.allDay = it
                        item.start = combineStart(date, time, it)
                    })
                    Text("all-day")
                }
                OutlinedTextField(
                    value = item.location,
                    onValueChange = { item.location = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Location") },
                )
            } else {
                val (due, _) = parseStart(item.due)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Due:")
                    DateButton(due, "no due date") { item.due = it.toString() }
                }
            }

            item.sourceQuote?.let {
                Text(
                    "“$it”",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
