package app.verbatim

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.verbatim.ui.EntryDetailScreen
import app.verbatim.ui.EntryFormScreen
import app.verbatim.ui.FormDraft
import app.verbatim.ui.LibraryScreen
import app.verbatim.ui.LocalVerbatimColors
import app.verbatim.ui.RecallPhase
import app.verbatim.ui.RecallScreen
import app.verbatim.ui.SettingsScreen
import app.verbatim.ui.VerbatimTheme
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AppScreen {
    data object LoadError : AppScreen

    data object Library : AppScreen

    data class Detail(val index: Int) : AppScreen

    data class Form(
        val editingIndex: Int?,
        val heading: String,
        val initialDraft: FormDraft,
        val existingReview: Review?,
        val returnToDetailIndex: Int?,
    ) : AppScreen

    data object Settings : AppScreen
}

data class ActiveRecall(
    val entryIndex: Int,
    val isReview: Boolean,
    val phase: RecallPhase = RecallPhase.Idle(),
)

class VerbatimViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext: Context = application.applicationContext
    private val store = EntryStore(appContext)
    private val prefs = appContext.getSharedPreferences("verbatim_ui", Context.MODE_PRIVATE)
    val voice = Voice(appContext)

    var library by mutableStateOf(Library())
        private set

    var screen by mutableStateOf<AppScreen>(AppScreen.Library)
        private set

    var formDraft by mutableStateOf(FormDraft())
        private set

    var recall by mutableStateOf<ActiveRecall?>(null)
        private set

    var today by mutableStateOf(LocalDate.now())
        private set

    var bookOrder by mutableStateOf(prefs.getBoolean("bookOrder", false))
        private set

    var notificationsEnabled by mutableStateOf(checkNotificationsEnabled())
        private set

    var needsNotificationPermission by mutableStateOf(checkNeedsNotificationPermission())
        private set

    var pendingReplace by mutableStateOf<Library?>(null)
        private set

    var addEntriesResult by mutableStateOf<AddEntriesResult?>(null)
        private set

    var snackbarMessage by mutableStateOf<String?>(null)
        private set

    private val attemptCounter = AtomicInteger(0)
    private var celebrationJob: Job? = null
    private var smoothedLevel = 0f

    init {
        // Migrate bookOrder preference from legacy Activity preferences if needed.
        if (!prefs.contains("bookOrder")) {
            val legacy = appContext.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
            if (legacy.contains("bookOrder")) {
                val value = legacy.getBoolean("bookOrder", false)
                prefs.edit { putBoolean("bookOrder", value) }
                bookOrder = value
            }
        }
        try {
            library = store.read()
        } catch (_: Exception) {
            screen = AppScreen.LoadError
        }
    }

    fun consumeSnackbar() {
        snackbarMessage = null
    }

    fun showMessage(text: String) {
        snackbarMessage = text
    }

    fun onResume() {
        today = LocalDate.now()
        notificationsEnabled = checkNotificationsEnabled()
        needsNotificationPermission = checkNeedsNotificationPermission()
        if (screen != AppScreen.LoadError) {
            Reminder.refresh(appContext, library)
        }
    }

    fun onPause() {
        val current = recall ?: return
        if (current.phase is RecallPhase.Preparing || current.phase is RecallPhase.Recording) {
            voice.cancel()
            attemptCounter.incrementAndGet()
            smoothedLevel = 0f
            recall =
                current.copy(
                    phase = RecallPhase.Idle(message = "Recording stopped when you left the app.")
                )
        }
    }

    override fun onCleared() {
        voice.cancel()
        attemptCounter.incrementAndGet()
        celebrationJob?.cancel()
        super.onCleared()
    }

    fun updateBookOrder(enabled: Boolean) {
        bookOrder = enabled
        prefs.edit { putBoolean("bookOrder", enabled) }
    }

    fun openLibrary() {
        screen = AppScreen.Library
    }

    fun openDetail(index: Int) {
        if (index in library.entries.indices) {
            screen = AppScreen.Detail(index)
        }
    }

    fun openSettings() {
        notificationsEnabled = checkNotificationsEnabled()
        needsNotificationPermission = checkNeedsNotificationPermission()
        screen = AppScreen.Settings
    }

    fun openAddForm() {
        val draft = FormDraft(weekday = FormDraft.defaultWeekday(today))
        formDraft = draft
        screen =
            AppScreen.Form(
                editingIndex = null,
                heading = "Add entry",
                initialDraft = draft,
                existingReview = null,
                returnToDetailIndex = null,
            )
    }

    fun openEditForm(index: Int, returnToDetail: Boolean) {
        val entry = library.entries.getOrNull(index) ?: return
        val draft = FormDraft.fromEntry(entry, today)
        formDraft = draft
        screen =
            AppScreen.Form(
                editingIndex = index,
                heading = "Edit entry",
                initialDraft = draft,
                existingReview = entry.review,
                returnToDetailIndex = if (returnToDetail) index else null,
            )
    }

    fun openDuplicateForm(index: Int, returnToDetail: Boolean) {
        val entry = library.entries.getOrNull(index) ?: return
        val draft =
            FormDraft(
                title = library.duplicateTitle(entry.title),
                text = entry.text,
                inProgress = false,
                weekday = FormDraft.defaultWeekday(today),
            )
        formDraft = draft
        screen =
            AppScreen.Form(
                editingIndex = null,
                heading = "New entry",
                initialDraft = draft,
                existingReview = null,
                returnToDetailIndex = if (returnToDetail) index else null,
            )
    }

    fun updateFormDraft(next: FormDraft) {
        formDraft = next
    }

    fun cancelForm() {
        val form = screen as? AppScreen.Form ?: return
        val target = form.returnToDetailIndex
        screen =
            if (target != null && target in library.entries.indices) AppScreen.Detail(target)
            else AppScreen.Library
    }

    /** Returns true if the caller should prompt for first-time notification permission. */
    fun saveForm(): Boolean {
        val form = screen as? AppScreen.Form ?: return false
        val title = formDraft.title.trim()
        val body = Entry.normalizeLineEndings(formDraft.text)
        if (title.isBlank() || body.isBlank()) {
            showMessage("Enter a title and text.")
            return false
        }
        val editIndex = form.editingIndex
        val duplicate =
            library.entries.withIndex().any { (i, existing) ->
                i != editIndex &&
                    title.equals(existing.title.trim(), ignoreCase = true) &&
                    body.trim() == existing.text.trim()
            }
        if (duplicate) {
            showMessage("That entry already exists.")
            return false
        }
        val cadence = formDraft.cadenceValue
        val review =
            when {
                cadence < 0 -> null
                form.existingReview?.cadence == cadence -> form.existingReview
                else -> Review.starting(cadence, today)
            }
        val savedEntry = Entry(title = title, text = body, progress = formDraft.inProgress, review = review)
        val updated = library.entries.toMutableList()
        val position =
            if (editIndex == null) {
                updated.size.also { updated.add(savedEntry) }
            } else {
                editIndex.also { updated[it] = savedEntry }
            }
        if (!persist(library.copy(entries = updated))) return false

        screen =
            if (editIndex != null && form.returnToDetailIndex == null) {
                AppScreen.Library
            } else {
                AppScreen.Detail(position)
            }

        if (review != null && shouldAskFirstTimeNotificationPermission()) {
            prefs.edit { putBoolean("askedForReminders", true) }
            return true
        }
        return false
    }

    fun deleteEntry(index: Int) {
        if (index !in library.entries.indices) return
        val next = library.copy(entries = library.entries.filterIndexed { i, _ -> i != index })
        if (persist(next)) {
            screen = AppScreen.Library
            showMessage("Entry deleted.")
        }
    }

    fun updateReminderTime(hour: Int, minute: Int) {
        persist(library.copy(hour = hour, minute = minute))
    }

    fun startRecall(index: Int, isReview: Boolean) {
        if (index !in library.entries.indices) return
        voice.cancel()
        celebrationJob?.cancel()
        attemptCounter.incrementAndGet()
        smoothedLevel = 0f
        recall = ActiveRecall(entryIndex = index, isReview = isReview, phase = RecallPhase.Idle())
    }

    fun closeRecall() {
        voice.cancel()
        celebrationJob?.cancel()
        attemptCounter.incrementAndGet()
        smoothedLevel = 0f
        recall = null
    }

    fun previewFromRecall() {
        val current = recall ?: return
        val idx = current.entryIndex
        closeRecall()
        openDetail(idx)
    }

    fun startVoiceRecognition() {
        val current = recall ?: return
        val entry = library.entries.getOrNull(current.entryIndex) ?: return
        val attemptId = attemptCounter.incrementAndGet()
        celebrationJob?.cancel()
        smoothedLevel = 0f
        recall = current.copy(phase = RecallPhase.Preparing)

        voice.start(
            onReady = {
                val active = recall ?: return@start
                if (attemptCounter.get() != attemptId) return@start
                if (active.phase is RecallPhase.Recording) return@start
                recall =
                    active.copy(
                        phase =
                            RecallPhase.Recording(
                                startedAtMs = System.currentTimeMillis(),
                                level = 0f,
                            )
                    )
            },
            onLevel = { raw ->
                val active = recall ?: return@start
                if (attemptCounter.get() != attemptId) return@start
                val phase = active.phase as? RecallPhase.Recording ?: return@start
                smoothedLevel += (raw - smoothedLevel) * if (raw > smoothedLevel) 0.6f else 0.25f
                recall = active.copy(phase = phase.copy(level = smoothedLevel))
            },
            onEndOfSpeech = {
                val active = recall ?: return@start
                if (attemptCounter.get() != attemptId) return@start
                if (active.phase is RecallPhase.Recording || active.phase is RecallPhase.Preparing) {
                    recall = active.copy(phase = RecallPhase.Transcribing)
                }
            },
            success = { readings ->
                val active = recall ?: return@start
                if (attemptCounter.get() != attemptId) return@start
                recall = active.copy(phase = RecallPhase.Transcribing)
                gradeRecitation(attemptId, entry.text, readings)
            },
            fail = { errorMessage ->
                val active = recall ?: return@start
                if (attemptCounter.get() != attemptId) return@start
                smoothedLevel = 0f
                recall = active.copy(phase = RecallPhase.Idle(message = errorMessage))
            },
        )
    }

    fun onMicPermissionDenied() {
        val active = recall ?: return
        recall =
            active.copy(
                phase =
                    RecallPhase.Idle(
                        message = "To recite aloud, allow microphone access for Verbatim in Android Settings.",
                        needsMicSettings = true,
                    )
            )
    }

    fun stopVoiceRecognition() {
        val active = recall ?: return
        if (active.phase !is RecallPhase.Recording) return
        recall = active.copy(phase = RecallPhase.Transcribing)
        voice.stop()
    }

    fun startOverRecall() {
        val active = recall ?: return
        voice.cancel()
        celebrationJob?.cancel()
        attemptCounter.incrementAndGet()
        smoothedLevel = 0f
        recall = active.copy(phase = RecallPhase.Idle())
    }

    fun finishReview() {
        val active = recall ?: return
        val idx = active.entryIndex
        val entry = library.entries.getOrNull(idx)
        val review = entry?.review
        if (entry != null && review != null && review.due(today)) {
            val items = library.entries.toMutableList()
            items[idx] = entry.copy(review = Review.starting(review.cadence, today.plusDays(1)))
            if (!persist(library.copy(entries = items))) return
        }
        closeRecall()
        screen = AppScreen.Library
    }

    private fun gradeRecitation(attemptId: Int, expectedText: String, readings: List<String>) {
        celebrationJob?.cancel()
        celebrationJob =
            viewModelScope.launch {
                val result =
                    withContext(Dispatchers.Default) {
                        Recall.grade(expectedText, readings)
                    }
                val active = recall ?: return@launch
                if (attemptCounter.get() != attemptId) return@launch
                if (result.verbatim) {
                    recall = active.copy(phase = RecallPhase.Celebrating(result))
                    delay(1150L)
                    if (attemptCounter.get() != attemptId) return@launch
                    val stillActive = recall ?: return@launch
                    recall = stillActive.copy(phase = RecallPhase.Result(result))
                } else {
                    recall = active.copy(phase = RecallPhase.Result(result))
                }
            }
    }

    fun exportBackupTo(uri: Uri) {
        viewModelScope.launch {
            val ok =
                withContext(Dispatchers.IO) {
                    runCatching {
                        requireNotNull(appContext.contentResolver.openOutputStream(uri, "wt"))
                            .bufferedWriter()
                            .use { it.write(library.encode()) }
                    }
                        .isSuccess
                }
            showMessage(
                if (ok) "Backup saved."
                else "Couldn’t write this backup. Your library hasn’t been changed."
            )
        }
    }

    fun importBackupFrom(uri: Uri, addOnly: Boolean) {
        viewModelScope.launch {
            val decoded =
                withContext(Dispatchers.IO) {
                    runCatching {
                        requireNotNull(appContext.contentResolver.openInputStream(uri))
                            .bufferedReader()
                            .use { Library.decode(it.readText()) }
                    }
                        .getOrNull()
                }
            if (decoded == null) {
                showMessage("Couldn’t read this backup. Your library hasn’t been changed.")
                return@launch
            }
            if (addOnly) {
                val stats = library.addingWithStats(decoded)
                if (stats.added == 0 || persist(stats.library)) {
                    addEntriesResult = stats
                }
            } else {
                pendingReplace = decoded
            }
        }
    }

    fun confirmReplaceLibrary() {
        val incoming = pendingReplace ?: return
        pendingReplace = null
        if (persist(incoming)) {
            screen = AppScreen.Library
            showMessage("Library restored.")
        }
    }

    fun dismissReplaceLibrary() {
        pendingReplace = null
    }

    fun dismissAddEntriesResult() {
        addEntriesResult = null
    }

    private fun persist(next: Library): Boolean {
        try {
            store.write(next)
        } catch (_: Exception) {
            showMessage("Couldn’t save. Your library hasn’t been changed.")
            return false
        }
        library = next
        Reminder.refresh(appContext, library)
        return true
    }

    private fun checkNotificationsEnabled(): Boolean {
        val manager = appContext.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled()
    }

    private fun checkNeedsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED

    private fun shouldAskFirstTimeNotificationPermission(): Boolean =
        checkNeedsNotificationPermission() && !prefs.getBoolean("askedForReminders", false)
}

class MainActivity : ComponentActivity() {
    private val vm: VerbatimViewModel by viewModels()

    private val requestMicPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) vm.startVoiceRecognition() else vm.onMicPermissionDenied()
        }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            vm.onResume()
        }

    private val createBackupFile =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) vm.exportBackupTo(uri)
        }

    private val openAddBackupFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) vm.importBackupFrom(uri, addOnly = true)
        }

    private val openReplaceBackupFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) vm.importBackupFrom(uri, addOnly = false)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VerbatimTheme {
                VerbatimApp(
                    vm = vm,
                    onCopyText = ::copyToClipboard,
                    onReadClipboard = ::readClipboardText,
                    onShareText = ::shareText,
                    onRequestMicAndRecord = ::requestMicOrRecord,
                    onRequestNotifications = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            requestNotificationPermission.launch(
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                        }
                    },
                    onOpenNotificationSettings = ::openNotificationSettings,
                    onOpenAppSettings = ::openAppSettings,
                    onExportBackup = {
                        createBackupFile.launch("Verbatim-Android-${LocalDate.now()}.json")
                    },
                    onAddBackup = { openAddBackupFile.launch(arrayOf("*/*")) },
                    onReplaceBackup = { openReplaceBackupFile.launch(arrayOf("*/*")) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    override fun onPause() {
        super.onPause()
        vm.onPause()
    }

    private fun requestMicOrRecord() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            vm.startVoiceRecognition()
        } else {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Verbatim", text))
        vm.showMessage("Copied to clipboard.")
    }

    private fun readClipboardText(): String? {
        val clipboard = getSystemService(ClipboardManager::class.java)
        val copied =
            clipboard.primaryClip?.let { clip ->
                if (clip.itemCount > 0) clip.getItemAt(0).coerceToText(this)?.toString() else null
            }
        if (copied.isNullOrBlank()) {
            vm.showMessage("No text on the clipboard.")
            return null
        }
        return copied
    }

    private fun shareText(title: String, text: String) {
        val intent =
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, title)
                .putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(intent, title))
    }

    private fun openNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        )
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))
        )
    }
}

@Composable
private fun VerbatimApp(
    vm: VerbatimViewModel,
    onCopyText: (String) -> Unit,
    onReadClipboard: () -> String?,
    onShareText: (title: String, text: String) -> Unit,
    onRequestMicAndRecord: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onExportBackup: () -> Unit,
    onAddBackup: () -> Unit,
    onReplaceBackup: () -> Unit,
) {
    val libraryListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(vm.snackbarMessage) {
        val msg = vm.snackbarMessage ?: return@LaunchedEffect
        vm.consumeSnackbar()
        snackbarHostState.showSnackbar(msg)
    }

    // Handle system back cleanly for non-root screens when Recall overlay is not active.
    val currentScreen = vm.screen
    val activeRecall = vm.recall

    BackHandler(enabled = activeRecall == null && (currentScreen is AppScreen.Detail || currentScreen is AppScreen.Settings)) {
        vm.openLibrary()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
            label = "mainScreen",
        ) { target ->
            when (target) {
                AppScreen.LoadError -> {
                    LoadErrorScreen()
                }
                AppScreen.Library -> {
                    LibraryScreen(
                        entries = vm.library.entries,
                        today = vm.today,
                        bookOrder = vm.bookOrder,
                        listState = libraryListState,
                        snackbarHostState = snackbarHostState,
                        onAddEntry = vm::openAddForm,
                        onOpenSettings = vm::openSettings,
                        onOpenDetail = vm::openDetail,
                        onStartRecall = vm::startRecall,
                        onEditEntry = { vm.openEditForm(it, returnToDetail = false) },
                        onDuplicateEntry = { vm.openDuplicateForm(it, returnToDetail = false) },
                        onDeleteEntry = vm::deleteEntry,
                    )
                }
                is AppScreen.Detail -> {
                    val entry = vm.library.entries.getOrNull(target.index)
                    if (entry != null) {
                        EntryDetailScreen(
                            entry = entry,
                            today = vm.today,
                            snackbarHostState = snackbarHostState,
                            onBack = vm::openLibrary,
                            onPractice = { vm.startRecall(target.index, isReview = false) },
                            onEdit = { vm.openEditForm(target.index, returnToDetail = true) },
                            onCopy = { onCopyText(Entry.normalizeLineEndings(entry.text)) },
                            onDuplicate = { vm.openDuplicateForm(target.index, returnToDetail = true) },
                            onShare = { onShareText(entry.title, Entry.normalizeLineEndings(entry.text)) },
                            onDelete = { vm.deleteEntry(target.index) },
                        )
                    } else {
                        LaunchedEffect(Unit) { vm.openLibrary() }
                    }
                }
                is AppScreen.Form -> {
                    EntryFormScreen(
                        heading = target.heading,
                        draft = vm.formDraft,
                        initialDraft = target.initialDraft,
                        existingReview = target.existingReview,
                        isEditingExisting = target.editingIndex != null,
                        today = vm.today,
                        snackbarHostState = snackbarHostState,
                        onDraftChange = vm::updateFormDraft,
                        onReadClipboard = onReadClipboard,
                        onSave = {
                            if (vm.saveForm()) {
                                onRequestNotifications()
                            }
                        },
                        onCancel = vm::cancelForm,
                        onDelete = target.editingIndex?.let { idx -> { vm.deleteEntry(idx) } },
                    )
                }
                AppScreen.Settings -> {
                    SettingsScreen(
                        hour = vm.library.hour,
                        minute = vm.library.minute,
                        bookOrder = vm.bookOrder,
                        notificationsEnabled = vm.notificationsEnabled,
                        needsNotificationPermission = vm.needsNotificationPermission,
                        pendingReplace = vm.pendingReplace,
                        currentEntryCount = vm.library.entries.size,
                        addResult = vm.addEntriesResult,
                        snackbarHostState = snackbarHostState,
                        onBack = vm::openLibrary,
                        onTimeSelected = vm::updateReminderTime,
                        onRequestNotificationPermission = onRequestNotifications,
                        onOpenNotificationSettings = onOpenNotificationSettings,
                        onBookOrderChange = vm::updateBookOrder,
                        onExportBackup = onExportBackup,
                        onReplaceFromBackup = onReplaceBackup,
                        onAddFromBackup = onAddBackup,
                        onConfirmReplace = vm::confirmReplaceLibrary,
                        onDismissReplace = vm::dismissReplaceLibrary,
                        onDismissAddResult = vm::dismissAddEntriesResult,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = activeRecall != null,
            enter = fadeIn(tween(260)),
            exit = fadeOut(tween(200)),
        ) {
            val currentRecall = activeRecall
            val entry = currentRecall?.let { vm.library.entries.getOrNull(it.entryIndex) }
            if (currentRecall != null && entry != null) {
                RecallScreen(
                    entryTitle = entry.title,
                    isReview = currentRecall.isReview,
                    speechAvailable = vm.voice.isAvailable(),
                    phase = currentRecall.phase,
                    snackbarHostState = snackbarHostState,
                    onClose = vm::closeRecall,
                    onPreviewEntry = vm::previewFromRecall,
                    onStartRecording = onRequestMicAndRecord,
                    onStopRecording = vm::stopVoiceRecognition,
                    onStartOver = vm::startOverRecall,
                    onTryAgain = vm::startOverRecall,
                    onFinishReview = vm::finishReview,
                    onOpenAppSettings = onOpenAppSettings,
                    onCopyText = onCopyText,
                )
            }
        }
    }
}

@Composable
private fun LoadErrorScreen() {
    val colors = LocalVerbatimColors.current
    Scaffold(containerColor = colors.readingSurface) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Couldn’t open Verbatim",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Your library hasn’t been changed. Close the app and try again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
