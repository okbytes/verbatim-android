package app.verbatim.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.verbatim.Recall
import kotlinx.coroutines.delay

sealed interface RecallPhase {
    data class Idle(val message: String? = null, val needsMicSettings: Boolean = false) :
        RecallPhase

    data object Preparing : RecallPhase

    data class Recording(val startedAtMs: Long, val level: Float) : RecallPhase

    data object Transcribing : RecallPhase

    data class Celebrating(val result: Recall.Result) : RecallPhase

    data class Result(val result: Recall.Result) : RecallPhase
}

@Composable
fun RecallScreen(
    entryTitle: String,
    isReview: Boolean,
    speechAvailable: Boolean,
    phase: RecallPhase,
    snackbarHostState: SnackbarHostState,
    onClose: () -> Unit,
    onPreviewEntry: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onStartOver: () -> Unit,
    onTryAgain: () -> Unit,
    onFinishReview: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onCopyText: (String) -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val keepAwake =
        phase is RecallPhase.Preparing ||
            phase is RecallPhase.Recording ||
            phase is RecallPhase.Transcribing ||
            phase is RecallPhase.Celebrating

    DisposableEffect(keepAwake) {
        val window = (context as? Activity)?.window
        if (keepAwake) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(phase is RecallPhase.Celebrating) {
        if (phase is RecallPhase.Celebrating) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    BackHandler(onBack = onClose)

    AnimatedContent(
        targetState = phase is RecallPhase.Result,
        transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(180)) },
        label = "recallStage",
    ) { showingResult ->
        if (showingResult && phase is RecallPhase.Result) {
            RecallResultStage(
                entryTitle = entryTitle,
                isReview = isReview,
                result = phase.result,
                snackbarHostState = snackbarHostState,
                onClose = onClose,
                onTryAgain = onTryAgain,
                onFinishReview = onFinishReview,
                onCopyText = onCopyText,
            )
        } else {
            RecallAttemptStage(
                entryTitle = entryTitle,
                isReview = isReview,
                speechAvailable = speechAvailable,
                phase = phase,
                snackbarHostState = snackbarHostState,
                onClose = onClose,
                onPreviewEntry = onPreviewEntry,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                onStartOver = onStartOver,
                onOpenAppSettings = onOpenAppSettings,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecallAttemptStage(
    entryTitle: String,
    isReview: Boolean,
    speechAvailable: Boolean,
    phase: RecallPhase,
    snackbarHostState: SnackbarHostState,
    onClose: () -> Unit,
    onPreviewEntry: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onStartOver: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val colors = LocalVerbatimColors.current
    val haptic = LocalHapticFeedback.current

    val isRecording = phase is RecallPhase.Recording
    val isVerbatim = phase is RecallPhase.Celebrating
    val isBusy = phase is RecallPhase.Preparing || phase is RecallPhase.Transcribing || isVerbatim
    val isAttempting = phase !is RecallPhase.Idle

    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(phase is RecallPhase.Recording) {
        if (phase is RecallPhase.Recording) {
            while (true) {
                elapsedSeconds = ((System.currentTimeMillis() - phase.startedAtMs) / 1000L).coerceAtLeast(0L)
                delay(250L)
            }
        } else {
            elapsedSeconds = 0L
        }
    }

    val topSpacerWeight by
        animateFloatAsState(
            targetValue = if (isAttempting) 0.001f else 0.7f,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
            label = "recallTitlePosition",
        )

    val waveformActivity =
        when (phase) {
            is RecallPhase.Idle -> null
            RecallPhase.Preparing -> WaveformActivity.Preparing
            is RecallPhase.Recording -> WaveformActivity.Listening
            RecallPhase.Transcribing -> WaveformActivity.Transcribing
            is RecallPhase.Celebrating -> WaveformActivity.Succeeded
            is RecallPhase.Result -> null
        }

    val promptText =
        when {
            !speechAvailable && !isAttempting ->
                "Recitation requires on-device speech recognition, which isn’t available on this phone."
            phase is RecallPhase.Celebrating -> "Verbatim"
            phase is RecallPhase.Idle -> phase.message ?: "Tap Record to begin"
            phase is RecallPhase.Preparing -> "Getting ready…"
            phase is RecallPhase.Recording -> "Tap Stop when you’re done"
            phase is RecallPhase.Transcribing -> "Checking recitation…"
            else -> ""
        }

    Scaffold(
        containerColor = colors.readingSurface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close")
                    }
                },
                title = {
                    val contextLabel = if (isReview) "Review" else "Practice"
                    val timerText =
                        if (isRecording) {
                            val mins = elapsedSeconds / 60
                            val secs = elapsedSeconds % 60
                            " · $mins:%02d".format(secs)
                        } else {
                            ""
                        }
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = contextLabel + timerText,
                            style =
                                MaterialTheme.typography.labelLarge.copy(
                                    fontFamily = FontFamily.SansSerif,
                                    fontFeatureSettings = "tnum",
                                ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                actions = {
                    if (isRecording) {
                        IconButton(onClick = onStartOver) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Start over",
                            )
                        }
                    } else if (isReview && phase is RecallPhase.Idle) {
                        TextButton(onClick = onPreviewEntry) {
                            Icon(
                                imageVector = Icons.Rounded.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Preview")
                        }
                    } else {
                        Spacer(Modifier.width(48.dp))
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.readingSurface,
                        scrolledContainerColor = colors.readingSurface,
                    ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(innerPadding)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (topSpacerWeight > 0.01f) {
                Spacer(Modifier.weight(topSpacerWeight))
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = entryTitle,
                    style = VerbatimTypography.entryLargeTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = promptText,
                    style =
                        if (isVerbatim) {
                            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                    color =
                        if (isVerbatim) colors.verbatim
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (phase is RecallPhase.Idle && phase.needsMicSettings) {
                    Spacer(Modifier.height(14.dp))
                    FilledTonalButton(onClick = onOpenAppSettings) {
                        Text("Open settings")
                    }
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                VoiceWaveform(
                    activity = waveformActivity,
                    level = (phase as? RecallPhase.Recording)?.level ?: 0f,
                    modifier = Modifier.fillMaxWidth().height(210.dp),
                )
            }

            Box(
                modifier = Modifier.padding(bottom = 36.dp).height(84.dp),
                contentAlignment = Alignment.Center,
            ) {
                val showButton =
                    speechAvailable &&
                        phase !is RecallPhase.Transcribing &&
                        phase !is RecallPhase.Celebrating
                androidx.compose.animation.AnimatedVisibility(
                    visible = showButton,
                    enter = fadeIn(tween(220)),
                    exit = fadeOut(tween(180)),
                ) {
                    RecordStopButton(
                        isRecording = isRecording,
                        enabled = !isBusy,
                        onClick = {
                            if (phase is RecallPhase.Recording) {
                                // Guard against accidental double-tap cutting off a brand-new take.
                                if (System.currentTimeMillis() - phase.startedAtMs < 500L) return@RecordStopButton
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onStopRecording()
                            } else if (phase is RecallPhase.Idle) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onStartRecording()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordStopButton(
    isRecording: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalVerbatimColors.current
    val innerSize by
        animateDpAsState(
            targetValue = if (isRecording) 28.dp else 58.dp,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
            label = "recordInnerSize",
        )
    val innerCorner by
        animateDpAsState(
            targetValue = if (isRecording) 7.dp else 29.dp,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
            label = "recordInnerCorner",
        )

    val actionLabel = if (isRecording) "Stop recording" else "Start recording"

    Box(
        modifier =
            Modifier.size(76.dp)
                .clip(CircleShape)
                .border(
                    width = 3.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape,
                )
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .semantics { contentDescription = actionLabel },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier.size(innerSize)
                    .clip(RoundedCornerShape(innerCorner))
                    .background(colors.recite.copy(alpha = if (enabled) 1f else 0.4f))
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RecallResultStage(
    entryTitle: String,
    isReview: Boolean,
    result: Recall.Result,
    snackbarHostState: SnackbarHostState,
    onClose: () -> Unit,
    onTryAgain: () -> Unit,
    onFinishReview: () -> Unit,
    onCopyText: (String) -> Unit,
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
            label = "resultBarTitleAlpha",
        )

    var showExtraWords by remember(result) { mutableStateOf(true) }
    var showTranscript by remember(result) { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var inspectedUnclear by remember { mutableStateOf<Pair<String, String>?>(null) }

    Scaffold(
        containerColor = colors.readingSurface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = if (isReview) "Discard review" else "Close",
                        )
                    }
                },
                title = {
                    Text(
                        text = entryTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alpha(barTitleAlpha),
                    )
                },
                actions = {
                    IconButton(onClick = onTryAgain) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Try again",
                        )
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
                                text = {
                                    Text(if (showTranscript) "Show marked text" else "Show transcript")
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.GraphicEq, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    showTranscript = !showTranscript
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(if (showTranscript) "Copy transcript" else "Copy text")
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onCopyText(
                                        if (showTranscript) result.transcript else result.sourceText
                                    )
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
        bottomBar = {
            Surface(
                color = colors.readingSurface,
                tonalElevation = 0.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (isReview) {
                            Button(
                                onClick = onFinishReview,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Finish review", fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            FilledTonalButton(
                                onClick = onTryAgain,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Text("Try again")
                            }
                            Button(
                                onClick = onClose,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Text("Done")
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp)
                    .padding(top = 12.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = entryTitle,
                style = VerbatimTypography.entryLargeTitle,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            if (result.verbatim) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = colors.verbatim,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = "Verbatim",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.verbatim,
                        )
                    }
                    if (result.unclearCount > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${result.unclearCount} unclear · tap dotted words",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (result.missedCount > 0) {
                        Text(
                            text = "${result.missedCount} missed",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.missedWord,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                    if (result.unclearCount > 0) {
                        Text(
                            text = "${result.unclearCount} unclear",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                    if (result.extraCount > 0) {
                        ExtraWordsPill(
                            count = result.extraCount,
                            checked = showExtraWords,
                            enabled = !showTranscript,
                            onToggle = { showExtraWords = !showExtraWords },
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            if (showTranscript) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Transcript",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer {
                        Text(
                            text = result.transcript.ifBlank { "No words were heard." },
                            style = VerbatimTypography.entryBody,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else if (result.marks.isEmpty()) {
                Text(
                    text = "No words to compare.",
                    style = VerbatimTypography.entryBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                InlineRecallText(
                    result = result,
                    showExtraWords = showExtraWords,
                    onUnclearTap = { expected, heard -> inspectedUnclear = expected to heard },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    inspectedUnclear?.let { (expected, heard) ->
        AlertDialog(
            onDismissRequest = { inspectedUnclear = null },
            title = { Text("“$expected”") },
            text = {
                Text(
                    if (heard.isNotEmpty()) {
                        "Heard “$heard”, credited from an alternate recognition reading."
                    } else {
                        "Credited from an alternate recognition reading."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { inspectedUnclear = null }) { Text("OK") }
            },
        )
    }
}

/**
 * Tappable amber pill toggle matching iOS's ExtraWordsToggleStyle: solid amber when extra words are
 * shown inline, soft tonal pill with amber label when hidden.
 */
@Composable
private fun ExtraWordsPill(
    count: Int,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val colors = LocalVerbatimColors.current
    val bg =
        if (checked) colors.extraWord else MaterialTheme.colorScheme.surfaceVariant
    val fg =
        if (checked) colors.onExtraWord else colors.extraWord
    val label = "$count extra"

    Box(
        modifier =
            Modifier.defaultMinSize(minHeight = 32.dp)
                .clip(RoundedCornerShape(50))
                .background(bg)
                .alpha(if (enabled) 1f else 0.4f)
                .clickable(enabled = enabled, onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 5.dp)
                .semantics {
                    contentDescription =
                        if (checked) "$label, shown. Tap to hide extra words."
                        else "$label, hidden. Tap to show extra words."
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = fg,
        )
    }
}
