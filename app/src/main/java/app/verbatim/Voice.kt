package app.verbatim

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class Voice(private val context: Context) {
    private var active: SpeechRecognizer? = null

    fun isAvailable() = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun cancel() {
        val r = active
        active = null
        r?.destroy()
    }

    fun stop() {
        active?.stopListening()
    }

    fun start(
        onReady: () -> Unit = {},
        onLevel: (Float) -> Unit = {},
        onEndOfSpeech: () -> Unit = {},
        success: (List<String>) -> Unit,
        fail: (String) -> Unit,
    ) {
        cancel()
        try {
            val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            active = r
            r.setRecognitionListener(
                object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        if (active !== r) return
                        onReady()
                    }

                    override fun onBeginningOfSpeech() {
                        if (active !== r) return
                        onReady()
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        if (active !== r) return
                        // Android typically reports rmsdB in roughly -2..10 dB.
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        onLevel(normalized)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        if (active !== r) return
                        onEndOfSpeech()
                    }

                    override fun onResults(results: Bundle) {
                        if (active !== r) return
                        val readings =
                            results
                                .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                .orEmpty()
                                .filter { it.isNotBlank() }
                        cancel()
                        if (readings.isEmpty()) fail("No words were heard. Try again.")
                        else success(readings)
                    }

                    override fun onError(error: Int) {
                        if (active !== r) return
                        cancel()
                        fail(
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
                        )
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                }
            )
            r.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                    )
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            )
        } catch (e: RuntimeException) {
            cancel()
            fail("Could not start on-device recognition. Check microphone access and try again.")
        }
    }
}
