package com.example.jarvis.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Jarvis TTS voice using Android TextToSpeech.
 * Configured for a deep, cinematic, calm British male voice (Jarvis style).
 * Includes UtteranceProgressListener to synchronize speech completion with microphone activation.
 */
class JarvisTts(private val context: Context) {

    companion object {
        private const val TAG = "JarvisTTS"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var isReady = false
    private val pendingCallbacks = ConcurrentHashMap<String, () -> Unit>()
    private val elevenLabs = ElevenLabsTtsEngine(context)

    /** Natural, cute, melodic Blue female voice pacing and pitch (ChatGPT Sky/Juniper style) */
    var pitch      = 1.10f   // Sweet, melodic, bright natural feminine pitch
    var speechRate = 1.12f   // Lively, fluent, natural conversational cadence (snappy, eliminates slowness)
    var silentMode = false

    fun init(onReady: () -> Unit = {}) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                configureTts()
                setupProgressListener()
                Log.i(TAG, "TTS ready with cute natural Blue female voice profile")
                onReady()
            } else {
                Log.e(TAG, "TTS initialisation failed: $status")
            }
        }
    }

    private fun configureTts() {
        val engine = tts ?: return

        // Set US English as primary choice for natural ChatGPT-style female delivery
        val usResult = engine.setLanguage(Locale.US)
        if (usResult == TextToSpeech.LANG_MISSING_DATA || usResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            val ukResult = engine.setLanguage(Locale.UK)
            if (ukResult == TextToSpeech.LANG_MISSING_DATA || ukResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "US/UK English missing, using default language")
            }
        }

        engine.setPitch(pitch)
        engine.setSpeechRate(speechRate)

        selectBestBlueVoice(engine)
    }

    private fun selectBestBlueVoice(engine: TextToSpeech) {
        val voices = engine.voices ?: return
        val englishVoices = voices.filter { it.locale.language == "en" }

        Log.i(TAG, "Inspecting ${englishVoices.size} available English voices on device:")
        for (v in englishVoices) {
            Log.d(TAG, "Available voice: name=${v.name} | locale=${v.locale} | quality=${v.quality} | netReq=${v.isNetworkConnectionRequired} | features=${v.features}")
        }

        fun isFemaleVoice(v: Voice): Boolean {
            val n = v.name.lowercase()
            val feats = v.features ?: emptySet()
            if (feats.contains("male") && !feats.contains("female")) return false
            if (n.contains("male") && !n.contains("female")) return false
            if (n.contains("man") || n.contains("boy")) return false
            if (feats.contains("female")) return true
            if (n.contains("female") || n.contains("woman") || n.contains("girl")) return true
            // Known Google US neural female code identifiers
            if (n.contains("sfg") || n.contains("iob") || n.contains("tpf") || n.contains("iom") || n.contains("cfl")) return true
            return false
        }

        // Tier 1: Google US WaveNet / Neural Network female voices (sfg > iob > tpf > iom > cfl)
        val usFemaleNeuralNetwork = listOf("sfg", "iob", "tpf", "iom", "cfl").firstNotNullOfOrNull { code ->
            englishVoices.firstOrNull { v ->
                val n = v.name.lowercase()
                (n.contains("en-us") || n.contains("en_us") || v.locale == Locale.US) &&
                n.contains(code) &&
                (n.contains("network") || v.isNetworkConnectionRequired) &&
                isFemaleVoice(v)
            }
        }

        // Tier 2: Any US English female network voice
        val anyUsFemaleNetwork = englishVoices.firstOrNull { v ->
            val n = v.name.lowercase()
            (n.contains("en-us") || n.contains("en_us") || v.locale == Locale.US) &&
            (n.contains("network") || v.isNetworkConnectionRequired) &&
            isFemaleVoice(v)
        }

        // Tier 3: US English female local neural voice (sfg > iob > tpf > iom > cfl)
        val usFemaleLocal = listOf("sfg", "iob", "tpf", "iom", "cfl").firstNotNullOfOrNull { code ->
            englishVoices.firstOrNull { v ->
                val n = v.name.lowercase()
                (n.contains("en-us") || n.contains("en_us") || v.locale == Locale.US) &&
                n.contains(code) &&
                isFemaleVoice(v)
            }
        }

        // Tier 4: Any English female network voice (UK / Australian / Canadian)
        val anyEnglishFemaleNetwork = englishVoices.firstOrNull { v ->
            (v.name.contains("network") || v.isNetworkConnectionRequired) && isFemaleVoice(v)
        }

        // Tier 5: Any female voice
        val anyFemale = englishVoices.firstOrNull { v -> isFemaleVoice(v) }

        val chosen = usFemaleNeuralNetwork
            ?: anyUsFemaleNetwork
            ?: usFemaleLocal
            ?: anyEnglishFemaleNetwork
            ?: anyFemale
            ?: englishVoices.firstOrNull()

        if (chosen != null) {
            try {
                engine.voice = chosen
                Log.i(TAG, "Selected Blue natural female voice: ${chosen.name} (locale=${chosen.locale}, netReq=${chosen.isNetworkConnectionRequired})")
            } catch (e: Exception) {
                Log.w(TAG, "Could not set chosen voice, using engine default", e)
            }
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "TTS started speaking: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId != null) {
                    val callback = pendingCallbacks.remove(utteranceId)
                    if (callback != null) {
                        mainHandler.post { callback.invoke() }
                    }
                }
            }

            override fun onError(utteranceId: String?) {
                if (utteranceId != null) {
                    val callback = pendingCallbacks.remove(utteranceId)
                    if (callback != null) {
                        mainHandler.post { callback.invoke() }
                    }
                }
            }
        })
    }

    /**
     * Clean and sanitize raw text for smooth speech delivery.
     */
    fun sanitizeForSpeech(raw: String): String {
        return raw.replace(Regex("https?://\\S+"), "link")
            .replace("&", " and ")
            .replace("%", " percent ")
            .replace("#", " hashtag ")
            .replace(Regex("[_\\-*#/\\\\]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Speak [text].
     * Uses ElevenLabs Rachel Multilingual voice when configured,
     * seamlessly falling back to Google Neural2 / WaveNet voice.
     * Optional [onDone] callback triggers once speech audio completes playing on the speaker.
     */
    fun speak(text: String, priority: Int = TextToSpeech.QUEUE_FLUSH, onDone: (() -> Unit)? = null) {
        if (silentMode || text.isBlank()) {
            onDone?.invoke()
            return
        }
        val cleanText = sanitizeForSpeech(text)

        // 1. ElevenLabs Neural Voice (flagship natural Rachel: English, Telugu, Hindi)
        if (elevenLabs.isConfigured()) {
            val handled = elevenLabs.speak(cleanText, onDone = onDone, onError = {
                // Instant fallback to Google on-device voice
                speakLocally(cleanText, priority, onDone)
            })
            if (handled) return
        }

        // 2. On-device Google Neural2 Voice
        speakLocally(cleanText, priority, onDone)
    }

    private fun speakLocally(cleanText: String, priority: Int, onDone: (() -> Unit)?) {
        if (!isReady) {
            onDone?.invoke()
            return
        }

        val utteranceId = "blue_${System.currentTimeMillis()}"
        if (onDone != null) {
            pendingCallbacks[utteranceId] = onDone
        }

        // Multilingual script detection for v4 fallback
        when {
            cleanText.any { it in '\u0C00'..'\u0C7F' } -> {
                // Telugu script
                tts?.setLanguage(Locale("te", "IN"))
            }
            cleanText.any { it in '\u0900'..'\u097F' } -> {
                // Hindi script
                tts?.setLanguage(Locale("hi", "IN"))
            }
            else -> {
                // Default to US English Neural2 voice
                tts?.setLanguage(Locale.US)
            }
        }

        tts?.setPitch(pitch)
        tts?.setSpeechRate(speechRate)
        tts?.speak(cleanText, priority, null, utteranceId)
    }

    fun stop() {
        elevenLabs.stop()
        pendingCallbacks.clear()
        tts?.stop()
    }

    fun shutdown() {
        elevenLabs.stop()
        pendingCallbacks.clear()
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }

    fun applySettings() {
        tts?.setPitch(pitch)
        tts?.setSpeechRate(speechRate)
    }
}
