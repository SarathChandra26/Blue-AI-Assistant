package com.example.jarvis.settings

import android.content.Context
import androidx.core.content.edit

/**
 * Persists user preferences using SharedPreferences.
 * No DataStore dependency needed for this small set of values.
 */
class JarvisPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)

    var pitch: Float
        get() = prefs.getFloat("blue_tts_pitch", 1.10f)
        set(v) { prefs.edit { putFloat("blue_tts_pitch", v) } }

    var speechRate: Float
        get() = prefs.getFloat("blue_tts_rate", 1.12f)
        set(v) { prefs.edit { putFloat("blue_tts_rate", v) } }

    var silentMode: Boolean
        get() = prefs.getBoolean("silent_mode", false)
        set(v) { prefs.edit { putBoolean("silent_mode", v) } }

    var wakeWordEnabled: Boolean
        get() = prefs.getBoolean("wake_word", false)
        set(v) { prefs.edit { putBoolean("wake_word", v) } }

    var floatingOrbEnabled: Boolean
        get() = prefs.getBoolean("floating_orb", false)
        set(v) { prefs.edit { putBoolean("floating_orb", v) } }

    var showOnboarding: Boolean
        get() = prefs.getBoolean("show_onboarding", true)
        set(v) { prefs.edit { putBoolean("show_onboarding", v) } }

    // ── ElevenLabs Neural Voice (English, Telugu, Hindi Multilingual) ─────
    var elevenLabsApiKey: String
        get() = prefs.getString("elevenlabs_api_key", "") ?: ""
        set(v) { prefs.edit { putString("elevenlabs_api_key", v.trim()) } }

    var elevenLabsVoiceId: String
        get() {
            val v = prefs.getString("elevenlabs_voice_id", "cgSgspJ2msm6clMCkdW9") ?: "cgSgspJ2msm6clMCkdW9"
            return if (v == "21m00Tcm4TlvDq8ikWAM" || v.isBlank()) "cgSgspJ2msm6clMCkdW9" else v
        }
        set(v) { prefs.edit { putString("elevenlabs_voice_id", v.trim()) } }

    var elevenLabsEnabled: Boolean
        get() = prefs.getBoolean("elevenlabs_enabled", true)
        set(v) { prefs.edit { putBoolean("elevenlabs_enabled", v) } }

    // ── High-Speed Free LLM (Groq LPU Instant 300ms) ──────────────────────
    var groqApiKey: String
        get() = prefs.getString("groq_api_key", "") ?: ""
        set(v) { prefs.edit { putString("groq_api_key", v.trim()) } }

    // ── Multilingual Language Selection (v4: Telugu & English) ───────────
    var assistantLanguage: String
        get() = prefs.getString("assistant_language", "auto") ?: "auto" // "auto", "te-IN", "en-US"
        set(v) { prefs.edit { putString("assistant_language", v) } }
}
