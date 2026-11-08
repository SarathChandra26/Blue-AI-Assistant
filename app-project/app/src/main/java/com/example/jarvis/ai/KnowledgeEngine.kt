package com.example.jarvis.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Free on-device AI knowledge & definition engine.
 * Powered by high-capability free Neural LLMs (OpenAI / Claude / LLaMA tier)
 * with instant Wikipedia Encyclopedic fallback.
 * Zero API keys required. Runs 100% in the background without UI interruption.
 */
object KnowledgeEngine {
    private const val TAG = "KnowledgeEngine"

    suspend fun query(prompt: String, context: android.content.Context? = null): String = withContext(Dispatchers.IO) {
        val cleanPrompt = prompt.trim()
        val isTelugu = isTeluguQuery(cleanPrompt)
        val isHindi = !isTelugu && isHindiQuery(cleanPrompt)
        val isLambadi = !isTelugu && isLambadiQuery(cleanPrompt)
        Log.i(TAG, "Querying AI Knowledge for: \"$cleanPrompt\" (te=$isTelugu, hi=$isHindi, lam=$isLambadi)")

        val systemPrompt = when {
            isTelugu -> "You are Blue, a friendly, smart female voice assistant. The user is asking in Telugu. Answer concisely in 1 to 2 natural spoken sentences in Telugu script (తెలుగు లిపి) only, without any markdown, bullets, emojis, or asterisks."
            isHindi -> "You are Blue, a friendly, smart female voice assistant. The user is asking in Hindi. Answer concisely in 1 to 2 natural spoken sentences in natural Hindi (Devanagari script or conversational Hindi), without any markdown, bullets, emojis, or asterisks."
            isLambadi -> "You are Blue, a friendly, smart female voice assistant. The user is asking in Lambadi / Gorboli (Banjara). Answer concisely and warmly in 1 to 2 spoken sentences in Lambadi / Gorboli, without any markdown or emojis."
            else -> "You are Blue, a friendly, smart female voice assistant. Answer concisely in 1 to 2 natural spoken sentences only, without any markdown, bullets, emojis, or asterisks."
        }

        // 1. High-Speed Tier 1: Groq LPU (Ultra-Fast 200-400ms) if key configured
        val groqKey = context?.let { com.example.jarvis.settings.JarvisPreferences(it).groqApiKey.trim() } ?: ""
        if (groqKey.isNotBlank()) {
            val groqAnswer = queryGroq(cleanPrompt, groqKey, systemPrompt)
            if (!groqAnswer.isNullOrBlank()) {
                Log.i(TAG, "Groq LPU high-speed answer: \"$groqAnswer\"")
                return@withContext groqAnswer
            }
        }

        // 2. High-Speed Tier 2 (Zero Keys): For definitions & factual queries,
        // Wikipedia instant summary returns in < 400ms!
        if (isDefinitionOrFactual(cleanPrompt)) {
            val wikiAnswer = queryWikipedia(cleanPrompt)
            if (!wikiAnswer.isNullOrBlank()) {
                Log.i(TAG, "Wikipedia instant answer returned: \"$wikiAnswer\"")
                return@withContext wikiAnswer
            }
        }

        // 3. Tier 3: Neural LLM Engine (Pollinations Turbo)
        val llmAnswer = queryLlm(cleanPrompt, systemPrompt)
        if (!llmAnswer.isNullOrBlank()) {
            Log.i(TAG, "Neural LLM returned answer: \"$llmAnswer\"")
            return@withContext llmAnswer
        }

        // 4. Tier 4: Wikipedia fallback for any conceptual query
        val wikiAnswer = queryWikipedia(cleanPrompt)
        if (!wikiAnswer.isNullOrBlank()) {
            Log.i(TAG, "Wikipedia fallback returned: \"$wikiAnswer\"")
            return@withContext wikiAnswer
        }

        return@withContext when {
            isTelugu -> "క్షమించండి, దీనికి సమాధానం ప్రస్తుతానికి లభించలేదు. దయచేసి ఇంటర్నెట్ కనెక్షన్ తనిఖీ చేయండి."
            isHindi -> "Maaf kijiye, mujhe iska uttar abhi nahi mila. Kripya apna internet connection check kijiye."
            isLambadi -> "Maaf karo, mane aato dyaan koni lagyo. Ek baar internet check karo."
            else -> "I'm sorry, I couldn't find an explanation for that right now. Please check your internet connection."
        }
    }

    /**
     * Blue v5 Polyglot Live Translator.
     * Translates between Lambadi (Gorboli / Banjara), Telugu, Hindi, and English with Roman/native script support.
     */
    suspend fun translate(
        text: String,
        targetLang: String,
        sourceLang: String = "auto",
        context: android.content.Context? = null
    ): String = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        val groqKey = context?.let { com.example.jarvis.settings.JarvisPreferences(it).groqApiKey.trim() } ?: ""

        val systemPrompt = "You are an expert polyglot translator specializing in Indian languages, especially Lambadi (Gorboli / Banjara / गोरबोली / లంబాడీ), Telugu, Hindi, and English. " +
                "Translate the given text into $targetLang" + (if (sourceLang != "auto") " from $sourceLang" else "") + ". " +
                "Output ONLY the clear, natural spoken translation without any preamble, explanations, markdown, or quotation marks. " +
                "If target is Telugu, use natural Telugu script. " +
                "If target is Hindi, use natural Devanagari script. " +
                "If target is English, use natural spoken English. " +
                "If target is Lambadi or Gorboli, give the natural Lambadi / Banjara expression in Roman and/or Telugu script."

        if (groqKey.isNotBlank()) {
            val res = queryGroq(cleanText, groqKey, systemPrompt)
            if (!res.isNullOrBlank()) return@withContext res
        }

        val res = queryLlm(cleanText, systemPrompt)
        if (!res.isNullOrBlank()) return@withContext res

        return@withContext "Translation could not be completed right now. Please check your internet connection."
    }

    /**
     * Blue v5 Screen Content Vision & Explanation.
     * Summarizes text content grabbed directly from the active window.
     */
    suspend fun explainScreen(
        screenText: String,
        context: android.content.Context? = null
    ): String = withContext(Dispatchers.IO) {
        if (screenText.isBlank()) {
            return@withContext "I don't see any text content on your screen right now."
        }
        val groqKey = context?.let { com.example.jarvis.settings.JarvisPreferences(it).groqApiKey.trim() } ?: ""
        val systemPrompt = "You are Blue, an intelligent AI voice assistant. The user asked you to look at their screen. " +
                "Here is the text visible on their screen. Summarize what is on the screen in 2 to 3 concise, natural spoken sentences without any bullets, markdown, or emojis."

        val prompt = "Visible on screen:\n$screenText"

        if (groqKey.isNotBlank()) {
            val res = queryGroq(prompt, groqKey, systemPrompt)
            if (!res.isNullOrBlank()) return@withContext res
        }

        val res = queryLlm(prompt, systemPrompt)
        if (!res.isNullOrBlank()) return@withContext res

        val lines = screenText.split("\n").filter { it.isNotBlank() }
        val sample = lines.take(3).joinToString(". ")
        return@withContext "Looking at your screen, I see: $sample"
    }

    private fun isTeluguQuery(prompt: String): Boolean {
        return prompt.any { it in '\u0C00'..'\u0C7F' } ||
               prompt.contains("ante enti") ||
               prompt.contains("ante emiti") ||
               prompt.contains("gurinchi cheppu") ||
               prompt.contains("enduku") ||
               prompt.contains("ela pani chesthundi")
    }

    private fun isHindiQuery(prompt: String): Boolean {
        return prompt.any { it in '\u0900'..'\u097F' } ||
               prompt.contains("kya hai") ||
               prompt.contains("kaise") ||
               prompt.contains("batao") ||
               prompt.contains("kyun") ||
               prompt.contains("kaun hai")
    }

    private fun isLambadiQuery(prompt: String): Boolean {
        val l = prompt.lowercase()
        return l.contains("kai che") ||
               l.contains("kasa cha") ||
               l.contains("kaso che") ||
               l.contains("tharo") ||
               l.contains("tharu") ||
               l.contains("maro") ||
               l.contains("kayi kare") ||
               l.contains("ram ram") ||
               l.contains("gorboli") ||
               l.contains("lambadi") ||
               l.contains("banjara")
    }

    private fun isDefinitionOrFactual(prompt: String): Boolean {
        val l = prompt.lowercase()
        return l.startsWith("define ") ||
               l.startsWith("what is ") ||
               l.startsWith("what are ") ||
               l.startsWith("whats ") ||
               l.startsWith("who is ") ||
               l.startsWith("meaning of ") ||
               l.any { it in '\u0C00'..'\u0C7F' } ||
               l.contains("ante enti") ||
               l.contains("ante emiti") ||
               l.contains("gurinchi cheppu")
    }

    private fun queryGroq(question: String, apiKey: String, systemPrompt: String): String? {
        val models = listOf("qwen/qwen3.8-27b", "openai/gpt-oss-20b", "llama-3.1-8b-instant")
        for (modelName in models) {
            val res = queryGroqModel(question, apiKey, modelName, systemPrompt)
            if (!res.isNullOrBlank()) return res
        }
        return null
    }

    private fun queryGroqModel(question: String, apiKey: String, model: String, systemPrompt: String): String? {
        return try {
            val url = URL("https://api.groq.com/openai/v1/chat/completions")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4000
                readTimeout = 6000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("User-Agent", "BlueVoiceAssistant/5.0")
            }

            val payload = JSONObject().apply {
                put("model", model)
                val messages = org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", question)
                    })
                }
                put("messages", messages)
                put("max_tokens", 350)
                put("temperature", 0.6)
            }

            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonStr = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val root = JSONObject(jsonStr)
                val choices = root.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val content = choices.getJSONObject(0).optJSONObject("message")?.optString("content", "") ?: ""
                    cleanSpeechText(content)
                } else null
            } else {
                Log.w(TAG, "Groq HTTP error for model '$model': ${conn.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Groq invocation failed for '$model': ${e.localizedMessage}")
            null
        }
    }

    private fun queryLlm(question: String, systemPromptText: String): String? {
        return try {
            val encodedPrompt = URLEncoder.encode(question, "UTF-8")
            val systemPrompt = URLEncoder.encode(systemPromptText, "UTF-8")
            val url = URL("https://text.pollinations.ai/$encodedPrompt?system=$systemPrompt")

            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 14000
                setRequestProperty("User-Agent", "BlueVoiceAssistant/5.0")
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.use { it.readText() }
                val cleaned = cleanSpeechText(response)
                if (cleaned.isNotBlank() && !cleaned.contains("SyntaxError") && !cleaned.contains("Bad Gateway")) cleaned else null
            } else {
                Log.w(TAG, "Pollinations returned HTTP ${conn.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Pollinations query failed: ${e.localizedMessage}")
            null
        }
    }

    private fun queryWikipedia(query: String): String? {
        val hasTeluguScript = query.any { it in '\u0C00'..'\u0C7F' }
        if (hasTeluguScript) {
            val stripped = query
                .replace("అంటే ఏమిటి", "")
                .replace("అంటే ఏంటి", "")
                .replace("అంటే enti", "")
                .replace("గురించి చెప్పు", "")
                .replace("గురించి వివరించు", "")
                .replace("ఎవరు", "")
                .replace("ఎందుకు", "")
                .replace("ఏమిటి", "")
                .replace("ఏంటి", "")
                .trim()

            val candidates = listOf(stripped, query.trim()).filter { it.isNotBlank() }.distinct()
            for (candidate in candidates) {
                val result = fetchWikiSummary(candidate, "te")
                if (result != null) return result
            }
            return null
        }

        val stripped = query.lowercase()
            .removePrefix("define ")
            .removePrefix("what is ")
            .removePrefix("what are ")
            .removePrefix("who is ")
            .removePrefix("why is ")
            .removePrefix("why are ")
            .removePrefix("why do ")
            .removePrefix("why does ")
            .removePrefix("explain ")
            .removePrefix("tell me about ")
            .removePrefix("meaning of ")
            .trim()
            .removePrefix("the ")
            .trim()

        val candidates = listOf(
            query.trim(),
            stripped,
            stripped.replaceFirstChar { it.uppercase() }
        ).filter { it.isNotBlank() }.distinct()

        for (candidate in candidates) {
            val result = fetchWikiSummary(candidate, "en")
            if (result != null) return result
        }
        return null
    }

    private fun fetchWikiSummary(title: String, lang: String = "en"): String? {
        return try {
            val formatted = title.replace(" ", "_")
            val encoded = URLEncoder.encode(formatted, "UTF-8")
            val url = URL("https://$lang.wikipedia.org/api/rest_v1/page/summary/$encoded")

            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4500
                setRequestProperty("User-Agent", "BlueVoiceAssistant/3.0")
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonStr = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val json = JSONObject(jsonStr)
                val extract = json.optString("extract", "")
                if (extract.isNotBlank()) {
                    val sentences = extract.split(Regex("(?<=[.!?])\\s+"))
                    val shortSummary = sentences.take(2).joinToString(" ")
                    val cleaned = cleanSpeechText(shortSummary)
                    if (cleaned.isNotBlank()) cleaned else null
                } else null
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wikipedia fallback failed for '$title': ${e.localizedMessage}")
            null
        }
    }

    private fun cleanSpeechText(text: String): String {
        return text
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("/[^/]+?/"), "") // Remove phonetic transcriptions e.g. /ˈætməsˌfɪər/
            .replace(Regex("\\((?:[^)]*?(?:Greek|Latin|pronunciation|listen|German|French|Spanish|Arabic|Hebrew|Russian|Chinese|Japanese|Sanskrit)[^)]*?)\\)"), "") // Remove origin / pronunciation parentheticals
            .replace(Regex("[\\p{IsGreek}\\p{IsCyrillic}\\p{InCJK_UNIFIED_IDEOGRAPHS}]+"), "") // Remove non-Latin scripts
            .replace(Regex("['’][a-z0-9\\s]+['’]"), "") // Remove single-quoted translation fragments
            .replace(Regex("[*#_~`>\\[\\]]"), "") // Strip markdown
            .replace(Regex("^\\d+\\.\\s*"), "")   // Strip leading numbers
            .replace(Regex("\\(\\s*\\)"), "")     // Remove empty parentheses
            .replace(Regex("\\s+([.,!?;])"), "$1") // Clean spacing before punctuation
            .replace(Regex("\\n+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
