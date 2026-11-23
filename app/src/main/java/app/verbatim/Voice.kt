package app.verbatim

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class Voice(private val context: Context) {
    private var active: SpeechRecognizer? = null
    private var stopRequested = false
    private val collectedSegments = mutableListOf<List<String>>()
    private var currentCallbacks: Callbacks? = null

    private data class Callbacks(
        val onReady: () -> Unit,
        val onLevel: (Float) -> Unit,
        val success: (List<String>) -> Unit,
        val fail: (String) -> Unit,
    )

    fun isAvailable() = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun cancel() {
        val r = active
        active = null
        currentCallbacks = null
        stopRequested = false
        collectedSegments.clear()
        r?.destroy()
    }

    fun stop() {
        stopRequested = true
        val r = active
        if (r != null) {
            r.stopListening()
        } else {
            finishOrFail("No words were heard. Try again.")
        }
    }

    fun start(
        onReady: () -> Unit = {},
        onLevel: (Float) -> Unit = {},
        success: (List<String>) -> Unit,
        fail: (String) -> Unit,
    ) {
        cancel()
        currentCallbacks = Callbacks(onReady, onLevel, success, fail)
        stopRequested = false
        collectedSegments.clear()
        beginListening()
    }

    private fun buildIntent(): Intent {
        val intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10_000L)
                .putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    6_000L,
                )
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 60_000L)
        if (Build.VERSION.SDK_INT >= 33) {
            intent.putExtra(
                RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
            )
        }
        return intent
    }

    private fun beginListening() {
        val callbacks = currentCallbacks ?: return
        try {
            val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            active = r
            r.setRecognitionListener(
                object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        if (active !== r) return
                        callbacks.onReady()
                    }

                    override fun onBeginningOfSpeech() {
                        if (active !== r) return
                        callbacks.onReady()
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        if (active !== r) return
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        callbacks.onLevel(normalized)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        // Do not end the take here; wait for explicit Stop or final segmented end.
                    }

                    override fun onSegmentResults(segmentResults: Bundle) {
                        if (active !== r) return
                        val readings =
                            segmentResults
                                .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                .orEmpty()
                                .filter { it.isNotBlank() }
                        if (readings.isNotEmpty()) {
                            collectedSegments += readings
                        }
                    }

                    override fun onEndOfSegmentedSession() {
                        if (active !== r) return
                        if (stopRequested) {
                            finishOrFail("No words were heard. Try again.")
                        } else {
                            // Keep listening seamlessly across natural pauses until the user taps Stop.
                            restartListening(r)
                        }
                    }

                    override fun onResults(results: Bundle) {
                        if (active !== r) return
                        val readings =
                            results
                                .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                .orEmpty()
                                .filter { it.isNotBlank() }
                        if (readings.isNotEmpty()) {
                            collectedSegments += readings
                        }
                        if (stopRequested) {
                            finishOrFail("No words were heard. Try again.")
                        } else {
                            // Non-segmented recognizer endpointed on a pause; keep listening until Stop.
                            restartListening(r)
                        }
                    }

                    override fun onError(error: Int) {
                        if (active !== r) return
                        val isSilenceError =
                            error == SpeechRecognizer.ERROR_NO_MATCH ||
                                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                        if (isSilenceError && !stopRequested) {
                            restartListening(r)
                            return
                        }
                        if (isSilenceError && stopRequested && collectedSegments.isNotEmpty()) {
                            finishOrFail("No words were heard. Try again.")
                            return
                        }
                        val message =
                            when (error) {
                                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                                    "Offline recognition for ${Locale.getDefault().displayName} isn’t ready. Check your phone’s speech settings for a language download."
                                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
                                    "This phone’s on-device recognizer doesn’t support ${Locale.getDefault().displayName}."
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                    "Microphone access is required for voice recall."
                                SpeechRecognizer.ERROR_NO_MATCH,
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                    "No words were heard. Try again."
                                else ->
                                    "The device could not finish recognition (error $error). Try again."
                            }
                        failAndClear(message)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                }
            )
            r.startListening(buildIntent())
        } catch (_: RuntimeException) {
            failAndClear(
                "Could not start on-device recognition. Check microphone access and try again."
            )
        }
    }

    private fun restartListening(previous: SpeechRecognizer) {
        if (active !== previous) return
        active = null
        previous.destroy()
        beginListening()
    }

    private fun finishOrFail(emptyMessage: String) {
        val combined = combineSegments(collectedSegments)
        val callbacks = currentCallbacks
        val r = active
        active = null
        currentCallbacks = null
        stopRequested = false
        collectedSegments.clear()
        r?.destroy()
        if (combined.isEmpty()) callbacks?.fail?.invoke(emptyMessage)
        else callbacks?.success?.invoke(combined)
    }

    private fun failAndClear(message: String) {
        val callbacks = currentCallbacks
        val r = active
        active = null
        currentCallbacks = null
        stopRequested = false
        collectedSegments.clear()
        r?.destroy()
        callbacks?.fail?.invoke(message)
    }

    companion object {
        /**
         * Joins multi-segment speech recognition results into a primary transcript plus alternate
         * readings that preserve single-segment alternatives for the grader's unclear-word credit.
         */
        fun combineSegments(segments: List<List<String>>): List<String> {
            val nonEmpty = segments.map { list -> list.filter { it.isNotBlank() } }.filter { it.isNotEmpty() }
            if (nonEmpty.isEmpty()) return emptyList()
            val primaryParts = nonEmpty.map { it.first().trim() }
            val primary = primaryParts.joinToString(" ")
            val result = mutableListOf(primary)
            val seen = mutableSetOf(primary)
            for (segIdx in nonEmpty.indices) {
                for (alt in nonEmpty[segIdx].drop(1)) {
                    val candidateParts = primaryParts.toMutableList()
                    candidateParts[segIdx] = alt.trim()
                    val candidate = candidateParts.joinToString(" ")
                    if (seen.add(candidate)) {
                        result += candidate
                    }
                }
            }
            return result
        }
    }
}
