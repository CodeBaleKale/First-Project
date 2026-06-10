package com.snapcal.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.snapcal.app.ui.CalendarScreen
import com.snapcal.app.ui.CaptureScreen
import com.snapcal.app.ui.SettingsScreen
import com.snapcal.app.ui.SnapCalTheme
import com.snapcal.app.ui.TasksScreen
import kotlinx.coroutines.launch

private enum class Tab(val label: String) { Capture("Capture"), Calendar("Calendar"), Tasks("Tasks") }

class MainActivity : ComponentActivity() {

    private val captureViewModel: CaptureViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShareIntent(intent)
        setContent {
            SnapCalTheme { SnapCalScaffold(captureViewModel) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    /** Accept text or screenshots shared from any other app. */
    private fun handleShareIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { captureViewModel.receiveSharedText(it) }
                intentUri(intent)?.let { captureViewModel.addImages(listOf(it)) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intentUris(intent)?.let { captureViewModel.addImages(it) }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun intentUri(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

    @Suppress("DEPRECATION")
    private fun intentUris(intent: Intent): List<Uri>? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnapCalScaffold(captureViewModel: CaptureViewModel) {
    var tab by remember { mutableStateOf(Tab.Capture) }
    var showSettings by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showSettings) "Settings" else "SnapCal") },
                actions = {
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        bottomBar = {
            if (!showSettings) {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == Tab.Capture,
                        onClick = { tab = Tab.Capture },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        label = { Text(Tab.Capture.label) },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.Calendar,
                        onClick = { tab = Tab.Calendar },
                        icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                        label = { Text(Tab.Calendar.label) },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.Tasks,
                        onClick = { tab = Tab.Tasks },
                        icon = { Icon(Icons.Default.Done, contentDescription = null) },
                        label = { Text(Tab.Tasks.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        if (showSettings) {
            SettingsScreen(modifier = modifier, onDone = { showSettings = false })
        } else {
            when (tab) {
                Tab.Capture -> CaptureScreen(
                    viewModel = captureViewModel,
                    modifier = modifier,
                    onSaved = { events, tasks ->
                        scope.launch { snackbar.showSnackbar("Added $events event(s), $tasks task(s)") }
                        tab = if (events > 0) Tab.Calendar else Tab.Tasks
                    },
                )
                Tab.Calendar -> CalendarScreen(modifier = modifier)
                Tab.Tasks -> TasksScreen(modifier = modifier)
            }
        }
    }
}
