package com.example.jarvis.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.util.Log
import com.example.jarvis.commands.CommandResult
import kotlin.math.max
import kotlin.math.min

/**
 * Resolves app names to launch intents using a multi-tiered fuzzy search:
 * 1. Normalized alias / hint check
 * 2. Token-based and substring fuzzy matching against all installed launcher activities
 * 3. Levenshtein edit-distance similarity (handling slight speech misrecognitions)
 * 4. Fallback to Google Play Store search
 */
class AppLauncher(private val context: Context) {

    companion object {
        private const val TAG = "AppLauncher"

        // Common nicknames and spoken aliases mapped to search hints / keywords
        private val ALIAS_EXPANSIONS = mapOf(
            "insta"         to "instagram",
            "ig"            to "instagram",
            "yt"            to "youtube",
            "wa"            to "whatsapp",
            "whatsup"       to "whatsapp",
            "whats app"     to "whatsapp",
            "gpt"           to "chatgpt",
            "chat gpt"      to "chatgpt",
            "openai"        to "chatgpt",
            "fb"            to "facebook",
            "browser"       to "chrome",
            "google chrome" to "chrome",
            "net"           to "chrome",
            "calc"          to "calculator",
            "cal"           to "calendar",
            "photos"        to "photos",
            "gallery"       to "photos",
            "music"         to "spotify",
            "msg"           to "messages",
            "text"          to "messages",
            "playstore"     to "play store",
            "market"        to "play store",
            "gpay"          to "google pay",
            "paytm"         to "paytm",
            "swiggy"        to "swiggy",
            "zomato"        to "zomato"
        )
    }

    data class InstalledApp(
        val packageName: String,
        val activityName: String,
        val label: String,
        val normalizedLabel: String,
        val tokens: List<String>
    )

    /**
     * Try to launch the best matching installed app.
     */
    fun launch(appName: String): CommandResult {
        val rawQuery = appName.trim().lowercase()
        val query = ALIAS_EXPANSIONS[rawQuery] ?: rawQuery

        val installedApps = getInstalledApps()
        if (installedApps.isEmpty()) {
            return tryPlayStore(appName)
        }

        // Score all apps against query
        var bestApp: InstalledApp? = null
        var highestScore = 0.0

        for (app in installedApps) {
            val score = scoreAppMatch(query, rawQuery, app)
            if (score > highestScore) {
                highestScore = score
                bestApp = app
            }
        }

        Log.i(TAG, "Search for '$appName' (expanded='$query') -> best: '${bestApp?.label}' (${bestApp?.packageName}) score=$highestScore")

        // Confident match threshold (50.0)
        if (bestApp != null && highestScore >= 50.0) {
            val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setClassName(bestApp.packageName, bestApp.activityName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return try {
                context.startActivity(launchIntent)
                CommandResult.Success("Opening ${bestApp.label}")
            } catch (e: Exception) {
                // Fallback to package launch intent
                val fallbackIntent = context.packageManager.getLaunchIntentForPackage(bestApp.packageName)
                if (fallbackIntent != null) {
                    fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(fallbackIntent)
                    CommandResult.Success("Opening ${bestApp.label}")
                } else {
                    CommandResult.Failure("Could not open ${bestApp.label}")
                }
            }
        }

        // If no match was found, fall back to Play Store
        return tryPlayStore(appName)
    }

    private fun scoreAppMatch(query: String, rawQuery: String, app: InstalledApp): Double {
        val label = app.normalizedLabel
        val pkg = app.packageName.lowercase()

        // 1. Exact label match (highest priority)
        if (label == query || label == rawQuery) return 100.0

        // 2. Token exact match (e.g. "chrome" matches token in "Google Chrome", "pay" in "Google Pay")
        if (app.tokens.contains(query) || app.tokens.contains(rawQuery)) return 95.0

        // 3. Package name exact token match (e.g. "com.android.chrome", "com.instagram.android")
        val pkgSegments = pkg.split(".")
        if (pkgSegments.contains(query) || pkgSegments.contains(rawQuery)) return 92.0

        // 4. Label starts with query (e.g. "insta" starts "Instagram", "calc" starts "Calculator")
        if (label.startsWith(query) || label.startsWith(rawQuery)) return 90.0

        // 5. Query starts with label
        if (query.startsWith(label) || rawQuery.startsWith(label)) return 88.0

        // 6. Token prefix match (e.g. "calc" matches "Calculator", "insta" matches "Instagram")
        if (app.tokens.any { it.startsWith(query) || it.startsWith(rawQuery) }) return 85.0

        // 7. Substring match (e.g. "chrome" inside "Google Chrome")
        if (label.contains(query) || label.contains(rawQuery)) return 80.0

        // 8. Package name substring match
        if (pkg.contains(query) || pkg.contains(rawQuery)) return 75.0

        // 9. Acronym match (e.g. "yt" -> "YouTube", "ig" -> "Instagram")
        val acronym = app.tokens.mapNotNull { it.firstOrNull() }.joinToString("")
        if (acronym.isNotEmpty() && (acronym == query || acronym == rawQuery)) return 72.0

        // 10. Levenshtein similarity for handling minor speech-to-text typos
        val dist = levenshteinDistance(query, label)
        val maxLen = max(query.length, label.length)
        val similarity = if (maxLen > 0) 1.0 - (dist.toDouble() / maxLen) else 0.0

        if (similarity >= 0.70) {
            return 60.0 + (similarity * 15.0)
        }

        // Compare query with individual tokens using Levenshtein
        var bestTokenSim = 0.0
        for (token in app.tokens) {
            val tDist = levenshteinDistance(query, token)
            val tMax = max(query.length, token.length)
            val tSim = if (tMax > 0) 1.0 - (tDist.toDouble() / tMax) else 0.0
            if (tSim > bestTokenSim) bestTokenSim = tSim
        }
        if (bestTokenSim >= 0.75) {
            return 58.0 + (bestTokenSim * 15.0)
        }

        return 0.0
    }

    @Volatile
    private var cachedInstalledApps: List<InstalledApp>? = null
    @Volatile
    private var lastCacheTime = 0L

    private fun getInstalledApps(forceRefresh: Boolean = false): List<InstalledApp> {
        val now = System.currentTimeMillis()
        val cached = cachedInstalledApps
        if (!forceRefresh && cached != null && (now - lastCacheTime) < 60_000L) {
            return cached
        }

        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val activities: List<ResolveInfo> = try {
            pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            Log.e(TAG, "Error querying launcher activities", e)
            emptyList()
        }

        val nonAlpha = Regex("[^a-z0-9 ]")
        val whitespace = Regex("\\s+")

        val apps = activities.mapNotNull { info ->
            try {
                val label = info.loadLabel(pm).toString().trim()
                if (label.isBlank()) return@mapNotNull null
                val normalized = label.lowercase()
                    .replace(nonAlpha, " ")
                    .replace(whitespace, " ")
                    .trim()
                val tokens = normalized.split(" ").filter { it.isNotBlank() }

                InstalledApp(
                    packageName = info.activityInfo.packageName,
                    activityName = info.activityInfo.name,
                    label = label,
                    normalizedLabel = normalized,
                    tokens = tokens
                )
            } catch (e: Exception) {
                null
            }
        }

        cachedInstalledApps = apps
        lastCacheTime = now
        return apps
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    dp[i - 1][j] + 1,
                    min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                )
            }
        }
        return dp[s1.length][s2.length]
    }

    private fun tryPlayStore(appName: String): CommandResult {
        return try {
            val playIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://search?q=${Uri.encode(appName)}&c=apps")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(playIntent)
            CommandResult.Failure("$appName is not installed. Opening Play Store.")
        } catch (e: Exception) {
            CommandResult.Failure("I couldn't find an app named $appName.")
        }
    }

    fun installedAppLabels(): List<String> {
        return getInstalledApps().map { it.label }.sorted()
    }

    fun findPackageForApp(appName: String): String? {
        val rawQuery = appName.trim().lowercase()
        val query = ALIAS_EXPANSIONS[rawQuery] ?: rawQuery
        val apps = getInstalledApps()
        var bestApp: InstalledApp? = null
        var highestScore = 0.0
        for (app in apps) {
            val score = scoreAppMatch(query, rawQuery, app)
            if (score > highestScore) {
                highestScore = score
                bestApp = app
            }
        }
        return if (bestApp != null && highestScore >= 50.0) bestApp.packageName else null
    }
}
