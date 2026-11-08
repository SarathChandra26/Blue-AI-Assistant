package com.example.jarvis.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.jarvis.settings.JarvisPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * High-fidelity ElevenLabs Neural TTS integration for Blue.
 * Selected flagship multilingual voice: Rachel (Voice ID: 21m00Tcm4TlvDq8ikWAM).
 * Uses eleven_multilingual_v2 to naturally speak English, Telugu, and Hindi.
 * Includes local disk audio caching and instant playback fallback.
 */
class ElevenLabsTtsEngine(private val context: Context) {

    companion object {
        private const val TAG = "ElevenLabsTTS"
        const val DEFAULT_VOICE_ID = "cgSgspJ2msm6clMCkdW9" // Jessica - Playful, Bright, Warm (Free Premade Multilingual v2)
        const val MODEL_MULTILINGUAL = "eleven_multilingual_v2"
    }

    private val prefs = JarvisPreferences(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private val cacheDir = File(context.cacheDir, "elevenlabs_cache").apply { mkdirs() }

    fun isConfigured(): Boolean {
        return prefs.elevenLabsApiKey.isNotBlank() && prefs.elevenLabsEnabled
    }

    /**
     * Synthesize and stream speech audio from ElevenLabs.
     * Returns true if request was accepted for processing.
     * Calls [onError] to seamlessly fall back to local TTS if any error occurs.
     */
    fun speak(
        text: String,
        onDone: (() -> Unit)? = null,
        onError: () -> Unit
    ): Boolean {
        if (!isConfigured()) return false

        scope.launch {
            try {
                val voiceId = prefs.elevenLabsVoiceId.ifBlank { DEFAULT_VOICE_ID }
                val cacheKey = hashKey("${voiceId}_${MODEL_MULTILINGUAL}_$text")
                val cachedFile = File(cacheDir, "$cacheKey.mp3")

                if (cachedFile.exists() && cachedFile.length() > 500) {
                    Log.d(TAG, "Playing cached ElevenLabs audio for: \"${text.take(30)}...\"")
                    mainHandler.post { playAudioFile(cachedFile, onDone, onError) }
                    return@launch
                }

                Log.i(TAG, "Generating ElevenLabs audio for voice $voiceId: \"${text.take(30)}...\"")
                val url = URL("https://api.elevenlabs.io/v1/text-to-speech/$voiceId?output_format=mp3_44100_128")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 7000
                    readTimeout = 10000
                    doOutput = true
                    setRequestProperty("xi-api-key", prefs.elevenLabsApiKey.trim())
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "audio/mpeg")
                }

                val payload = JSONObject().apply {
                    put("text", text)
                    put("model_id", MODEL_MULTILINGUAL)
                    put("voice_settings", JSONObject().apply {
                        put("stability", 0.50)
                        put("similarity_boost", 0.80)
                        put("style", 0.0)
                        put("use_speaker_boost", true)
                    })
                }

                conn.outputStream.use { os ->
                    os.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val tempFile = File(cacheDir, "${cacheKey}_tmp.mp3")
                    conn.inputStream.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    tempFile.renameTo(cachedFile)
                    Log.i(TAG, "ElevenLabs synthesis success (${cachedFile.length()} bytes)")
                    mainHandler.post { playAudioFile(cachedFile, onDone, onError) }
                } else {
                    val errText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    Log.w(TAG, "ElevenLabs HTTP $responseCode error: $errText. Falling back to local TTS.")
                    mainHandler.post { onError() }
                }
            } catch (e: Exception) {
                Log.w(TAG, "ElevenLabs invocation failed: ${e.localizedMessage}. Falling back to local TTS.")
                mainHandler.post { onError() }
            }
        }
        return true
    }

    private fun playAudioFile(file: File, onDone: (() -> Unit)?, onError: () -> Unit) {
        try {
            stop()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    it.release()
                    mediaPlayer = null
                    onDone?.invoke()
                }
                setOnErrorListener { mp, what, extra ->
                    Log.w(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    mp.release()
                    mediaPlayer = null
                    onError()
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaPlayer play failed: ${e.localizedMessage}")
            onError()
        }
    }

    fun stop() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping MediaPlayer: ${e.localizedMessage}")
        }
    }

    private fun hashKey(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.take(16).joinToString("") { "%02x".format(it) }
    }
}
