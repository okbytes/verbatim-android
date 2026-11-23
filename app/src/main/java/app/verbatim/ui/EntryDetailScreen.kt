package app.verbatim.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.verbatim.Entry
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryDetailScreen(
    entry: Entry,
    today: LocalDate,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onPractice: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalVerbatimColors.current
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val titleThresholdPx = with(density) { 56.dp.toPx() }
    val showBarTitle by remember { derivedStateOf { scrollState.value > titleThresholdPx } }
    val barTitleAlpha by
        animateFloatAsState(
            targetValue = if (showBarTitle) 1f else 0f,
            animationSpec = tween(160),
            label = "detailBarTitleAlpha",
        )
    val fabExpanded by remember { derivedStateOf { scrollState.value < titleThresholdPx } }

    var menuExpanded by remember { mutableStateOf(false) }
    var confirmsDelete by rememberSaveable { mutableStateOf(false) }

    val displayText = remember(entry.text) { Entry.normalizeLineEndings(entry.text).trimEnd() }

    Scaffold(
        containerColor = colors.readingSurface,
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
                title = {
                    Text(
                        text = entry.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alpha(barTitleAlpha),
                    )
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(imageVector = Icons.Rounded.Edit, contentDescription = "Edit entry")
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(imageVector = Icons.Rounded.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Copy text") },
                                leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onCopy()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Duplicate") },
                                leadingIcon = { Icon(Icons.Rounded.FileCopy, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onDuplicate()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Share") },
                                leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onShare()
                                },
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(
                                text = { Text("Delete entry", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    confirmsDelete = true
                                },
                            )
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.readingSurface,
                        scrolledContainerColor = colors.readingSurface,
                    ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onPractice,
                expanded = fabExpanded,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = null,
                    )
                },
                text = { Text("Recite") },
                containerColor = colors.recite,
                contentColor = colors.onRecite,
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp)
                    .padding(top = 12.dp, bottom = 108.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = entry.title,
                style = VerbatimTypography.entryLargeTitle,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            if (entry.progress || entry.review != null) {
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (entry.progress) {
                        Text(
                            text = "In Progress",
                            style = VerbatimTypography.metadata,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                    entry.review?.let { review ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            CadenceBadge(cadence = review.cadence)
                            Text(
                                text = ReviewFormat.scheduleName(review.cadence),
                                style = VerbatimTypography.metadata,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val isDue = review.due(today)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            MetadataChip(
                                label = "DUE",
                                containerColor =
                                    if (isDue) colors.dueTint.copy(alpha = 0.16f)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor =
                                    if (isDue) colors.dueTint
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!isDue) {
                                Text(
                                    text = ReviewFormat.rowDateLabel(review.day, today),
                                    style = VerbatimTypography.metadata,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            SelectionContainer(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = displayText,
                    style = VerbatimTypography.entryBody,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (confirmsDelete) {
        AlertDialog(
            onDismissRequest = { confirmsDelete = false },
            title = { Text("Delete entry?") },
            text = {
                Text("This permanently removes “${entry.title}” and its review schedule.")
            },
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
