package app.verbatim.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.verbatim.AddEntriesResult
import app.verbatim.Library

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    hour: Int,
    minute: Int,
    bookOrder: Boolean,
    notificationsEnabled: Boolean,
    needsNotificationPermission: Boolean,
    pendingReplace: Library?,
    currentEntryCount: Int,
    addResult: AddEntriesResult?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onBookOrderChange: (Boolean) -> Unit,
    onExportBackup: () -> Unit,
    onReplaceFromBackup: () -> Unit,
    onAddFromBackup: () -> Unit,
    onConfirmReplace: () -> Unit,
    onDismissReplace: () -> Unit,
    onDismissAddResult: () -> Unit,
) {
    val colors = LocalVerbatimColors.current
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }

    val formattedTime =
        remember(hour, minute) { ReviewFormat.formatReminderTime(context, hour, minute) }

    Scaffold(
        containerColor = colors.groupedBackdrop,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                title = { Text("Settings") },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.groupedBackdrop,
                        scrolledContainerColor = colors.groupedBackdrop,
                    ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Reminders section
            Column {
                SettingsSectionHeader("Reminders")
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.groupedCard,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clickable { showTimePicker = true }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Reminder time",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = "Daily notification when reviews are due",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = formattedTime,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 54.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )

                        if (needsNotificationPermission) {
                            Row(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .clickable(onClick = onRequestNotificationPermission)
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Allow reminders",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = "Grant notification permission for due reviews",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 54.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }

                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clickable(onClick = onOpenNotificationSettings)
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector =
                                    if (notificationsEnabled) Icons.Rounded.NotificationsActive
                                    else Icons.Rounded.NotificationsOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Notification settings",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text =
                                        if (notificationsEnabled) "Notifications are on"
                                        else "Notifications are off in Android Settings",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // Library section
            Column {
                SettingsSectionHeader("Library")
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.groupedCard,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Sort order",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(10.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = !bookOrder,
                                onClick = { onBookOrderChange(false) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            ) {
                                Text("Alphabetical", maxLines = 1)
                            }
                            SegmentedButton(
                                selected = bookOrder,
                                onClick = { onBookOrderChange(true) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            ) {
                                Text("Bible book order", maxLines = 1)
                            }
                        }
                    }
                }
            }

            // Library files section (3 side-by-side action tiles matching iOS ergonomics)
            Column {
                SettingsSectionHeader("Library files")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FileActionTile(
                        title = "Export",
                        icon = Icons.Rounded.FileUpload,
                        onClick = onExportBackup,
                        modifier = Modifier.weight(1f),
                    )
                    FileActionTile(
                        title = "Replace",
                        icon = Icons.Rounded.FileDownload,
                        onClick = onReplaceFromBackup,
                        modifier = Modifier.weight(1f),
                    )
                    FileActionTile(
                        title = "Add",
                        icon = Icons.Rounded.LibraryAdd,
                        onClick = onAddFromBackup,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "“Add” imports new entries unscheduled, skipping titles you already have.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showTimePicker) {
        val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
        val pickerState =
            rememberTimePickerState(
                initialHour = hour,
                initialMinute = minute,
                is24Hour = is24Hour,
            )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Reminder time") },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTimePicker = false
                        onTimeSelected(pickerState.hour, pickerState.minute)
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
        )
    }

    pendingReplace?.let { incoming ->
        AlertDialog(
            onDismissRequest = onDismissReplace,
            title = { Text("Replace library?") },
            text = {
                Text(
                    "Replace your $currentEntryCount entries and reminder time with the ${incoming.entries.size} entries and reminder time in this backup? This can’t be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmReplace) {
                    Text("Replace", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissReplace) { Text("Cancel") }
            },
        )
    }

    addResult?.let { summary ->
        val title =
            if (summary.added > 0) {
                "Added ${summary.added} ${if (summary.added == 1) "entry" else "entries"}"
            } else {
                "No new entries"
            }
        val body =
            when {
                summary.added == 0 && summary.skippedInLibrary == 0 ->
                    "This file has no entries."
                summary.added == 0 ->
                    "Every entry in this file is already in your library."
                summary.skippedInLibrary > 0 && summary.skippedRepeated > 0 ->
                    "Skipped ${summary.skippedInLibrary} already in your library and ${summary.skippedRepeated} repeated in this file."
                summary.skippedInLibrary > 0 ->
                    "Skipped ${summary.skippedInLibrary} already in your library."
                summary.skippedRepeated > 0 ->
                    "Skipped ${summary.skippedRepeated} repeated in this file."
                else -> "Imported unscheduled and ready to learn."
            }
        AlertDialog(
            onDismissRequest = onDismissAddResult,
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                TextButton(onClick = onDismissAddResult) { Text("OK") }
            },
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Composable
private fun FileActionTile(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalVerbatimColors.current
    Surface(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = colors.groupedCard,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}
