package app.verbatim.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.verbatim.Citation
import app.verbatim.Entry
import app.verbatim.Review
import java.time.LocalDate

enum class CadenceMode(val label: String) {
    None("None"),
    Daily("Daily"),
    Weekly("Weekly"),
}

data class FormDraft(
    val title: String = "",
    val text: String = "",
    val inProgress: Boolean = false,
    val cadenceMode: CadenceMode = CadenceMode.None,
    val weekday: Int = defaultWeekday(),
) {
    val canSave: Boolean
        get() = title.isNotBlank() && text.isNotBlank()

    val isBlank: Boolean
        get() = title.isBlank() && text.isBlank() && !inProgress && cadenceMode == CadenceMode.None

    val cadenceValue: Int
        get() =
            when (cadenceMode) {
                CadenceMode.None -> -1
                CadenceMode.Daily -> 0
                CadenceMode.Weekly -> weekday
            }

    companion object {
        fun defaultWeekday(today: LocalDate = LocalDate.now()): Int = today.dayOfWeek.value % 7 + 1

        fun fromEntry(entry: Entry, today: LocalDate = LocalDate.now()): FormDraft {
            val review = entry.review
            return FormDraft(
                title = entry.title,
                text = entry.text,
                inProgress = entry.progress,
                cadenceMode =
                    when {
                        review == null -> CadenceMode.None
                        review.cadence == 0 -> CadenceMode.Daily
                        else -> CadenceMode.Weekly
                    },
                weekday =
                    if (review != null && review.cadence in 1..7) review.cadence
                    else defaultWeekday(today),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryFormScreen(
    heading: String,
    draft: FormDraft,
    initialDraft: FormDraft,
    existingReview: Review?,
    isEditingExisting: Boolean,
    today: LocalDate,
    snackbarHostState: SnackbarHostState,
    onDraftChange: (FormDraft) -> Unit,
    onReadClipboard: () -> String?,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val colors = LocalVerbatimColors.current
    val titleFocus = remember { FocusRequester() }
    var confirmsDiscard by remember { mutableStateOf(false) }
    var confirmsDelete by remember { mutableStateOf(false) }
    var pendingPaste by remember { mutableStateOf<Pair<String?, String>?>(null) }

    val hasChanges = draft != initialDraft && !draft.isBlank

    fun attemptClose() {
        if (hasChanges) confirmsDiscard = true else onCancel()
    }

    BackHandler(onBack = ::attemptClose)

    // Focus title automatically only for a brand-new empty entry (matching iOS).
    LaunchedEffect(Unit) {
        if (!isEditingExisting && draft.title.isEmpty()) {
            runCatching { titleFocus.requestFocus() }
        }
    }

    val previewReview =
        remember(draft.cadenceValue, existingReview, today) {
            when {
                draft.cadenceValue < 0 -> null
                existingReview?.cadence == draft.cadenceValue -> existingReview
                else -> Review.starting(draft.cadenceValue, today)
            }
        }

    Scaffold(
        containerColor = colors.groupedBackdrop,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = ::attemptClose) {
                        Icon(imageVector = Icons.Rounded.Close, contentDescription = "Cancel")
                    }
                },
                title = { Text(heading) },
                actions = {
                    IconButton(
                        onClick = {
                            val copied = onReadClipboard()
                            if (copied.isNullOrBlank()) return@IconButton
                            val parsed = Citation.paste(copied)
                            val replaces =
                                draft.text.isNotBlank() ||
                                    (parsed.first != null && draft.title.isNotBlank())
                            if (replaces) {
                                pendingPaste = parsed
                            } else {
                                onDraftChange(
                                    draft.copy(
                                        title = parsed.first ?: draft.title,
                                        text = parsed.second,
                                    )
                                )
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentPaste,
                            contentDescription = "Paste from clipboard",
                        )
                    }
                    TextButton(
                        onClick = onSave,
                        enabled = draft.canSave,
                    ) {
                        Text(
                            text = "Save",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
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
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.groupedCard,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    OutlinedTextField(
                        value = draft.title,
                        onValueChange = { onDraftChange(draft.copy(title = it)) },
                        label = { Text("Title") },
                        singleLine = true,
                        keyboardOptions =
                            KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next,
                            ),
                        modifier = Modifier.fillMaxWidth().focusRequester(titleFocus),
                    )
                    OutlinedTextField(
                        value = draft.text,
                        onValueChange = { onDraftChange(draft.copy(text = it)) },
                        label = { Text("Text") },
                        minLines = 6,
                        keyboardOptions =
                            KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.groupedCard,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "In Progress",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "Keep near the top of your library while learning",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = draft.inProgress,
                            onCheckedChange = { onDraftChange(draft.copy(inProgress = it)) },
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )

                    Text(
                        text = "Review schedule",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(10.dp))

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        CadenceMode.entries.forEachIndexed { idx, mode ->
                            SegmentedButton(
                                selected = draft.cadenceMode == mode,
                                onClick = { onDraftChange(draft.copy(cadenceMode = mode)) },
                                shape =
                                    SegmentedButtonDefaults.itemShape(
                                        index = idx,
                                        count = CadenceMode.entries.size,
                                    ),
                            ) {
                                Text(mode.label, maxLines = 1)
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = draft.cadenceMode == CadenceMode.Weekly,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                for (day in 1..7) {
                                    val selected = draft.weekday == day
                                    val fullName = ReviewFormat.weekdayFull(day)
                                    Box(
                                        modifier =
                                            Modifier.weight(1f)
                                                .defaultMinSize(minHeight = 40.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (selected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surfaceVariant
                                                )
                                                .clickable {
                                                    onDraftChange(draft.copy(weekday = day))
                                                }
                                                .semantics { contentDescription = fullName },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = ReviewFormat.weekdayShort(day),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight =
                                                if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color =
                                                if (selected) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    previewReview?.let { review ->
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text =
                                "Next review: ${ReviewFormat.nextReviewSentence(review.day, today)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (isEditingExisting && onDelete != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.groupedCard,
                    modifier =
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { confirmsDelete = true },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text(
                            text = "Delete entry",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmsDiscard) {
        AlertDialog(
            onDismissRequest = { confirmsDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Return without saving this entry?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmsDiscard = false
                        onCancel()
                    }
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmsDiscard = false }) { Text("Keep editing") }
            },
        )
    }

    pendingPaste?.let { (headingText, bodyText) ->
        AlertDialog(
            onDismissRequest = { pendingPaste = null },
            title = { Text("Replace draft text?") },
            text = { Text("Paste over the text currently in this form?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingPaste = null
                        onDraftChange(
                            draft.copy(
                                title = headingText ?: draft.title,
                                text = bodyText,
                            )
                        )
                    }
                ) {
                    Text("Replace")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPaste = null }) { Text("Cancel") }
            },
        )
    }

    if (confirmsDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmsDelete = false },
            title = { Text("Delete entry?") },
            text = { Text("This permanently removes the entry and its review schedule.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmsDelete = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmsDelete = false }) { Text("Cancel") }
            },
        )
    }
}
