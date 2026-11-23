package app.verbatim

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.verbatim.ui.CadenceMode
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
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AppScreen {
    data object LoadError : AppScreen

    data object Library : AppScreen

    data class Detail(val entryId: String) : AppScreen

    data class Form(
        val editingEntryId: String?,
        val heading: String,
        val initialDraft: FormDraft,
        val existingReview: Review?,
        val returnToDetailId: String?,
        val persistsDraft: Boolean,
    ) : AppScreen

    data object Settings : AppScreen
}

data class ActiveRecall(
    val entryId: String,
    val entryTitle: String,
    val isReview: Boolean,
    val phase: RecallPhase = RecallPhase.Idle(),
)

class VerbatimViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {
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

    /** Held separately from [recall] so 20 Hz RMS updates never recompose parent screens. */
    var voiceLevel by mutableFloatStateOf(0f)
        private set

    var speechAvailable by mutableStateOf(voice.isAvailable())
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

    private val _snackbarMessages = Channel<String>(Channel.BUFFERED)
    val snackbarMessages = _snackbarMessages.receiveAsFlow()

    private val attemptCounter = AtomicInteger(0)
    private var celebrationJob: Job? = null
    private var watchdogJob: Job? = null

    init {
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
            restoreScreenAfterProcessDeath()
        } catch (_: Exception) {
            screen = AppScreen.LoadError
        }
        startMidnightTicker()
    }

    private fun startMidnightTicker() {
        viewModelScope.launch {
            while (true) {
                val now = ZonedDateTime.now()
                val nextMidnight =
                    now.toLocalDate().plusDays(1).atStartOfDay(ZoneId.systemDefault())
                val waitMs =
                    (nextMidnight.toInstant().toEpochMilli() - now.toInstant().toEpochMilli())
                        .coerceAtLeast(1_000L) + 250L
                delay(waitMs)
                today = LocalDate.now()
                if (screen != AppScreen.LoadError) {
                    Reminder.schedule(appContext, library)
                }
            }
        }
    }

    private fun restoreScreenAfterProcessDeath() {
        when (savedState.get<String>(KEY_SCREEN)) {
            "settings" -> screen = AppScreen.Settings
            "form" -> {
                val saved = loadSavedDraft()
                if (saved != null) {
                    formDraft = saved
                    screen =
                        AppScreen.Form(
                            editingEntryId = null,
                            heading = "Add entry",
                            initialDraft = FormDraft(weekday = FormDraft.defaultWeekday(today)),
                            existingReview = null,
                            returnToDetailId = null,
                            persistsDraft = true,
                        )
                }
            }
            "detail" -> {
                val title = savedState.get<String>(KEY_DETAIL_TITLE)
                val match = title?.let { t -> library.entries.firstOrNull { it.title == t } }
                if (match != null) {
                    screen = AppScreen.Detail(match.id)
                }
            }
        }
    }

    private fun recordScreenState(next: AppScreen) {
        when (next) {
            AppScreen.Library -> {
                savedState[KEY_SCREEN] = "library"
                savedState[KEY_DETAIL_TITLE] = null
            }
            AppScreen.Settings -> {
                savedState[KEY_SCREEN] = "settings"
                savedState[KEY_DETAIL_TITLE] = null
            }
            is AppScreen.Detail -> {
                savedState[KEY_SCREEN] = "detail"
                savedState[KEY_DETAIL_TITLE] = entryById(next.entryId)?.title
            }
            is AppScreen.Form -> {
                savedState[KEY_SCREEN] = if (next.persistsDraft) "form" else "library"
            }
            AppScreen.LoadError -> savedState[KEY_SCREEN] = "error"
        }
    }

    fun entryById(id: String): Entry? = library.entries.firstOrNull { it.id == id }

    fun showMessage(text: String) {
        _snackbarMessages.trySend(text)
    }

    fun onResume() {
        today = LocalDate.now()
        speechAvailable = voice.isAvailable()
        notificationsEnabled = checkNotificationsEnabled()
        needsNotificationPermission = checkNeedsNotificationPermission()
        if (screen != AppScreen.LoadError) {
            Reminder.schedule(appContext, library)
        }
    }

    fun onBackgrounded() {
        val current = recall ?: return
        if (current.phase is RecallPhase.Preparing || current.phase is RecallPhase.Recording) {
            voice.cancel()
            watchdogJob?.cancel()
            attemptCounter.incrementAndGet()
            voiceLevel = 0f
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
        watchdogJob?.cancel()
        super.onCleared()
    }

    fun updateBookOrder(enabled: Boolean) {
        bookOrder = enabled
        prefs.edit { putBoolean("bookOrder", enabled) }
    }

    fun hasAskedNotificationPermission(): Boolean =
        prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)

    fun markAskedNotificationPermission() {
        prefs.edit { putBoolean(KEY_ASKED_NOTIFICATIONS, true) }
    }

    fun hasAskedMicPermission(): Boolean = prefs.getBoolean(KEY_ASKED_MIC, false)

    fun markAskedMicPermission() {
        prefs.edit { putBoolean(KEY_ASKED_MIC, true) }
    }

    fun openLibrary() {
        pendingReplace = null
        addEntriesResult = null
        screen = AppScreen.Library
        recordScreenState(AppScreen.Library)
    }

    fun openDetail(entryId: String) {
        if (entryById(entryId) != null) {
            val next = AppScreen.Detail(entryId)
            screen = next
            recordScreenState(next)
        }
    }

    fun openSettings() {
        notificationsEnabled = checkNotificationsEnabled()
        needsNotificationPermission = checkNeedsNotificationPermission()
        screen = AppScreen.Settings
        recordScreenState(AppScreen.Settings)
    }

    fun openAddForm() {
        val blank = FormDraft(weekday = FormDraft.defaultWeekday(today))
        val restored = loadSavedDraft()
        formDraft = restored ?: blank
        val next =
            AppScreen.Form(
                editingEntryId = null,
                heading = "Add entry",
                initialDraft = blank,
                existingReview = null,
                returnToDetailId = null,
                persistsDraft = true,
            )
        screen = next
        recordScreenState(next)
    }

    fun openEditForm(entryId: String, returnToDetail: Boolean) {
        val entry = entryById(entryId) ?: return
        val draft = FormDraft.fromEntry(entry, today)
        formDraft = draft
        val next =
            AppScreen.Form(
                editingEntryId = entry.id,
                heading = "Edit entry",
                initialDraft = draft,
                existingReview = entry.review,
                returnToDetailId = if (returnToDetail) entry.id else null,
                persistsDraft = false,
            )
        screen = next
        recordScreenState(next)
    }

    fun openDuplicateForm(entryId: String, returnToDetail: Boolean) {
        val entry = entryById(entryId) ?: return
        val draft =
            FormDraft(
                title = library.duplicateTitle(entry.title),
                text = entry.text,
                inProgress = false,
                weekday = FormDraft.defaultWeekday(today),
            )
        formDraft = draft
        val next =
            AppScreen.Form(
                editingEntryId = null,
                heading = "New entry",
                initialDraft = draft,
                existingReview = null,
                returnToDetailId = if (returnToDetail) entry.id else null,
                persistsDraft = false,
            )
        screen = next
        recordScreenState(next)
    }

    fun updateFormDraft(next: FormDraft) {
        formDraft = next
        val form = screen as? AppScreen.Form
        if (form?.persistsDraft == true) {
            saveDraft(next)
        }
    }

    fun cancelForm() {
        val form = screen as? AppScreen.Form ?: return
        if (form.persistsDraft) clearSavedDraft()
        val targetId = form.returnToDetailId
        if (targetId != null && entryById(targetId) != null) {
            openDetail(targetId)
        } else {
            openLibrary()
        }
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
        val titleKey = Entry.normalizedTitleKey(title)
        val bodyKey = Entry.normalizedBodyKey(body)
        val duplicate =
            library.entries.any { existing ->
                existing.id != form.editingEntryId &&
                    Entry.normalizedTitleKey(existing.title) == titleKey &&
                    Entry.normalizedBodyKey(existing.text) == bodyKey
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
        val updated = library.entries.toMutableList()
        val savedEntry =
            if (form.editingEntryId == null) {
                Entry(title = title, text = body, progress = formDraft.inProgress, review = review)
                    .also { updated.add(it) }
            } else {
                val idx = updated.indexOfFirst { it.id == form.editingEntryId }
                if (idx < 0) return false
                updated[idx]
                    .copy(
                        title = title,
                        text = body,
                        progress = formDraft.inProgress,
                        review = review,
                    )
                    .also { updated[idx] = it }
            }
        if (!persist(library.copy(entries = updated))) return false
        if (form.persistsDraft) clearSavedDraft()

        if (form.editingEntryId != null && form.returnToDetailId == null) {
            openLibrary()
        } else {
            openDetail(savedEntry.id)
        }

        if (review != null && needsNotificationPermission && !hasAskedNotificationPermission()) {
            markAskedNotificationPermission()
            return true
        }
        return false
    }

    fun deleteEntry(entryId: String) {
        if (entryById(entryId) == null) return
        val next = library.copy(entries = library.entries.filterNot { it.id == entryId })
        if (persist(next)) {
            if (recall?.entryId == entryId) closeRecall()
            openLibrary()
            showMessage("Entry deleted.")
        }
    }

    fun updateReminderTime(hour: Int, minute: Int) {
        persist(library.copy(hour = hour, minute = minute))
    }

    /** Matches iOS RecallRequest: whenever an entry is due today, reciting it counts as a Review. */
    fun startRecall(entryId: String) {
        val entry = entryById(entryId) ?: return
        voice.cancel()
        celebrationJob?.cancel()
        watchdogJob?.cancel()
        attemptCounter.incrementAndGet()
        voiceLevel = 0f
        recall =
            ActiveRecall(
                entryId = entry.id,
                entryTitle = entry.title,
                isReview = entry.review?.due(today) == true,
                phase = RecallPhase.Idle(),
            )
    }

    fun closeRecall() {
        voice.cancel()
        celebrationJob?.cancel()
        watchdogJob?.cancel()
        attemptCounter.incrementAndGet()
        voiceLevel = 0f
        recall = null
    }

    fun previewFromRecall() {
        val current = recall ?: return
        val id = current.entryId
        closeRecall()
        openDetail(id)
    }

    fun startVoiceRecognition() {
        val current = recall ?: return
        val entry = entryById(current.entryId) ?: return
        val attemptId = attemptCounter.incrementAndGet()
        celebrationJob?.cancel()
        voiceLevel = 0f
        recall = current.copy(phase = RecallPhase.Preparing)
        armWatchdog(attemptId, "Speech recognition took too long to start. Try again.")

        voice.start(
            onReady = {
                val active = recall ?: return@start
                if (attemptCounter.get() != attemptId) return@start
                watchdogJob?.cancel()
                if (active.phase is RecallPhase.Recording) return@start
                recall =
                    active.copy(
                        phase = RecallPhase.Recording(startedAtElapsedMs = SystemClock.elapsedRealtime())
                    )
            },
            onLevel = { raw ->
                if (attemptCounter.get() != attemptId) return@start
                voiceLevel = raw
            },
            success = { readings ->
                val active = recall ?: return@start
                if (attemptCounter.get() != attemptId) return@start
                watchdogJob?.cancel()
                voiceLevel = 0f
                recall = active.copy(phase = RecallPhase.Transcribing)
                gradeRecitation(attemptId, entry.text, readings)
            },
            fail = { errorMessage ->
                val active = recall ?: return@start
                if (attemptCounter.get() != attemptId) return@start
                watchdogJob?.cancel()
                voiceLevel = 0f
                recall = active.copy(phase = RecallPhase.Idle(message = errorMessage))
            },
        )
    }

    fun onMicPermissionDenied(permanentlyDenied: Boolean) {
        val active = recall ?: return
        recall =
            active.copy(
                phase =
                    RecallPhase.Idle(
                        message =
                            if (permanentlyDenied) {
                                "To recite aloud, allow microphone access for Verbatim in Android Settings."
                            } else {
                                "Microphone access is required to recite aloud. Tap Record to try again."
                            },
                        needsMicSettings = permanentlyDenied,
                    )
            )
    }

    fun stopVoiceRecognition() {
        val active = recall ?: return
        if (active.phase !is RecallPhase.Recording) return
        val attemptId = attemptCounter.get()
        voiceLevel = 0f
        recall = active.copy(phase = RecallPhase.Transcribing)
        armWatchdog(attemptId, "Speech recognition timed out. Try again.")
        voice.stop()
    }

    fun startOverRecall() {
        val active = recall ?: return
        voice.cancel()
        celebrationJob?.cancel()
        watchdogJob?.cancel()
        attemptCounter.incrementAndGet()
        voiceLevel = 0f
        recall = active.copy(phase = RecallPhase.Idle())
    }

    private fun armWatchdog(attemptId: Int, timeoutMessage: String) {
        watchdogJob?.cancel()
        watchdogJob =
            viewModelScope.launch {
                delay(12_000L)
                if (attemptCounter.get() != attemptId) return@launch
                val active = recall ?: return@launch
                if (active.phase is RecallPhase.Preparing || active.phase is RecallPhase.Transcribing) {
                    voice.cancel()
                    attemptCounter.incrementAndGet()
                    voiceLevel = 0f
                    recall = active.copy(phase = RecallPhase.Idle(message = timeoutMessage))
                }
            }
    }

    fun finishReview() {
        val active = recall ?: return
        val idx = library.entries.indexOfFirst { it.id == active.entryId }
        val entry = library.entries.getOrNull(idx)
        val review = entry?.review
        if (entry != null && review != null && review.due(today)) {
            val items = library.entries.toMutableList()
            items[idx] = entry.copy(review = Review.starting(review.cadence, today.plusDays(1)))
            if (!persist(library.copy(entries = items))) return
        }
        closeRecall()
        openLibrary()
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
                    writeBytes(uri, library.encode().toByteArray(Charsets.UTF_8))
                }
            showMessage(
                if (ok) "Backup saved."
                else "Couldn’t write this backup. Your library hasn’t been changed."
            )
        }
    }

    fun exportRawStoreTo(uri: Uri) {
        viewModelScope.launch {
            val ok =
                withContext(Dispatchers.IO) {
                    val bytes = store.readRawBytes() ?: return@withContext false
                    writeBytes(uri, bytes)
                }
            showMessage(
                if (ok) "Unreadable file exported."
                else "Couldn’t export the unreadable library file."
            )
        }
    }

    private fun writeBytes(uri: Uri, bytes: ByteArray): Boolean =
        runCatching {
                val stream =
                    runCatching { appContext.contentResolver.openOutputStream(uri, "wt") }
                        .getOrNull()
                        ?: appContext.contentResolver.openOutputStream(uri, "w")
                requireNotNull(stream).use { it.write(bytes) }
            }
            .isSuccess

    fun importBackupFrom(uri: Uri, addOnly: Boolean) {
        if (screen == AppScreen.LoadError) return
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
            if (screen != AppScreen.Settings) return@launch
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
            openLibrary()
            showMessage("Library restored.")
        }
    }

    fun dismissReplaceLibrary() {
        pendingReplace = null
    }

    fun dismissAddEntriesResult() {
        addEntriesResult = null
    }

    fun formatTranscriptionLog(title: String, result: Recall.Result): String {
        val pkgInfo =
            runCatching { appContext.packageManager.getPackageInfo(appContext.packageName, 0) }
                .getOrNull()
        val version = pkgInfo?.versionName ?: "?"
        val header = "Verbatim $version · Android ${Build.VERSION.RELEASE} · ${Build.MODEL}"
        return result.transcriptionLog(title = title, header = header)
    }

    private fun persist(next: Library): Boolean {
        if (screen == AppScreen.LoadError) return false
        try {
            store.write(next)
        } catch (_: Exception) {
            showMessage("Couldn’t save. Your library hasn’t been changed.")
            return false
        }
        library = next
        Reminder.schedule(appContext, library)
        return true
    }

    private fun saveDraft(draft: FormDraft) {
        if (draft.isBlank) {
            clearSavedDraft()
            return
        }
        prefs.edit {
            putBoolean(KEY_HAS_DRAFT, true)
            putString(KEY_DRAFT_TITLE, draft.title)
            putString(KEY_DRAFT_TEXT, draft.text)
            putBoolean(KEY_DRAFT_PROGRESS, draft.inProgress)
            putString(KEY_DRAFT_CADENCE, draft.cadenceMode.name)
            putInt(KEY_DRAFT_WEEKDAY, draft.weekday)
        }
    }

    private fun loadSavedDraft(): FormDraft? {
        if (!prefs.getBoolean(KEY_HAS_DRAFT, false)) return null
        val modeName = prefs.getString(KEY_DRAFT_CADENCE, CadenceMode.None.name)
        val mode =
            CadenceMode.entries.firstOrNull { it.name == modeName } ?: CadenceMode.None
        return FormDraft(
            title = prefs.getString(KEY_DRAFT_TITLE, "").orEmpty(),
            text = prefs.getString(KEY_DRAFT_TEXT, "").orEmpty(),
            inProgress = prefs.getBoolean(KEY_DRAFT_PROGRESS, false),
            cadenceMode = mode,
            weekday = prefs.getInt(KEY_DRAFT_WEEKDAY, FormDraft.defaultWeekday(today)).coerceIn(1, 7),
        )
    }

    private fun clearSavedDraft() {
        prefs.edit {
            remove(KEY_HAS_DRAFT)
            remove(KEY_DRAFT_TITLE)
            remove(KEY_DRAFT_TEXT)
            remove(KEY_DRAFT_PROGRESS)
            remove(KEY_DRAFT_CADENCE)
            remove(KEY_DRAFT_WEEKDAY)
        }
    }

    private fun checkNotificationsEnabled(): Boolean {
        val manager = appContext.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled()
    }

    private fun checkNeedsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED

    private companion object {
        const val KEY_SCREEN = "screen"
        const val KEY_DETAIL_TITLE = "detail_title"
        const val KEY_ASKED_NOTIFICATIONS = "askedForReminders"
        const val KEY_ASKED_MIC = "askedForMic"
        const val KEY_HAS_DRAFT = "has_add_draft"
        const val KEY_DRAFT_TITLE = "draft_title"
        const val KEY_DRAFT_TEXT = "draft_text"
        const val KEY_DRAFT_PROGRESS = "draft_progress"
        const val KEY_DRAFT_CADENCE = "draft_cadence"
        const val KEY_DRAFT_WEEKDAY = "draft_weekday"
    }
}

class MainActivity : ComponentActivity() {
    private val vm: VerbatimViewModel by viewModels()

    private val requestMicPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                vm.startVoiceRecognition()
            } else {
                val permanentlyDenied =
                    vm.hasAskedMicPermission() &&
                        !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
                vm.onMicPermissionDenied(permanentlyDenied)
            }
        }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            vm.onResume()
        }

    private val createBackupFile =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) vm.exportBackupTo(uri)
        }

    private val createRawStoreExport =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) vm.exportRawStoreTo(uri)
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
                    onRequestNotifications = ::requestNotificationsOrOpenSettings,
                    onOpenNotificationSettings = ::openNotificationSettings,
                    onOpenAppSettings = ::openAppSettings,
                    onExportBackup = {
                        launchSafely {
                            createBackupFile.launch("Verbatim-Android-${LocalDate.now()}.json")
                        }
                    },
                    onExportRawStore = {
                        launchSafely {
                            createRawStoreExport.launch("Verbatim-Unreadable-${LocalDate.now()}.json")
                        }
                    },
                    onAddBackup = { launchSafely { openAddBackupFile.launch(arrayOf("*/*")) } },
                    onReplaceBackup = {
                        launchSafely { openReplaceBackupFile.launch(arrayOf("*/*")) }
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            vm.onBackgrounded()
        }
    }

    private fun launchSafely(action: () -> Unit) {
        try {
            action()
        } catch (_: ActivityNotFoundException) {
            vm.showMessage("No file manager is available on this device.")
        }
    }

    private fun requestMicOrRecord() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            vm.startVoiceRecognition()
        } else {
            vm.markAskedMicPermission()
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun requestNotificationsOrOpenSettings() {
        if (Build.VERSION.SDK_INT < 33) {
            openNotificationSettings()
            return
        }
        val permanentlyDenied =
            vm.hasAskedNotificationPermission() &&
                !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        if (permanentlyDenied) {
            openNotificationSettings()
        } else {
            vm.markAskedNotificationPermission()
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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
        launchSafely { startActivity(Intent.createChooser(intent, title)) }
    }

    private fun openNotificationSettings() {
        launchSafely {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        }
    }

    private fun openAppSettings() {
        launchSafely {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", packageName, null))
            )
        }
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
    onExportRawStore: () -> Unit,
    onAddBackup: () -> Unit,
    onReplaceBackup: () -> Unit,
) {
    val libraryListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.snackbarMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val currentScreen = vm.screen
    val activeRecall = vm.recall
    var lastRecall by remember { mutableStateOf<ActiveRecall?>(null) }
    if (activeRecall != null) {
        lastRecall = activeRecall
    }

    BackHandler(
        enabled =
            activeRecall == null &&
                (currentScreen is AppScreen.Detail || currentScreen is AppScreen.Settings)
    ) {
        vm.openLibrary()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val backgroundModifier =
            if (activeRecall != null) {
                Modifier.fillMaxSize().clearAndSetSemantics {}
            } else {
                Modifier.fillMaxSize()
            }

        Box(modifier = backgroundModifier) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                label = "mainScreen",
            ) { target ->
                when (target) {
                    AppScreen.LoadError -> {
                        LoadErrorScreen(
                            snackbarHostState = snackbarHostState,
                            onExportRawFile = onExportRawStore,
                        )
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
                        val entry = vm.entryById(target.entryId)
                        if (entry != null) {
                            EntryDetailScreen(
                                entry = entry,
                                today = vm.today,
                                snackbarHostState = snackbarHostState,
                                onBack = vm::openLibrary,
                                onPractice = { vm.startRecall(entry.id) },
                                onEdit = { vm.openEditForm(entry.id, returnToDetail = true) },
                                onCopy = { onCopyText(Entry.normalizeLineEndings(entry.text)) },
                                onDuplicate = {
                                    vm.openDuplicateForm(entry.id, returnToDetail = true)
                                },
                                onShare = {
                                    onShareText(entry.title, Entry.normalizeLineEndings(entry.text))
                                },
                                onDelete = { vm.deleteEntry(entry.id) },
                            )
                        } else {
                            LaunchedEffect(target.entryId) { vm.openLibrary() }
                        }
                    }
                    is AppScreen.Form -> {
                        EntryFormScreen(
                            heading = target.heading,
                            draft = vm.formDraft,
                            initialDraft = target.initialDraft,
                            existingReview = target.existingReview,
                            isEditingExisting = target.editingEntryId != null,
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
                            onDelete = target.editingEntryId?.let { id -> { vm.deleteEntry(id) } },
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
        }

        AnimatedVisibility(
            visible = activeRecall != null,
            enter = fadeIn(tween(260)),
            exit = fadeOut(tween(200)),
        ) {
            val shown = activeRecall ?: lastRecall
            if (shown != null) {
                RecallScreen(
                    entryTitle = shown.entryTitle,
                    isReview = shown.isReview,
                    speechAvailable = vm.speechAvailable,
                    phase = shown.phase,
                    levelProvider = { vm.voiceLevel },
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
                    onCopyTranscriptionLog = { result ->
                        onCopyText(vm.formatTranscriptionLog(shown.entryTitle, result))
                    },
                )
            }
        }
    }
}

@Composable
private fun LoadErrorScreen(
    snackbarHostState: SnackbarHostState,
    onExportRawFile: () -> Unit,
) {
    val colors = LocalVerbatimColors.current
    Scaffold(
        containerColor = colors.readingSurface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
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
                text =
                    "Your library file could not be read and hasn’t been changed. You can export the raw file before reinstalling or clearing app data.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(onClick = onExportRawFile) {
                Text("Export unreadable file")
            }
        }
    }
}
