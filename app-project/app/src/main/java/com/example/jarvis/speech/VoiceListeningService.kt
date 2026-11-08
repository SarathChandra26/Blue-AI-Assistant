package com.example.jarvis.speech

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.jarvis.MainActivity
import com.example.jarvis.R
import com.example.jarvis.actions.ActionExecutor
import com.example.jarvis.apps.AppLauncher
import com.example.jarvis.audio.VolumeController
import com.example.jarvis.commands.CommandParser
import com.example.jarvis.commands.CommandResult
import com.example.jarvis.commands.JarvisCommand
import com.example.jarvis.flashlight.FlashlightController
import com.example.jarvis.settings.JarvisPreferences
import com.example.jarvis.telephony.TelephonyController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Background wake-word listening service.
 * Continuously listens on-device for "Hey Jarvis" or "Jarvis".
 * When detected, executes the spoken command directly or prompts for input.
 */
class VoiceListeningService : Service() {

    companion object {
        private const val TAG = "VoiceListeningService"
        private const val CHANNEL_ID = "jarvis_wake_channel"
        private const val NOTIFICATION_ID = 2001
        const val ACTION_STOP = "com.example.jarvis.ACTION_STOP_LISTENING"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var isDestroyed = false

    private lateinit var prefs: JarvisPreferences
    private lateinit var tts: JarvisTts
    private lateinit var executor: ActionExecutor

    override fun onCreate() {
        super.onCreate()
        prefs = JarvisPreferences(applicationContext)
        tts = JarvisTts(applicationContext).apply {
            pitch = prefs.pitch
            speechRate = prefs.speechRate
            silentMode = prefs.silentMode
            init()
        }

        val appLauncher = AppLauncher(applicationContext)
        val flashlight = FlashlightController(applicationContext)
        val volume = VolumeController(applicationContext)
        val telephony = TelephonyController(applicationContext)
        executor = ActionExecutor(applicationContext, appLauncher, flashlight, volume, telephony)

        _isRunning.value = true
        Log.i(TAG, "VoiceListeningService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            prefs.wakeWordEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        mainHandler.post {
            startContinuousListening()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isDestroyed = true
        _isRunning.value = false
        serviceScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        tts.shutdown()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.i(TAG, "VoiceListeningService destroyed")
        super.onDestroy()
    }

    private fun startContinuousListening() {
        if (isDestroyed) return

        try {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { sr ->
                    sr.setRecognitionListener(recognitionListener)
                }
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                val localeTag = java.util.Locale.getDefault().toLanguageTag()
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L)
            }

            recognizer?.cancel()
            recognizer?.startListening(intent)
            Log.d(TAG, "Continuous wake-word listening loop active")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting continuous speech recognizer", e)
            try { recognizer?.destroy() } catch (_: Exception) {}
            recognizer = null
            scheduleRestart(1200)
        }
    }

    private fun scheduleRestart(delayMs: Long = 400) {
        if (isDestroyed) return
        mainHandler.postDelayed({
            if (!isDestroyed) {
                startContinuousListening()
            }
        }, delayMs)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                handleRecognizedCandidates(matches)
            } else {
                scheduleRestart(300)
            }
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    scheduleRestart(350)
                }
                SpeechRecognizer.ERROR_CLIENT,
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    try { recognizer?.destroy() } catch (_: Exception) {}
                    recognizer = null
                    scheduleRestart(1000)
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    Log.e(TAG, "Microphone permission revoked, stopping service")
                    stopSelf()
                }
                else -> {
                    scheduleRestart(600)
                }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun handleRecognizedCandidates(candidates: List<String>) {
        val wakeWords = listOf("hey blue", "hi blue", "ok blue", "okay blue", "blue", "hey jarvis", "hi jarvis", "ok jarvis", "okay jarvis", "jarvis")

        for (candidate in candidates) {
            val normalized = CommandParser.normalize(candidate)
            val matchedWake = wakeWords.firstOrNull { normalized == it || normalized.startsWith("$it ") }

            if (matchedWake != null) {
                Log.i(TAG, "Wake word matched from candidate: \"$candidate\" (matched: $matchedWake)")
                triggerHapticFeedback()

                val remainder = normalized.removePrefix(matchedWake).trim()
                if (remainder.isBlank()) {
                    com.example.jarvis.overlay.JarvisOverlayService.activate(applicationContext, "Hey! I'm listening...")
                    scheduleRestart(8000)
                } else {
                    serviceScope.launch {
                        val command = CommandParser.parse(remainder)
                        val result = executor.execute(command, remainder)

                        when (result) {
                            is CommandResult.Success -> {
                                if (result.message.isNotEmpty() && !result.message.startsWith("__")) {
                                    tts.speak(result.message)
                                }
                            }
                            is CommandResult.Failure -> tts.speak(result.reason)
                            is CommandResult.NeedsPermission -> tts.speak("Permission required: ${result.rationale}")
                            is CommandResult.NeedsSetup -> tts.speak(result.instruction)
                            CommandResult.Silent -> {}
                        }
                        scheduleRestart(1200)
                    }
                }
                return
            }
        }

        // No candidate contained wake-word, resume background listening
        scheduleRestart(250)
    }

    private fun triggerHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VibratorManager::class.java)
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(80)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not trigger haptic vibration", e)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Blue Wake Word Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active background listening for 'Hey Blue'"
            setSound(null, null)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, VoiceListeningService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BLUE Active")
            .setContentText("Listening for 'Hey Blue'")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
