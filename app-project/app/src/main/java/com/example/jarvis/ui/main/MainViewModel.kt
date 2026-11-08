package com.example.jarvis.ui.main

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jarvis.accessibility.JarvisAccessibilityService
import com.example.jarvis.actions.ActionExecutor
import com.example.jarvis.apps.AppLauncher
import com.example.jarvis.audio.VolumeController
import com.example.jarvis.commands.CommandParser
import com.example.jarvis.commands.CommandResult
import com.example.jarvis.commands.JarvisCommand
import com.example.jarvis.flashlight.FlashlightController
import com.example.jarvis.overlay.JarvisOverlayService
import com.example.jarvis.settings.JarvisPreferences
import com.example.jarvis.speech.JarvisTts
import com.example.jarvis.speech.SpeechRecognitionManager
import com.example.jarvis.speech.VoiceListeningService
import com.example.jarvis.telephony.TelephonyController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Represents the full UI state of the main screen. */
data class JarvisUiState(
    val jarvisState: JarvisState = JarvisState.Idle,
    val lastCommand: String = "",
    val statusMessage: String = "",
    val micPermission: Boolean = false,
    val contactPermission: Boolean = false,
    val accessibilityActive: Boolean = false,
    val onDeviceSpeech: Boolean = false,
    val isListening: Boolean = false,
    val silentMode: Boolean = false,
    val wakeWordActive: Boolean = false,
    val floatingOrbActive: Boolean = false,
    val ttsRate: Float = 1.12f,
    val ttsPitch: Float = 1.10f,
    val showSettings: Boolean = false,
    val showSetup: Boolean = false,
    val elevenLabsApiKey: String = "",
    val elevenLabsVoiceId: String = "cgSgspJ2msm6clMCkdW9",
    val elevenLabsEnabled: Boolean = true,
    val groqApiKey: String = "",
    val assistantLanguage: String = "auto",
    val continuousChatMode: Boolean = false,
    val relationships: Map<String, String> = emptyMap(),
    val workflows: Map<String, List<String>> = emptyMap(),
)

enum class JarvisState { Idle, Listening, Processing, Executing, Success, Error }

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx = application.applicationContext

    // ── Dependencies ─────────────────────────────────────────────────────
    val prefs        = JarvisPreferences(ctx)
    val speech       = SpeechRecognitionManager(ctx)
    val tts          = JarvisTts(ctx)
    val flashlight   = FlashlightController(ctx)
    val volume       = VolumeController(ctx)
    val telephony    = TelephonyController(ctx)
    val appLauncher  = AppLauncher(ctx)
    val memory       = com.example.jarvis.memory.MemoryRepository(ctx)
    val executor     = ActionExecutor(ctx, appLauncher, flashlight, volume, telephony)

    // ── UI State ─────────────────────────────────────────────────────────
    private val _ui = MutableStateFlow(JarvisUiState())
    val ui: StateFlow<JarvisUiState> = _ui.asStateFlow()

    init {
        tts.pitch      = prefs.pitch
        tts.speechRate = prefs.speechRate
        tts.silentMode = prefs.silentMode
        tts.init()

        _ui.update { it.copy(
            wakeWordActive = prefs.wakeWordEnabled,
            floatingOrbActive = prefs.floatingOrbEnabled,
            ttsRate = prefs.speechRate,
            ttsPitch = prefs.pitch,
            elevenLabsApiKey = prefs.elevenLabsApiKey,
            elevenLabsVoiceId = prefs.elevenLabsVoiceId,
            elevenLabsEnabled = prefs.elevenLabsEnabled,
            groqApiKey = prefs.groqApiKey,
            assistantLanguage = prefs.assistantLanguage,
        )}

        refreshMemory()

        // Reflect accessibility service state
        viewModelScope.launch {
            JarvisAccessibilityService.serviceActive.collect { active ->
                _ui.update { it.copy(accessibilityActive = active) }
            }
        }

        // Reflect wake-word service state
        viewModelScope.launch {
            VoiceListeningService.isRunning.collect { running ->
                _ui.update { it.copy(wakeWordActive = running) }
            }
        }

        // Reflect floating overlay service state
        viewModelScope.launch {
            JarvisOverlayService.isRunning.collect { running ->
                _ui.update { it.copy(floatingOrbActive = running) }
            }
        }

        // Mirror speech recognizer state to UI state
        viewModelScope.launch {
            speech.state.collect { speechState ->
                when (speechState) {
                    is SpeechRecognitionManager.State.Idle       -> transitionTo(JarvisState.Idle)
                    is SpeechRecognitionManager.State.Listening  -> transitionTo(JarvisState.Listening)
                    is SpeechRecognitionManager.State.Processing -> transitionTo(JarvisState.Processing)
                    is SpeechRecognitionManager.State.Result     -> {
                        _ui.update { it.copy(lastCommand = speechState.text) }
                    }
                    is SpeechRecognitionManager.State.Error -> {
                        transitionTo(JarvisState.Error, speechState.message)
                    }
                    is SpeechRecognitionManager.State.PermissionDenied -> {
                        transitionTo(JarvisState.Error,
                            "Microphone permission denied. Grant it in Settings.")
                    }
                }
            }
        }

        if (prefs.wakeWordEnabled && !VoiceListeningService.isRunning.value) {
            toggleWakeWord(true)
        }

        if (prefs.floatingOrbEnabled && !JarvisOverlayService.isRunning.value) {
            toggleFloatingOrb(true)
        }

        refreshPermissions()
    }

    // ── Public actions ────────────────────────────────────────────────────

    fun startListening() {
        refreshPermissions()
        if (!_ui.value.micPermission) {
            _ui.update { it.copy(
                jarvisState = JarvisState.Error,
                statusMessage = "Microphone permission not granted."
            )}
            return
        }
        speech.startListening { rawText -> onSpeechResult(rawText) }
    }

    fun stopListening() = speech.stopListening()

    fun toggleSettings(show: Boolean) = _ui.update { it.copy(showSettings = show) }
    fun toggleSetup(show: Boolean)    = _ui.update { it.copy(showSetup = show) }

    fun toggleWakeWord(enabled: Boolean) {
        prefs.wakeWordEnabled = enabled
        val intent = Intent(ctx, VoiceListeningService::class.java)
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        } else {
            ctx.stopService(intent)
        }
        _ui.update { it.copy(wakeWordActive = enabled) }
    }

    fun toggleFloatingOrb(enabled: Boolean) {
        prefs.floatingOrbEnabled = enabled
        val intent = Intent(ctx, JarvisOverlayService::class.java)
        if (enabled) {
            ctx.startService(intent)
        } else {
            ctx.stopService(intent)
        }
        _ui.update { it.copy(floatingOrbActive = enabled) }
    }

    fun updateTtsPitch(v: Float) {
        prefs.pitch = v
        tts.pitch = v
        tts.applySettings()
        _ui.update { it.copy(ttsPitch = v) }
    }

    fun updateTtsRate(v: Float) {
        prefs.speechRate = v
        tts.speechRate = v
        tts.applySettings()
        _ui.update { it.copy(ttsRate = v) }
    }

    fun updateSilentMode(enabled: Boolean) {
        prefs.silentMode = enabled
        tts.silentMode = enabled
        _ui.update { it.copy(silentMode = enabled) }
    }

    fun updateElevenLabsKey(key: String) {
        prefs.elevenLabsApiKey = key
        _ui.update { it.copy(elevenLabsApiKey = key) }
    }

    fun updateElevenLabsVoiceId(voiceId: String) {
        prefs.elevenLabsVoiceId = voiceId
        _ui.update { it.copy(elevenLabsVoiceId = voiceId) }
    }

    fun toggleElevenLabs(enabled: Boolean) {
        prefs.elevenLabsEnabled = enabled
        _ui.update { it.copy(elevenLabsEnabled = enabled) }
    }

    fun updateGroqKey(key: String) {
        prefs.groqApiKey = key
        _ui.update { it.copy(groqApiKey = key) }
    }

    fun updateAssistantLanguage(lang: String) {
        prefs.assistantLanguage = lang
        _ui.update { it.copy(assistantLanguage = lang) }
    }

    fun refreshPermissions() {
        val mic = ContextCompat.checkSelfPermission(ctx,
            android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val contacts = ContextCompat.checkSelfPermission(ctx,
            android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        _ui.update { it.copy(
            micPermission = mic,
            contactPermission = contacts,
            accessibilityActive = JarvisAccessibilityService.isRunning(),
            onDeviceSpeech = speech.isOnDeviceAvailable(),
        )}
    }

    // ── Memory & Relationship Management (v5) ─────────────────────────────

    fun refreshMemory() {
        _ui.update { it.copy(
            relationships = memory.getAllRelationships(),
            workflows = memory.getAllWorkflows()
        )}
    }

    fun saveRelationship(relation: String, contact: String) {
        memory.setRelationship(relation, contact)
        refreshMemory()
    }

    fun deleteRelationship(relation: String) {
        memory.removeRelationship(relation)
        refreshMemory()
    }

    fun saveWorkflow(name: String, steps: List<String>) {
        memory.saveWorkflow(name, steps)
        refreshMemory()
    }

    fun deleteWorkflow(name: String) {
        memory.deleteWorkflow(name)
        refreshMemory()
    }

    fun toggleContinuousChat(enabled: Boolean) {
        _ui.update { it.copy(continuousChatMode = enabled) }
        if (enabled) {
            tts.speak("Continuous chat mode enabled. Go ahead, I'm listening.", onDone = {
                android.os.Handler(android.os.Looper.getMainLooper()).post { startListening() }
            })
        } else {
            tts.speak("Exited chat mode.")
        }
    }

    // ── Speech / Command result pipeline ─────────────────────────────────

    fun executeCommand(rawText: String) = onSpeechResult(rawText)

    private fun onSpeechResult(rawText: String) {
        val command = CommandParser.parse(rawText)
        if (command is JarvisCommand.AskAi) {
            transitionTo(JarvisState.Processing, "Thinking...")
        } else {
            transitionTo(JarvisState.Executing, rawText)
        }

        viewModelScope.launch {
            val result = executor.execute(command, rawText)

            when (result) {
                is CommandResult.Success -> {
                    val msg = result.message
                    when {
                        msg == "__OPEN_JARVIS_SETTINGS__" -> {
                            transitionTo(JarvisState.Success, "Opening Blue settings")
                            _ui.update { it.copy(showSettings = true) }
                        }
                        msg == "__SHOW_HELP__" -> {
                            transitionTo(JarvisState.Success, "Here's what I can do")
                            _ui.update { it.copy(showSetup = true) }
                        }
                        msg == "__CHAT_MODE_ON__" -> {
                            _ui.update { it.copy(continuousChatMode = true) }
                            transitionTo(JarvisState.Success, "Continuous chat mode active")
                            tts.speak("Continuous chat mode active! I'm listening, go ahead.", onDone = {
                                android.os.Handler(android.os.Looper.getMainLooper()).post { startListening() }
                            })
                        }
                        msg == "__CHAT_MODE_OFF__" -> {
                            _ui.update { it.copy(continuousChatMode = false) }
                            transitionTo(JarvisState.Success, "Exited chat mode")
                            tts.speak("Exited chat mode.")
                        }
                        else -> {
                            transitionTo(JarvisState.Success, msg)
                            refreshMemory()
                            tts.speak(msg, onDone = {
                                if (_ui.value.continuousChatMode) {
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        startListening()
                                    }, 400)
                                }
                            })
                        }
                    }
                }
                is CommandResult.Failure -> {
                    transitionTo(JarvisState.Error, result.reason)
                    tts.speak(result.reason, onDone = {
                        if (_ui.value.continuousChatMode) {
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                startListening()
                            }, 500)
                        }
                    })
                }
                is CommandResult.NeedsPermission -> {
                    transitionTo(JarvisState.Error, result.rationale)
                    tts.speak("Permission required. ${result.rationale}")
                }
                is CommandResult.NeedsSetup -> {
                    transitionTo(JarvisState.Error, result.instruction)
                    tts.speak(result.instruction)
                }
                CommandResult.Silent -> transitionTo(JarvisState.Idle)
            }
        }
    }

    private fun transitionTo(state: JarvisState, message: String = "") {
        _ui.update { it.copy(jarvisState = state, statusMessage = message) }
    }

    override fun onCleared() {
        super.onCleared()
        speech.destroy()
        tts.shutdown()
        flashlight.release()
    }
}
