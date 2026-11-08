package com.example.jarvis.overlay

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import com.example.jarvis.actions.ActionExecutor
import com.example.jarvis.apps.AppLauncher
import com.example.jarvis.audio.VolumeController
import com.example.jarvis.commands.CommandParser
import com.example.jarvis.commands.CommandResult
import com.example.jarvis.commands.JarvisCommand
import com.example.jarvis.flashlight.FlashlightController
import com.example.jarvis.settings.JarvisPreferences
import com.example.jarvis.speech.JarvisTts
import com.example.jarvis.telephony.TelephonyController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Manages the floating Assistive Orb and the compact Siri-style floating pill HUD.
 * Enhanced with generous listening timeouts and speech retry resilience.
 */
class JarvisOverlayService : Service() {

    companion object {
        private const val TAG = "JarvisOverlay"
        const val ACTION_ACTIVATE_HUD = "com.example.jarvis.ACTION_ACTIVATE_HUD"
        const val ACTION_INCOMING_CALL = "com.example.jarvis.ACTION_INCOMING_CALL"
        const val EXTRA_PROMPT = "EXTRA_PROMPT"
        const val EXTRA_CALLER_NAME = "EXTRA_CALLER_NAME"
        const val EXTRA_CALLER_NUMBER = "EXTRA_CALLER_NUMBER"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        fun activate(context: Context, prompt: String = "Hey! I'm listening...") {
            val intent = Intent(context, JarvisOverlayService::class.java).apply {
                action = ACTION_ACTIVATE_HUD
                putExtra(EXTRA_PROMPT, prompt)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun notifyIncomingCall(context: Context, name: String, number: String) {
            val intent = Intent(context, JarvisOverlayService::class.java).apply {
                action = ACTION_INCOMING_CALL
                putExtra(EXTRA_CALLER_NAME, name)
                putExtra(EXTRA_CALLER_NUMBER, number)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null

    // Views
    private var orbView: View? = null
    private var hudView: JarvisHudView? = null
    private var isHudAttached = false

    // Controllers
    private lateinit var prefs: JarvisPreferences
    private lateinit var tts: JarvisTts
    private lateinit var executor: ActionExecutor
    private var speechRecognizer: SpeechRecognizer? = null

    // Speech state tracking
    private var isUserSpeaking = false
    private var speechRetryCount = 0

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot draw overlays — permission missing")
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
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

        initOrbView()
        _isRunning.value = true
        Log.i(TAG, "JarvisOverlayService initialized")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ACTIVATE_HUD -> {
                val prompt = intent.getStringExtra(EXTRA_PROMPT) ?: "Hey! I'm listening..."
                showListeningHud(prompt)
            }
            ACTION_INCOMING_CALL -> {
                val name = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Unknown"
                val num = intent.getStringExtra(EXTRA_CALLER_NUMBER) ?: ""
                showIncomingCallHud(name, num)
            }
        }
        return START_NOT_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initOrbView() {
        val sizePx = (52 * resources.displayMetrics.density).toInt()
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16
            y = 500
        }

        val orb = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setBackgroundResource(android.R.drawable.presence_online)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            alpha = 0.85f
            elevation = 12f
        }

        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var isClick = false

        orb.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isClick = false
                    }
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager?.updateViewLayout(orb, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        showListeningHud("Hey! I'm listening...")
                    }
                    true
                }
                else -> false
            }
        }

        orbView = orb
        try {
            windowManager?.addView(orb, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed adding orb to WindowManager", e)
        }
    }

    /**
     * Display the compact Siri floating pill and begin voice recognition.
     */
    private fun showListeningHud(prompt: String) {
        mainHandler.post {
            ensureHudAttached()
            speechRetryCount = 0
            isUserSpeaking = false

            hudView?.apply {
                state = JarvisHudView.HudState.LISTENING
                actionStatusText = "BLUE"
                transcriptionText = if (prompt.isNotBlank()) prompt else "Listening..."
                setRmsLevel(2f)
            }

            if (prompt.isNotBlank()) {
                tts.speak(prompt) {
                    mainHandler.post {
                        startSpeechRecognition()
                    }
                }
            } else {
                // Completely silent activation (power button) — mic opens in 0ms!
                startSpeechRecognition()
            }
        }
    }

    private fun showIncomingCallHud(name: String, number: String) {
        mainHandler.post {
            ensureHudAttached()
            speechRetryCount = 0
            isUserSpeaking = false

            hudView?.apply {
                state = JarvisHudView.HudState.CALL_RINGING
                actionStatusText = "INCOMING CALL"
                transcriptionText = "$name — Say accept, decline, or silent"
                setRmsLevel(4f)
            }

            // Silent prompt — does not talk over the phone ringer
            startSpeechRecognition()
        }
    }

    private fun ensureHudAttached() {
        if (isHudAttached && hudView != null) return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Docked at the bottom like Siri, 100% transparent everywhere else
        val heightPx = (120 * resources.displayMetrics.density).toInt()
        val hudParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            heightPx,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 10
        }

        val hud = JarvisHudView(this).apply {
            onDismissRequested = {
                dismissHud()
            }
        }

        try {
            windowManager?.addView(hud, hudParams)
            hudView = hud
            isHudAttached = true
            orbView?.visibility = View.GONE
            Log.i(TAG, "Siri floating HUD attached at bottom")
        } catch (e: Exception) {
            Log.e(TAG, "Error attaching HUD overlay", e)
        }
    }

    private fun dismissHud() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        tts.stop()

        if (isHudAttached && hudView != null) {
            try {
                windowManager?.removeView(hudView)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing HUD view", e)
            }
            hudView = null
            isHudAttached = false
        }
        orbView?.visibility = View.VISIBLE
        Log.d(TAG, "HUD dismissed, returning to orb")
    }

    private fun startSpeechRecognition() {
        if (!isHudAttached) return

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).also { sr ->
                sr.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        hudView?.actionStatusText = "LISTENING"
                        if (speechRetryCount == 0) {
                            hudView?.transcriptionText = "Listening... Speak now"
                        }
                        // Micro-haptic pulse so user feels the exact millisecond mic opens
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val vm = getSystemService(android.os.VibratorManager::class.java)
                                vm?.defaultVibrator?.vibrate(
                                    android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK)
                                )
                            } else {
                                @Suppress("DEPRECATION")
                                val v = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                v?.vibrate(25)
                            }
                        } catch (_: Exception) {}
                    }

                    override fun onBeginningOfSpeech() {
                        isUserSpeaking = true
                        hudView?.transcriptionText = "Listening..."
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        hudView?.setRmsLevel(rmsdB)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        hudView?.state = JarvisHudView.HudState.PROCESSING
                        hudView?.actionStatusText = "PROCESSING"
                    }

                    override fun onError(error: Int) {
                        Log.w(TAG, "SpeechRecognizer error: $error (speaking=$isUserSpeaking, retries=$speechRetryCount)")

                        // Give user more time if they paused or haven't spoken yet
                        if (!isUserSpeaking && speechRetryCount < 2 && (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH)) {
                            speechRetryCount++
                            hudView?.transcriptionText = "Listening... Go ahead"
                            mainHandler.postDelayed({
                                if (isHudAttached) {
                                    startSpeechRecognition()
                                }
                            }, 350)
                            return
                        }

                        hudView?.actionStatusText = "STANDBY"
                        hudView?.transcriptionText = "No command detected."
                        mainHandler.postDelayed({ dismissHud() }, 1000)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            // N-best hypothesis parsing: pick best match across all candidates
                            val (command, recognizedText) = CommandParser.parseCandidates(matches)
                            handleCommandExecution(command, recognizedText)
                        } else {
                            dismissHud()
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = partials?.firstOrNull()?.trim()
                        if (!text.isNullOrBlank()) {
                            isUserSpeaking = true
                            hudView?.transcriptionText = "\"$text\""
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                val localeTag = java.util.Locale.getDefault().toLanguageTag()
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
                // Generous timeouts for patient response
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 6000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting overlay speech recognition", e)
            dismissHud()
        }
    }

    private fun handleCommandExecution(command: JarvisCommand, rawText: String) {
        hudView?.apply {
            state = JarvisHudView.HudState.EXECUTING
            transcriptionText = "\"$rawText\""
            if (command is JarvisCommand.AskAi) {
                actionStatusText = "THINKING..."
                setRmsLevel(5f)
            }
        }

        serviceScope.launch {
            val result = executor.execute(command, rawText)

            val spokenMsg = when (result) {
                is CommandResult.Success -> {
                    hudView?.actionStatusText = "BLUE"
                    hudView?.transcriptionText = result.message.ifEmpty { "Done" }
                    result.message
                }
                is CommandResult.Failure -> {
                    hudView?.actionStatusText = "FAILED"
                    result.reason
                }
                is CommandResult.NeedsPermission -> {
                    hudView?.actionStatusText = "PERMISSION"
                    "Permission required: ${result.rationale}"
                }
                is CommandResult.NeedsSetup -> {
                    hudView?.actionStatusText = "SETUP"
                    result.instruction
                }
                CommandResult.Silent -> {
                    hudView?.actionStatusText = "BLUE"
                    ""
                }
            }

            if (spokenMsg.isNotBlank() && !spokenMsg.startsWith("__")) {
                tts.speak(spokenMsg) {
                    mainHandler.postDelayed({ dismissHud() }, 800)
                }
            } else {
                mainHandler.postDelayed({ dismissHud() }, 1400)
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        dismissHud()
        if (orbView != null) {
            try {
                windowManager?.removeView(orbView)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing orb view", e)
            }
            orbView = null
        }
        tts.shutdown()
        _isRunning.value = false
        super.onDestroy()
    }
}
