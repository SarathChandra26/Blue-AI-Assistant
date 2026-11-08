package com.example.jarvis.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Wraps Android's [SpeechRecognizer] API.
 *
 * ## On-device vs. cloud recognition
 * Android's SpeechRecognizer delegates to the default recognition service installed on
 * the device. On a stock iQOO 12 (Google services present), this is Google Speech Services.
 *
 * - Google Speech Services CAN operate on-device for some languages (English is supported
 *   from Android 13+ with the on-device model downloaded).
 * - If the on-device model is not present, it falls back to Google's cloud servers.
 * - iQOO 12 ships with Google Play Services and Google Speech Recognition pre-installed.
 * - To check: Settings → Apps → Google → App details → Speech Recognition.
 *
 * Jarvis does NOT force cloud mode. It requests on-device via
 * [RecognizerIntent.EXTRA_PREFER_OFFLINE]. If unavailable, it transparently falls back
 * to network recognition — which will use internet. This is reported in the UI.
 *
 * ## Must run on main thread
 * [SpeechRecognizer] must be created and called on the main thread (Android requirement).
 */
class SpeechRecognitionManager(private val context: Context) {

    companion object { private const val TAG = "SpeechRec" }

    sealed class State {
        object Idle       : State()
        object Listening  : State()
        object Processing : State()
        data class Result(val text: String) : State()
        data class Error(val message: String) : State()
        object PermissionDenied : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private var recognizer: SpeechRecognizer? = null
    private var onResult: ((String) -> Unit)? = null

    /** Check whether on-device recognition is available (avoids internet dependency). */
    fun isOnDeviceAvailable(): Boolean =
        SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Start a single recognition session.
     * [callback] receives the best-match text when recognition succeeds.
     * Must be called from the **main thread**.
     */
    fun startListening(callback: (String) -> Unit) {
        if (!isAvailable()) {
            _state.value = State.Error(
                "Speech recognition is not available on this device. " +
                "Install Google app or another recognition service."
            )
            return
        }

        onResult = callback
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { sr ->
            sr.setRecognitionListener(listener)
        }

        val prefs = com.example.jarvis.settings.JarvisPreferences(context)
        val langPref = prefs.assistantLanguage

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                     RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, isOnDeviceAvailable())

            when (langPref) {
                "te-IN" -> {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "te-IN")
                    putExtra("android.speech.extra.EXTRA_LANGUAGE_PREFERENCE", "te-IN")
                }
                "en-US" -> {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                }
                else -> {
                    // Multilingual Auto: accepts both Telugu and English
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("te-IN", "en-IN", "en-US"))
                    putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayOf("te-IN", "en-IN", "en-US"))
                }
            }
        }

        _state.value = State.Listening
        recognizer?.startListening(intent)
        Log.d(TAG, "Started listening (lang=$langPref, on-device=${isOnDeviceAvailable()})")
    }

    /** Stop the current listening session. */
    fun stopListening() {
        recognizer?.stopListening()
        if (_state.value is State.Listening) {
            _state.value = State.Processing
        }
    }

    fun cancel() {
        recognizer?.cancel()
        _state.value = State.Idle
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        _state.value = State.Idle
    }

    // ── RecognitionListener ───────────────────────────────────────────────

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for speech")
            _state.value = State.Listening
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Speech began")
        }

        override fun onRmsChanged(rmsdB: Float) { /* used for waveform animation */ }

        override fun onBufferReceived(buffer: ByteArray?) { }

        override fun onEndOfSpeech() {
            Log.d(TAG, "End of speech")
            _state.value = State.Processing
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val best = matches?.firstOrNull()
            Log.d(TAG, "Result: $best")
            if (!best.isNullOrBlank()) {
                _state.value = State.Result(best)
                onResult?.invoke(best)
            } else {
                _state.value = State.Error("I didn't catch that. Please try again.")
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!partial.isNullOrBlank()) {
                // Surface partial for live display — don't trigger execution yet
                _state.value = State.Result("$partial…")
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) { }

        override fun onError(error: Int) {
            val msg = when (error) {
                SpeechRecognizer.ERROR_AUDIO          -> "Audio recording error."
                SpeechRecognizer.ERROR_CLIENT         -> "Client-side error."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    "Microphone permission not granted. Grant it in Settings → Apps → Jarvis → Permissions."
                SpeechRecognizer.ERROR_NETWORK        -> "Network error. On-device recognition may not be downloaded."
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout."
                SpeechRecognizer.ERROR_NO_MATCH       -> "No speech matched. Speak clearly and try again."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recogniser is busy."
                SpeechRecognizer.ERROR_SERVER         -> "Speech server error."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
                else                                   -> "Unknown speech error ($error)."
            }
            Log.w(TAG, "Error $error: $msg")
            _state.value = State.Error(msg)
        }
    }
}
