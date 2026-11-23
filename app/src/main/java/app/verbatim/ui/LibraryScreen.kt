package app.verbatim.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.verbatim.Citation
import app.verbatim.Entry
import java.time.LocalDate

enum class LibrarySection(val title: String) {
    Due("Due"),
    InProgress("In Progress"),
    Library("Library"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    entries: List<Entry>,
    today: LocalDate,
    bookOrder: Boolean,
    listState: LazyListState,
    snackbarHostState: SnackbarHostState,
    onAddEntry: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onStartRecall: (String) -> Unit,
    onEditEntry: (String) -> Unit,
    onDuplicateEntry: (String) -> Unit,
    onDeleteEntry: (String) -> Unit,
) {
    val colors = LocalVerbatimColors.current
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }

    val groupedSections =
        remember(entries, today, bookOrder) {
            val comparator = if (bookOrder) Citation.order else String.CASE_INSENSITIVE_ORDER
            val grouped =
                entries.groupBy { entry ->
                    when {
                        entry.review?.due(today) == true -> LibrarySection.Due
                        entry.progress -> LibrarySection.InProgress
                        else -> LibrarySection.Library
                    }
                }
            LibrarySection.entries.mapNotNull { section ->
                val items =
                    grouped[section]
                        ?.sortedWith { a, b -> comparator.compare(a.title, b.title) }
                        ?: emptyList()
                if (items.isEmpty()) null else section to items
            }
        }

    Scaffold(
        containerColor = colors.groupedBackdrop,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Verbatim",
                        style = VerbatimTypography.libraryWordmark,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings",
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddEntry,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add entry",
                )
            }
        },
    ) { innerPadding ->
        if (entries.isEmpty()) {
            EmptyLibraryState(
                onAddEntry = onAddEntry,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            ) {
                groupedSections.forEachIndexed { sectionIndex, (section, items) ->
                    item(key = "header_${section.name}") {
                        if (sectionIndex > 0) Spacer(Modifier.height(20.dp))
                        SectionHeaderRow(section = section, count = items.size)
                    }

                    itemsIndexed(
                        items = items,
                        key = { _, entry -> "${section.name}_${entry.id}" },
                    ) { idx, entry ->
                        val isFirst = idx == 0
                        val isLast = idx == items.lastIndex
                        val shape =
                            RoundedCornerShape(
                                topStart = if (isFirst) 16.dp else 0.dp,
                                topEnd = if (isFirst) 16.dp else 0.dp,
                                bottomStart = if (isLast) 16.dp else 0.dp,
                                bottomEnd = if (isLast) 16.dp else 0.dp,
                            )
                        Column(modifier = Modifier.fillMaxWidth().clip(shape)) {
                            LibraryEntryRow(
                                entry = entry,
                                section = section,
                                today = today,
                                onTap = {
                                    if (section == LibrarySection.Due) onStartRecall(entry.id)
                                    else onOpenDetail(entry.id)
                                },
                                onAlternate = {
                                    if (section == LibrarySection.Due) onOpenDetail(entry.id)
                                    else onStartRecall(entry.id)
                                },
                                onEdit = { onEditEntry(entry.id) },
                                onDuplicate = { onDuplicateEntry(entry.id) },
                                onDelete = { pendingDeleteId = entry.id },
                            )
                            if (!isLast) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().background(colors.groupedCard)
                                ) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 16.dp),
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val pendingDelete = pendingDeleteId?.let { id -> entries.firstOrNull { it.id == id } }
    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete entry?") },
            text = {
                Text("“${target.title}” and its review schedule will be permanently removed.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteId = null
                        onDeleteEntry(target.id)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SectionHeaderRow(section: LibrarySection, count: Int) {
    val colors = LocalVerbatimColors.current
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color =
                if (section == LibrarySection.Due) colors.dueTint
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyLibraryState(onAddEntry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.ChatBubbleOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Nothing yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Add text you’d like to learn and recite from memory.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        FilledTonalButton(onClick = onAddEntry) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Add entry")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun LibraryEntryRow(
    entry: Entry,
    section: LibrarySection,
    today: LocalDate,
    onTap: () -> Unit,
    onAlternate: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalVerbatimColors.current
    val fontScale = LocalDensity.current.fontScale
    val haptic = LocalHapticFeedback.current
    var menuExpanded by remember { mutableStateOf(false) }

    val currentOnAlternate by rememberUpdatedState(onAlternate)
    val currentOnEdit by rememberUpdatedState(onEdit)
    val currentOnDuplicate by rememberUpdatedState(onDuplicate)
    val currentOnDelete by rememberUpdatedState(onDelete)

    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnAlternate()
                        false
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnEdit()
                        false
                    }
                    SwipeToDismissBoxValue.Settled -> false
                }
            },
            positionalThreshold = { totalDistance -> totalDistance * 0.35f },
        )

    val alternateLabel = if (section == LibrarySection.Due) "Preview entry" else "Recite"
    val rowDescription =
        buildString {
            append(entry.title)
            entry.review?.let { review ->
                append(". ")
                append(ReviewFormat.accessibilitySummary(review, today))
            }
            if (section == LibrarySection.Due) {
                append(" Opens the review without showing the text.")
            }
        }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val isStart = direction == SwipeToDismissBoxValue.StartToEnd
            val isEnd = direction == SwipeToDismissBoxValue.EndToStart
            val bgColor =
                when {
                    isStart && section == LibrarySection.Due ->
                        MaterialTheme.colorScheme.secondaryContainer
                    isStart -> colors.recite.copy(alpha = 0.16f)
                    isEnd -> MaterialTheme.colorScheme.primaryContainer
                    else -> colors.groupedCard
                }
            val iconTint =
                when {
                    isStart && section == LibrarySection.Due ->
                        MaterialTheme.colorScheme.onSecondaryContainer
                    isStart -> colors.recite
                    isEnd -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            Box(
                modifier =
                    Modifier.fillMaxSize().background(bgColor).padding(horizontal = 20.dp),
                contentAlignment = if (isStart) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                if (isStart) {
                    Icon(
                        imageVector =
                            if (section == LibrarySection.Due) Icons.Rounded.Visibility
                            else Icons.Rounded.Mic,
                        contentDescription = null,
                        tint = iconTint,
                    )
                } else if (isEnd) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        tint = iconTint,
                    )
                }
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxWidth().background(colors.groupedCard)) {
            val contentModifier =
                Modifier.fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .combinedClickable(
                        onClick = onTap,
                        onLongClick = { menuExpanded = true },
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = rowDescription
                        customActions =
                            listOf(
                                CustomAccessibilityAction(alternateLabel) {
                                    currentOnAlternate()
                                    true
                                },
                                CustomAccessibilityAction("Edit") {
                                    currentOnEdit()
                                    true
                                },
                                CustomAccessibilityAction("Duplicate") {
                                    currentOnDuplicate()
                                    true
                                },
                                CustomAccessibilityAction("Delete") {
                                    currentOnDelete()
                                    true
                                },
                            )
                    }

            if (fontScale >= 1.4f) {
                Column(modifier = contentModifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (entry.review != null || section == LibrarySection.Due) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RowMetadata(entry = entry, section = section, today = today)
                        }
                    }
                }
            } else {
                Row(
                    modifier = contentModifier,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (entry.review != null || section == LibrarySection.Due) {
                        Spacer(Modifier.width(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RowMetadata(entry = entry, section = section, today = today)
                        }
                    }
                }
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(alternateLabel) },
                    leadingIcon = {
                        Icon(
                            imageVector =
                                if (section == LibrarySection.Due) Icons.Rounded.Visibility
                                else Icons.Rounded.Mic,
                            contentDescription = null,
                            tint =
                                if (section == LibrarySection.Due) MaterialTheme.colorScheme.onSurfaceVariant
                                else colors.recite,
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onAlternate()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onEdit()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Duplicate") },
                    leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onDuplicate()
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun RowMetadata(entry: Entry, section: LibrarySection, today: LocalDate) {
    val colors = LocalVerbatimColors.current
    val review = entry.review ?: return
    if (section != LibrarySection.Due) {
        Text(
            text = ReviewFormat.rowDateLabel(review.day, today),
            style = VerbatimTypography.metadata,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
    CadenceBadge(cadence = review.cadence)
    if (section == LibrarySection.Due) {
        Icon(
            imageVector = Icons.Rounded.Mic,
            contentDescription = null,
            tint = colors.recite.copy(alpha = 0.85f),
            modifier = Modifier.size(18.dp),
        )
    }
}
