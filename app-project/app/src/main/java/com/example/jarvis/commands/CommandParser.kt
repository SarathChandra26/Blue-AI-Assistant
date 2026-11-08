package com.example.jarvis.commands

/**
 * Pure rule-based command parser.
 *
 * Normalises the raw speech string, handles wake words ("Hey Jarvis"),
 * and matches against known phrase patterns.
 * No network calls, no ML models.
 *
 * Returns a [JarvisCommand] — [JarvisCommand.Unknown] when nothing matches.
 */
object CommandParser {

    // Verbs for "open / launch / start"
    private val openVerbs = setOf(
        "open", "launch", "start", "run", "go to", "navigate to", "show me", "take me to"
    )

    // Map of spoken app aliases → canonical name used by AppLauncher
    private val appAliases: Map<String, String> = mapOf(
        "chrome"          to "chrome",
        "google chrome"   to "chrome",
        "browser"         to "chrome",
        "brave"           to "brave",
        "brave browser"   to "brave",
        "firefox"         to "firefox",
        "opera"           to "opera",
        "edge"            to "edge",
        "youtube"         to "youtube",
        "yt"              to "youtube",
        "whatsapp"        to "whatsapp",
        "whats app"       to "whatsapp",
        "chatgpt"         to "chatgpt",
        "chat gpt"        to "chatgpt",
        "openai"          to "chatgpt",
        "settings"        to "settings",
        "phone settings"  to "settings",
        "camera"          to "camera",
        "gallery"         to "gallery",
        "photos"          to "gallery",
        "maps"            to "maps",
        "google maps"     to "maps",
        "spotify"         to "spotify",
        "music"           to "spotify",
        "instagram"       to "instagram",
        "insta"           to "instagram",
        "ig"              to "instagram",
        "fb"              to "facebook",
        "facebook"        to "facebook",
        "wa"              to "whatsapp",
        "whatsup"         to "whatsapp",
        "gpt"             to "chatgpt",
        "calc"            to "calculator",
        "gpay"            to "google pay",
        "paytm"           to "paytm",
        "swiggy"          to "swiggy",
        "zomato"          to "zomato",
        "amazon"          to "amazon",
        "flipkart"        to "flipkart",
        "telegram"        to "telegram",
        "gmail"           to "gmail",
        "email"           to "gmail",
        "clock"           to "clock",
        "alarm"           to "clock",
        "calculator"      to "calculator",
        "calendar"        to "calendar",
        "contacts"        to "contacts",
        "dialer"          to "phone",
        "phone"           to "phone",
        "play store"      to "playstore",
        "google play"     to "playstore",
        "files"           to "files",
        "file manager"    to "files",
        // Telugu app aliases
        "యూట్యూబ్"        to "youtube",
        "వాట్సాప్"        to "whatsapp",
        "క్రోమ్"          to "chrome",
        "ఇన్‌స్టాగ్రామ్"    to "instagram",
        "కెమెరా"          to "camera",
        "గ్యాలరీ"         to "gallery",
        "మ్యాప్స్"        to "maps",
        "స్పాటిఫై"       to "spotify",
        "సెట్టింగ్స్"      to "settings",
        "కాలిక్యులేటర్"   to "calculator",
        "క్యాలెండర్"      to "calendar",
        "కాంటాక్ట్స్"     to "contacts",
        "ఫోన్"            to "phone",
    )

    // Volume levels by word
    private val levelWords: Map<String, Int> = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3,
        "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
        "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11,
        "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15,
        "max" to 15, "maximum" to 15, "full" to 15, "minimum" to 0, "min" to 0
    )

    // Preserves English alphanumeric, Telugu (\u0C00-\u0C7F), and Devanagari (\u0900-\u097F)
    private val nonAlphaNumericRegex = Regex("[^a-z0-9\\u0C00-\\u0C7F\\u0900-\\u097F ]")
    private val whitespaceRegex = Regex("\\s+")

    /**
     * Normalise: lowercase, strip punctuation, collapse whitespace,
     * and repair common acoustic homophone slips.
     */
    fun normalize(raw: String): String {
        var text = raw.lowercase()
            .replace(nonAlphaNumericRegex, " ")
            .replace(whitespaceRegex, " ")
            .trim()

        if (text.startsWith("coal ")) text = "call " + text.removePrefix("coal ")
        if (text.startsWith("col ")) text = "call " + text.removePrefix("col ")
        if (text.startsWith("could ")) text = "call " + text.removePrefix("could ")
        if (text.startsWith("call to ")) text = "call " + text.removePrefix("call to ")
        if (text.startsWith("touch on")) text = text.replace("touch on", "torch on")
        if (text.startsWith("touch off")) text = text.replace("touch off", "torch off")
        if (text.endsWith(" on rave")) text = text.replace(" on rave", " on brave")
        if (text.endsWith(" in rave")) text = text.replace(" in rave", " in brave")
        if (text.endsWith(" on grave")) text = text.replace(" on grave", " on brave")
        if (text.endsWith(" in grave")) text = text.replace(" in grave", " in brave")
        if (text == "open whitey" || text == "open why t" || text == "open u tube" || text == "open you tube") text = "open youtube"
        if (text.contains("whats app") || text.contains("what s app") || text.contains("watsapp")) {
            text = text.replace("whats app", "whatsapp").replace("what s app", "whatsapp").replace("watsapp", "whatsapp")
        }
        if (text.contains("screen shot")) text = text.replace("screen shot", "screenshot")
        if (text.contains("spot if why") || text.contains("spotty fie")) {
            text = text.replace("spot if why", "spotify").replace("spotty fie", "spotify")
        }

        return text
    }

    /**
     * Evaluates N-best hypotheses from SpeechRecognizer.
     * Automatically picks the first candidate that matches a valid executable command.
     */
    fun parseCandidates(candidates: List<String>): Pair<JarvisCommand, String> {
        if (candidates.isEmpty()) return Pair(JarvisCommand.Unknown(""), "")

        // 1. First pass: look for a clear device / system / app command (not AskAi and not Unknown)
        for (candidate in candidates) {
            val cmd = parse(candidate)
            if (cmd !is JarvisCommand.Unknown && cmd !is JarvisCommand.AskAi) {
                return Pair(cmd, candidate)
            }
        }

        // 2. Second pass: look for AskAi (knowledge / definitions / explanations)
        for (candidate in candidates) {
            val cmd = parse(candidate)
            if (cmd is JarvisCommand.AskAi) {
                return Pair(cmd, candidate)
            }
        }

        val first = candidates.first()
        return Pair(parse(first), first)
    }

    fun parse(rawSpeech: String): JarvisCommand {
        var text = normalize(rawSpeech)

        // ── Strip wake-word prefixes if user said "Hey Blue ...", "Blue ...", or "Hey Jarvis ..."
        val wakeWordPrefixes = listOf(
            "hey blue", "hi blue", "ok blue", "okay blue", "blue",
            "hey jarvis", "hi jarvis", "ok jarvis", "okay jarvis", "jarvis"
        )
        for (prefix in wakeWordPrefixes) {
            if (text == prefix) {
                return JarvisCommand.Help
            }
            if (text.startsWith("$prefix ")) {
                text = text.removePrefix("$prefix ").trim()
                break
            }
        }

        // ── Blue v5 Intelligence: Translation, Screen Vision, Chat Mode, Workflows ──
        val transCmd = parseTranslation(text)
        if (transCmd != null) return transCmd

        val screenCmd = parseLookAtScreen(text)
        if (screenCmd != null) return screenCmd

        val chatModeCmd = parseChatMode(text)
        if (chatModeCmd != null) return chatModeCmd

        val wfCmd = parseWorkflow(text)
        if (wfCmd != null) return wfCmd

        // ── Memory (v2 & v5: universal relationships, erase, query) ───────────
        val memCmd = parseMemory(text)
        if (memCmd != null) return memCmd

        // ── Telugu & Teluglish Commands (v4) ─────────────────────────────────
        val teluguCmd = parseTelugu(text)
        if (teluguCmd != null) return teluguCmd

        // ── Hindi & Hinglish Commands (v5) ───────────────────────────────────
        val hindiCmd = parseHindi(text)
        if (hindiCmd != null) return hindiCmd

        // ── Lambadi / Gorboli / Banjara Commands (v5) ────────────────────────
        val lambadiCmd = parseLambadi(text)
        if (lambadiCmd != null) return lambadiCmd

        // ── Navigation / gestures ─────────────────────────────────────────
        if (matches(text, "next reel", "next video", "next short", "next post", "next story", "scroll reel")) return JarvisCommand.NextReel
        if (matches(text, "previous reel", "prev reel", "previous video", "previous short", "last reel")) return JarvisCommand.PreviousReel
        if (matches(text, "like reel", "like video", "like this", "double tap", "like")) return JarvisCommand.LikeReel

        if (matches(text, "go back", "back", "navigate back", "press back")) return JarvisCommand.GoBack
        if (matches(text, "go home", "home", "go to home screen")) return JarvisCommand.GoHome
        if (matches(text, "recent apps", "recents", "show recent", "recent")) return JarvisCommand.OpenRecents
        if (matches(text, "scroll up", "move up", "go up")) return JarvisCommand.ScrollUp
        if (matches(text, "scroll down", "move down", "go down")) return JarvisCommand.ScrollDown
        if (matches(text, "swipe left", "slide left")) return JarvisCommand.SwipeLeft
        if (matches(text, "swipe right", "slide right")) return JarvisCommand.SwipeRight
        if (matches(text, "tap", "click", "press")) return JarvisCommand.TapCenter

        // ── Volume ───────────────────────────────────────────────────────────
        if (matches(text, "volume up", "increase volume", "louder", "turn up")) return JarvisCommand.VolumeUp
        if (matches(text, "volume down", "decrease volume", "quieter", "turn down", "lower volume")) return JarvisCommand.VolumeDown
        if (matches(text, "mute", "mute media", "silence media")) return JarvisCommand.MuteMedia
        if (matches(text, "unmute", "unmute media")) return JarvisCommand.UnmuteMedia
        if (matches(text, "silence", "silence ringer", "silent mode", "vibrate", "silence call", "silent call", "mute call", "quiet call", "silent")) return JarvisCommand.SilenceRinger
        if (matches(text, "do not disturb", "dnd", "enable dnd", "turn on dnd")) return JarvisCommand.EnableDnd
        if (matches(text, "disable dnd", "turn off dnd", "disable do not disturb")) return JarvisCommand.DisableDnd

        // Set volume N
        val setVolumeCmd = parseSetVolume(text)
        if (setVolumeCmd != null) return setVolumeCmd

        // ── Deep Media & Music Automation (v2) ────────────────────────────────
        val mediaCmd = parseDeepMedia(text)
        if (mediaCmd != null) return mediaCmd

        // ── Media Playback Controls ───────────────────────────────────────────
        if (text == "play" || matches(text, "pause", "resume", "play music", "pause music", "stop music", "resume music")) return JarvisCommand.MediaPlayPause
        if (matches(text, "next", "next song", "next track", "skip", "skip song", "skip track")) return JarvisCommand.MediaNext
        if (matches(text, "previous", "previous song", "previous track", "prev song", "last song")) return JarvisCommand.MediaPrevious

        // ── Device & Battery Status ───────────────────────────────────────────
        if (matches(text, "battery", "what is my battery", "whats my battery", "battery status", "battery percentage", "battery level", "how much battery", "check battery", "power level")) {
            return JarvisCommand.GetBattery
        }

        // ── Time & Date ───────────────────────────────────────────────────────
        if (matches(text, "time", "what time now", "what time is it", "what is the time", "current time", "tell me the time", "time now", "the time")) {
            return JarvisCommand.GetTime
        }
        if (matches(text, "date", "what day is it", "what day is today", "what is today", "what date is today", "what is the date", "what s the date", "today s date", "date today", "which day is it", "today date")) {
            return JarvisCommand.GetDate
        }

        // ── Clock, Timers & Alarms ────────────────────────────────────────────
        val timerCmd = parseTimer(text)
        if (timerCmd != null) return timerCmd

        val alarmCmd = parseAlarm(text)
        if (alarmCmd != null) return alarmCmd

        if (matches(text, "stopwatch", "open stopwatch", "open timer", "open clock")) return JarvisCommand.OpenClock

        // ── Routines / Macros ─────────────────────────────────────────────────
        if (matches(text, "night mode", "good night", "bedtime", "sleep mode", "goodnight")) return JarvisCommand.NightMode
        if (matches(text, "morning mode", "good morning")) return JarvisCommand.MorningMode
        if (matches(text, "focus mode", "work mode", "study mode")) return JarvisCommand.FocusMode

        // ── Spoken Notifications (v2) ─────────────────────────────────────────
        val notifCmd = parseSpokenNotifications(text)
        if (notifCmd != null) return notifCmd

        // ── Screen, System & Accessibility Actions (v2) ───────────────────────
        val a11yCmd = parseSystemAccessibility(text)
        if (a11yCmd != null) return a11yCmd

        // ── Conversational & System Diagnostics (v2) ──────────────────────────
        val convCmd = parseConversational(text)
        if (convCmd != null) return convCmd

        // ── Flashlight ───────────────────────────────────────────────────────
        if (matches(text, "flashlight on", "turn on flashlight", "turn on torch", "enable flashlight", "enable torch", "torch on")) return JarvisCommand.FlashlightOn
        if (matches(text, "flashlight off", "turn off flashlight", "turn off torch", "disable flashlight", "disable torch", "torch off")) return JarvisCommand.FlashlightOff
        if (matches(text, "toggle flashlight", "toggle torch", "flashlight")) return JarvisCommand.FlashlightToggle

        // ── Screen / lock ────────────────────────────────────────────────────
        if (matches(text, "lock screen", "lock phone", "lock device", "lock")) return JarvisCommand.LockScreen

        // ── Phone calls & Incoming Call Handling ─────────────────────────────
        if (matches(text, "silent", "silent call", "silence call", "silence", "mute call", "quiet", "quiet call")) return JarvisCommand.SilenceIncomingCall
        if (matches(text, "answer", "answer call", "pick up", "accept call", "accept")) return JarvisCommand.AnswerCall
        if (matches(text, "decline", "decline call", "reject call", "reject", "hang up", "end call")) return JarvisCommand.DeclineCall

        // ── WhatsApp Calls ────────────────────────────────────────────────────
        val waCall = parseWhatsAppCall(text)
        if (waCall != null) return waCall

        val callCmd = parseCallContact(text)
        if (callCmd != null) return callCmd

        // ── WhatsApp & Messaging ─────────────────────────────────────────────
        val waMsg = parseWhatsAppMessage(text)
        if (waMsg != null) return waMsg
        if (matches(text, "open whatsapp", "launch whatsapp", "whatsapp")) return JarvisCommand.WhatsAppOpen()

        val smsMsg = parseSmsMessage(text)
        if (smsMsg != null) return smsMsg

        // ── Text entry ───────────────────────────────────────────────────────
        val typeCmd = parseTypeText(text)
        if (typeCmd != null) return typeCmd

        // ── Browser / search / clipboard ─────────────────────────────────────
        if (matches(text, "open the link i copied", "open copied link", "open link i copied", "open clipboard link", "open link from clipboard", "open copied url", "open what i copied", "open copied text", "open the copied link", "open link")) {
            return JarvisCommand.OpenCopiedLink
        }

        val browserSiteCmd = parseOpenSiteInBrowser(text)
        if (browserSiteCmd != null) return browserSiteCmd

        val urlCmd = parseUrl(text)
        if (urlCmd != null) return urlCmd

        val searchCmd = parseSearch(text)
        if (searchCmd != null) return searchCmd

        // ── Settings ─────────────────────────────────────────────────────────
        if (matches(text, "jarvis settings", "open jarvis settings", "settings for jarvis", "blue settings", "open blue settings")) return JarvisCommand.OpenJarvisSettings
        if (matches(text, "help", "what can you do", "commands")) return JarvisCommand.Help

        // ── Knowledge, Definitions & Free AI Engine (v3) ─────────────────────
        val aiCmd = parseKnowledgeAndAi(text)
        if (aiCmd != null) return aiCmd

        // ── Generic app open ─────────────────────────────────────────────────
        val appCmd = parseOpenApp(text)
        if (appCmd != null) return appCmd

        // Fallback 1: If 1-2 words and not a conversational question, attempt to open as an app
        val words = text.split(" ").filter { it.isNotBlank() }
        if (words.size in 1..2 && !text.startsWith("what") && !text.startsWith("how") && !text.startsWith("why") && !text.startsWith("who") && !text.startsWith("can") && !text.startsWith("tell") && !text.startsWith("define")) {
            val canonical = appAliases[text] ?: text
            return JarvisCommand.OpenApp(canonical)
        }

        // Fallback 2: If >= 3 words and unrecognized by device commands, route to Free AI Knowledge Engine
        if (words.size >= 3) {
            return JarvisCommand.AskAi(rawSpeech)
        }

        return JarvisCommand.Unknown(rawSpeech)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun matches(text: String, vararg phrases: String): Boolean =
        phrases.any { text == it || text.startsWith("$it ") || text.endsWith(" $it") || text.contains(" $it ") }

    private fun parseOpenSiteInBrowser(text: String): JarvisCommand.OpenSiteInBrowser? {
        val pattern = Regex("^(?:open|search|launch|go to) (.+?) (?:on|in) (brave|chrome|firefox|opera|edge|google chrome|brave browser)$")
        val match = pattern.find(text) ?: return null
        val siteOrQuery = match.groupValues[1].trim()
        val rawBrowser = match.groupValues[2].trim()
        val browserName = appAliases[rawBrowser] ?: rawBrowser
        if (siteOrQuery.isNotBlank() && browserName.isNotBlank()) {
            return JarvisCommand.OpenSiteInBrowser(siteOrQuery, browserName)
        }
        return null
    }

    private fun parseWhatsAppCall(text: String): JarvisCommand.WhatsAppCall? {
        val pattern1 = Regex("^(?:call|make a call to|ring) (.+?) (?:on|via) (?:whatsapp|wa)$")
        val match1 = pattern1.find(text)
        if (match1 != null) {
            val name = match1.groupValues[1].trim()
            if (name.isNotBlank()) return JarvisCommand.WhatsAppCall(name)
        }

        val pattern2 = Regex("^(?:whatsapp call|wa call)(?: to)? (.+)$")
        val match2 = pattern2.find(text)
        if (match2 != null) {
            val name = match2.groupValues[1].trim()
            if (name.isNotBlank()) return JarvisCommand.WhatsAppCall(name)
        }
        return null
    }

    private fun parseSetVolume(text: String): JarvisCommand.SetVolume? {
        val prefixes = listOf("set volume to", "volume", "set volume at", "volume level")
        for (prefix in prefixes) {
            if (text.startsWith(prefix)) {
                val rest = text.removePrefix(prefix).trim()
                // Try numeric
                rest.toIntOrNull()?.let { n -> if (n in 0..15) return JarvisCommand.SetVolume(n) }
                // Try word
                levelWords[rest]?.let { return JarvisCommand.SetVolume(it) }
            }
        }
        return null
    }

    private fun parseTimer(text: String): JarvisCommand.SetTimer? {
        val regex = Regex("(?:set timer (?:for )?|timer (?:for )?|wake me up (?:after |in )?|wake me (?:after |in )?)(\\d+)\\s*(minute|minutes|min|mins|second|seconds|sec|secs|hour|hours|hr|hrs)?")
        val match = regex.find(text) ?: return null
        val amount = match.groupValues[1].toIntOrNull() ?: return null
        val unit = match.groupValues[2]
        val seconds = when {
            unit.startsWith("sec") -> amount
            unit.startsWith("hour") || unit.startsWith("hr") -> amount * 3600
            else -> amount * 60
        }
        val label = if (text.contains("wake")) "Wake up timer" else "$amount ${if (seconds >= 3600) "hr" else if (seconds >= 60) "min" else "sec"} timer"
        return JarvisCommand.SetTimer(seconds, label)
    }

    private fun parseAlarm(text: String): JarvisCommand.SetAlarm? {
        val regex = Regex("(?:set alarm (?:for |at )?|alarm (?:for |at )?|wake me up (?:for |at )?|wake me (?:for |at )?)(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?")
        val match = regex.find(text) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val ampm = match.groupValues[3]
        if (ampm == "pm" && hour < 12) hour += 12
        if (ampm == "am" && hour == 12) hour = 0
        return if (hour in 0..23 && minute in 0..59) JarvisCommand.SetAlarm(hour, minute) else null
    }

    private fun parseCallContact(text: String): JarvisCommand.CallContact? {
        val callPrefixes = listOf("call", "phone", "dial", "ring", "call contact")
        for (prefix in callPrefixes) {
            if (text.startsWith("$prefix ")) {
                var name = text.removePrefix("$prefix ").trim()
                var onSpeaker = false
                val speakerSuffixes = listOf(" on speaker", " on speakerphone", " speaker", " speakerphone")
                for (suffix in speakerSuffixes) {
                    if (name.endsWith(suffix)) {
                        name = name.removeSuffix(suffix).trim()
                        onSpeaker = true
                        break
                    }
                }
                if (name.isNotBlank() && !name.contains("whatsapp")) {
                    return JarvisCommand.CallContact(name, onSpeaker)
                }
            }
        }
        return null
    }

    private fun parseWhatsAppMessage(text: String): JarvisCommand? {
        val pattern = Regex(
            "(?:whatsapp|send whatsapp(?: message)? to|wa message to)(?: send)?(?: to)? " +
            "([a-z ]+?) (?:saying|that|colon|the message|message is|:) (.+)"
        )
        val match = pattern.find(text) ?: return null
        val contact = match.groupValues[1].trim()
        val message = match.groupValues[2].trim()
        return if (contact.isNotBlank() && message.isNotBlank())
            JarvisCommand.WhatsAppMessage(contact, message)
        else null
    }

    private fun parseSmsMessage(text: String): JarvisCommand? {
        val pattern = Regex(
            "(?:send )?(?:text|sms|message)(?: message)? to ([a-z ]+?) (?:saying|that|colon|the message|message is|:) (.+)"
        )
        val match = pattern.find(text) ?: return null
        val contact = match.groupValues[1].trim()
        val message = match.groupValues[2].trim()
        return if (contact.isNotBlank() && message.isNotBlank())
            JarvisCommand.SendSms(contact, message)
        else null
    }

    private fun parseTypeText(text: String): JarvisCommand.TypeText? {
        val typePrefixes = listOf("type", "write", "enter", "insert", "type in", "dictate")
        for (prefix in typePrefixes) {
            if (text.startsWith("$prefix ")) {
                val body = text.removePrefix("$prefix ").trim()
                if (body.isNotBlank()) return JarvisCommand.TypeText(body)
            }
        }
        return null
    }

    private fun parseUrl(text: String): JarvisCommand.OpenUrl? {
        val urlPrefixes = listOf("open url", "open website", "go to url", "navigate to", "visit")
        for (prefix in urlPrefixes) {
            if (text.startsWith("$prefix ")) {
                var url = text.removePrefix("$prefix ").trim()
                if (!url.startsWith("http")) url = "https://$url"
                return JarvisCommand.OpenUrl(url)
            }
        }
        return null
    }

    private fun parseSearch(text: String): JarvisCommand.WebSearch? {
        val searchPrefixes = listOf(
            "search for", "search", "google", "look up", "look for", "find", "google for"
        )
        for (prefix in searchPrefixes) {
            if (text.startsWith("$prefix ")) {
                val query = text.removePrefix("$prefix ").trim()
                if (query.isNotBlank()) return JarvisCommand.WebSearch(query)
            }
        }
        return null
    }

    private fun parseOpenApp(text: String): JarvisCommand.OpenApp? {
        for (verb in openVerbs) {
            if (text.startsWith("$verb ")) {
                val appRaw = text.removePrefix("$verb ").trim()
                if (appRaw.isNotBlank()) {
                    val canonical = appAliases[appRaw] ?: appRaw
                    return JarvisCommand.OpenApp(canonical)
                }
            }
        }
        val canonical = appAliases[text]
        if (canonical != null) return JarvisCommand.OpenApp(canonical)
        return null
    }

    private fun parseSystemAccessibility(text: String): JarvisCommand? {
        if (matches(text, "take screenshot", "take a screenshot", "screenshot", "capture screen", "screen capture", "take screen shot", "screen shot")) {
            return JarvisCommand.TakeScreenshot
        }
        if (text == "notifications" || matches(text, "open notifications", "show notifications", "show notifications panel", "open notification panel", "expand notifications", "notification shade", "pull down notifications")) {
            return JarvisCommand.OpenNotificationsPanel
        }
        if (matches(text, "quick settings", "open quick settings", "show quick settings", "open quick settings panel")) {
            return JarvisCommand.OpenQuickSettings
        }
        if (matches(text, "power menu", "power dialog", "show power menu", "open power menu", "power options", "restart phone")) {
            return JarvisCommand.OpenPowerMenu
        }
        if (matches(text, "split screen", "toggle split screen", "enable split screen", "open split screen")) {
            return JarvisCommand.ToggleSplitScreen
        }
        return null
    }

    private fun parseSpokenNotifications(text: String): JarvisCommand? {
        if (matches(text, "read notifications", "read my notifications", "what are my notifications", "check notifications", "tell me my notifications", "any notifications")) {
            return JarvisCommand.ReadNotifications
        }
        if (matches(text, "read messages", "read my messages", "read whatsapp messages", "read whatsapp", "who messaged me", "any new messages", "check messages", "read unread messages")) {
            return JarvisCommand.ReadMessages
        }
        if (matches(text, "clear notifications", "clear my notifications", "dismiss notifications")) {
            return JarvisCommand.ClearNotifications
        }
        return null
    }

    private fun parseDeepMedia(text: String): JarvisCommand? {
        val spotifyRegex = Regex("^(?:play|listen to) (.+?) (?:on|in) spotify$")
        val spotifyMatch = spotifyRegex.find(text)
        if (spotifyMatch != null) {
            val query = spotifyMatch.groupValues[1].trim()
            if (query.isNotBlank()) return JarvisCommand.PlayOnSpotify(query)
        }

        val ytRegex = Regex("^(?:play|watch) (.+?) (?:on|in) (?:youtube|yt)$")
        val ytMatch = ytRegex.find(text)
        if (ytMatch != null) {
            val query = ytMatch.groupValues[1].trim()
            if (query.isNotBlank()) return JarvisCommand.PlayOnYouTube(query)
        }

        return null
    }

    private fun parseMemory(text: String): JarvisCommand? {
        val t = text.trim()

        // 1. User Name Query
        if (matches(t, "what is my name", "whats my name", "what s my name", "who am i",
                       "do you know my name", "tell me my name", "na peru enti", "na peru emiti",
                       "mera naam kya hai")) {
            return JarvisCommand.GetUserName
        }

        // 2. Clear All Memory
        if (matches(t, "clear memory", "forget everything", "reset memory", "clear all memory",
                       "memory clear cheyi", "sab bhool jao")) {
            return JarvisCommand.ClearMemory
        }

        // 3. Erase Specific Relationship (Voice & UI command)
        // English: "forget my bamardhi", "erase my brother", "remove bamardhi", "delete bamardhi"
        val eraseRegex = Regex("^(?:forget|erase|remove|delete) (?:my )?([a-z0-9 ]+)$")
        val eraseMatch = eraseRegex.find(t)
        if (eraseMatch != null) {
            val rel = eraseMatch.groupValues[1].trim()
            val systemExclusions = setOf("notifications", "messages", "alarm", "timer", "workflow", "chat mode", "app", "everything", "all")
            if (rel.isNotBlank() && rel !in systemExclusions) {
                return JarvisCommand.EraseRelationship(rel)
            }
        }

        // Telugu Erase: "బావమరిదిని మర్చిపో", "bamardhi ni marchipo", "bamardhi ni delete cheyi", "bamardhi memory delete cheyi"
        val telEraseRegex = Regex("^([a-z0-9\\u0C00-\\u0C7F ]+?) (?:ni marchipo|ni erase cheyi|ni delete cheyi|memory delete cheyi)$")
        val telEraseMatch = telEraseRegex.find(t)
        if (telEraseMatch != null) {
            val rel = telEraseMatch.groupValues[1].trim()
            if (rel.isNotBlank()) return JarvisCommand.EraseRelationship(rel)
        }

        // Hindi Erase: "bamardhi ko bhool jao", "bamardhi ko delete karo", "bamardhi ko erase karo"
        val hinEraseRegex = Regex("^([a-z0-9\\u0900-\\u097F ]+?) ko (?:bhool jao|delete karo|erase karo)$")
        val hinEraseMatch = hinEraseRegex.find(t)
        if (hinEraseMatch != null) {
            val rel = hinEraseMatch.groupValues[1].trim()
            if (rel.isNotBlank()) return JarvisCommand.EraseRelationship(rel)
        }

        // 4. Query Relationship: "who is my brother", "who is my bamardhi", "na bamardhi evaru", "mera bamardhi kaun hai"
        val relGetRegex = Regex("^(?:who is|who s) my ([a-z0-9 ]+)$")
        val relGetMatch = relGetRegex.find(t)
        if (relGetMatch != null) {
            val relation = relGetMatch.groupValues[1].trim()
            if (relation.isNotBlank() && relation != "name") {
                return JarvisCommand.GetRelationship(relation)
            }
        }

        val telGetRegex = Regex("^(?:na )?([a-z0-9\\u0C00-\\u0C7F ]+?) evaru$")
        val telGetMatch = telGetRegex.find(t)
        if (telGetMatch != null) {
            val relation = telGetMatch.groupValues[1].trim()
            if (relation.isNotBlank() && relation != "peru") {
                return JarvisCommand.GetRelationship(relation)
            }
        }

        val hinGetRegex = Regex("^(?:mera|meri) ([a-z0-9\\u0900-\\u097F ]+?) kaun hai$")
        val hinGetMatch = hinGetRegex.find(t)
        if (hinGetMatch != null) {
            val relation = hinGetMatch.groupValues[1].trim()
            if (relation.isNotBlank() && relation != "naam") {
                return JarvisCommand.GetRelationship(relation)
            }
        }

        // 5. Set Relationship: Universal relation matching!
        // English: "teja is my brother", "harsha jr is my bamardhi", "remember teja is my brother"
        val relSetRegex = Regex("^(?:remember (?:that )?)?([a-z0-9 ]+?) is my ([a-z0-9 ]+)$")
        val relSetMatch = relSetRegex.find(t)
        if (relSetMatch != null) {
            val contact = relSetMatch.groupValues[1].trim()
            val relation = relSetMatch.groupValues[2].trim()
            val invalidStarters = listOf("what", "who", "where", "when", "why", "how", "this", "that")
            val startsWithQuestion = invalidStarters.any { contact.startsWith(it) }
            if (!startsWithQuestion && contact.isNotBlank() && relation.isNotBlank() && relation != "name") {
                return JarvisCommand.RememberRelationship(relation, contact)
            }
        }

        // Telugu Set: "హర్ష జూనియర్ నా బావమరిది", "harsha jr na bamardhi", "teja na brother"
        val telSetRegex = Regex("^([a-z0-9\\u0C00-\\u0C7F ]+?) na ([a-z0-9\\u0C00-\\u0C7F ]+)$")
        val telSetMatch = telSetRegex.find(t)
        if (telSetMatch != null) {
            val contact = telSetMatch.groupValues[1].trim()
            val relation = telSetMatch.groupValues[2].trim()
            if (contact.isNotBlank() && relation.isNotBlank() && relation != "peru") {
                return JarvisCommand.RememberRelationship(relation, contact)
            }
        }

        // Hindi Set: "harsha jr mera bamardhi hai", "teja mera bhai hai"
        val hinSetRegex = Regex("^([a-z0-9\\u0900-\\u097F ]+?) (?:mera|meri) ([a-z0-9\\u0900-\\u097F ]+?)(?: hai)?$")
        val hinSetMatch = hinSetRegex.find(t)
        if (hinSetMatch != null) {
            val contact = hinSetMatch.groupValues[1].trim()
            val relation = hinSetMatch.groupValues[2].trim()
            if (contact.isNotBlank() && relation.isNotBlank() && relation != "naam") {
                return JarvisCommand.RememberRelationship(relation, contact)
            }
        }

        // 6. User Name Setting
        val nameRegex = Regex("^(?:remember (?:that )?my name is |my name is |call me )([a-z0-9 ]+)$")
        val nameMatch = nameRegex.find(t)
        if (nameMatch != null) {
            val name = nameMatch.groupValues[1].trim()
            if (name.isNotBlank() && name != "back" && name != "home" && !name.contains("on speaker") && !name.contains("whatsapp")) {
                return JarvisCommand.RememberUserName(name)
            }
        }

        val telNameRegex = Regex("^(?:na peru )([a-z0-9\\u0C00-\\u0C7F ]+)$")
        val telNameMatch = telNameRegex.find(t)
        if (telNameMatch != null) {
            val name = telNameMatch.groupValues[1].trim()
            if (name.isNotBlank()) return JarvisCommand.RememberUserName(name)
        }

        val hinNameRegex = Regex("^(?:mera naam )([a-z0-9\\u0900-\\u097F ]+?)(?: hai)?$")
        val hinNameMatch = hinNameRegex.find(t)
        if (hinNameMatch != null) {
            val name = hinNameMatch.groupValues[1].trim()
            if (name.isNotBlank()) return JarvisCommand.RememberUserName(name)
        }

        return null
    }

    private fun parseConversational(text: String): JarvisCommand? {
        if (matches(text, "status report", "system diagnostics", "diagnostics", "system status", "run diagnostics", "diagnostic report")) {
            return JarvisCommand.SystemDiagnostics
        }
        if (matches(text, "who are you", "what are you", "what is your name", "whats your name")) {
            return JarvisCommand.Conversational("I'm Blue, your personal AI assistant! How can I help you today?")
        }
        if (matches(text, "how are you", "how are you doing", "how r u", "how do you feel")) {
            return JarvisCommand.Conversational("I'm doing wonderful! Ready and excited to help you. What's on your mind?")
        }
        if (matches(text, "tell me a joke", "make me laugh", "say something funny")) {
            return JarvisCommand.Conversational("Why don't skeletons fight each other? Because they don't have the guts!")
        }
        if (matches(text, "thank you", "thanks", "thank you blue", "thanks blue", "thank you jarvis", "thanks jarvis", "good job", "well done", "good work", "appreciate it")) {
            return JarvisCommand.Conversational("You're so welcome! Always here for you!")
        }
        if (text == "hello" || text == "hi" || text == "hey" || text == "hello blue" || text == "hi blue" || text == "hey blue" || text == "hello jarvis" || text == "good day") {
            return JarvisCommand.Conversational("Hey there! I'm listening, what can I do for you?")
        }
        return null
    }

    private fun parseKnowledgeAndAi(text: String): JarvisCommand? {
        // Explicit AI ask: "ask ai ...", "ask chatgpt ...", "ask blue ..."
        if (text.startsWith("ask ai ") || text.startsWith("ask chatgpt ") || text.startsWith("ask blue ")) {
            val q = text.removePrefix("ask ai ").removePrefix("ask chatgpt ").removePrefix("ask blue ").trim()
            if (q.isNotBlank()) return JarvisCommand.AskAi(q)
        }

        // Definition queries: "define atmosphere", "meaning of photosynthesis"
        if (text.startsWith("define ")) {
            val q = text.removePrefix("define ").trim()
            if (q.isNotBlank()) return JarvisCommand.AskAi("Define $q")
        }
        if (text.startsWith("meaning of ") || text.startsWith("what is the meaning of ")) {
            val q = text.removePrefix("what is the meaning of ").removePrefix("meaning of ").trim()
            if (q.isNotBlank()) return JarvisCommand.AskAi("What is the meaning of $q")
        }

        // Explanations: "explain quantum computing", "explain how airplanes fly"
        if (text.startsWith("explain ")) {
            val q = text.removePrefix("explain ").trim()
            if (q.isNotBlank()) return JarvisCommand.AskAi("Explain $q")
        }

        // "Why is ...", "Why do ...", "Why does ...", "Why are ..."
        if (text.startsWith("why is ") || text.startsWith("why do ") || text.startsWith("why does ") || text.startsWith("why are ")) {
            return JarvisCommand.AskAi(text)
        }

        // "How does ... work", "How do ... work", "How to ..."
        if (text.startsWith("how does ") || text.startsWith("how do ") || text.startsWith("how to ") || text.startsWith("how can i ")) {
            return JarvisCommand.AskAi(text)
        }

        // "Tell me about ..."
        if (text.startsWith("tell me about ")) {
            val q = text.removePrefix("tell me about ").trim()
            if (q.isNotBlank()) return JarvisCommand.AskAi("Tell me about $q")
        }

        // "What is ...", "What are ..." (excluding device status queries)
        if (text.startsWith("what is ") || text.startsWith("what are ") || text.startsWith("whats ")) {
            val sub = text.removePrefix("what is ").removePrefix("what are ").removePrefix("whats ").trim()
            val deviceQueries = setOf(
                "my battery", "the battery", "battery", "my name", "the time", "time",
                "the date", "today", "todays date", "my brother", "my sister", "my mom",
                "my mother", "my dad", "my father", "my notifications", "my messages",
                "up", "going on"
            )
            if (sub !in deviceQueries) {
                return JarvisCommand.AskAi(text)
            }
        }

        // "Who is ..." (excluding "who is my brother/sister/etc." and "who are you")
        if (text.startsWith("who is ") || text.startsWith("whos ")) {
            val sub = text.removePrefix("who is ").removePrefix("whos ").trim()
            if (!sub.startsWith("my ")) {
                return JarvisCommand.AskAi(text)
            }
        }

        return null
    }

    private fun parseTelugu(text: String): JarvisCommand? {
        val t = text.trim()

        // 1. Flashlight
        if (matches(t, "టార్చ్ ఆన్", "టార్చ్ ఆన్ చేయి", "లైట్ ఆన్ చేయి", "లైట్ ఆన్", "టార్చ్ లైట్ ఆన్",
                       "torch on cheyi", "light on cheyi", "torch light on cheyi")) {
            return JarvisCommand.FlashlightOn
        }
        if (matches(t, "టార్చ్ ఆఫ్", "టార్చ్ ఆఫ్ చేయి", "లైట్ ఆఫ్ చేయి", "లైట్ ఆఫ్", "టార్చ్ లైట్ ఆఫ్",
                       "torch off cheyi", "light off cheyi", "light aapeyi", "torch light off cheyi")) {
            return JarvisCommand.FlashlightOff
        }
        if (t == "టార్చ్" || t == "టార్చ్ లైట్") {
            return JarvisCommand.FlashlightToggle
        }

        // 2. Battery / Time / Date
        if (matches(t, "బ్యాటరీ ఎంత ఉంది", "బ్యాటరీ ఎంత", "బ్యాటరీ శాతం ఎంత", "నా బ్యాటరీ ఎంత", "బ్యాటరీ శాతం",
                       "battery entha undi", "battery percentage cheppu", "battery entha")) {
            return JarvisCommand.GetBattery
        }
        if (matches(t, "సమయం ఎంత", "సమయం ఎంత అయింది", "టైమ్ ఎంత", "టైం ఎంత",
                       "time entha", "samayam entha", "time entha ayyindi")) {
            return JarvisCommand.GetTime
        }
        if (matches(t, "ఈరోజు తేదీ ఎంత", "తేదీ ఎంత", "ఈరోజు డేట్ ఎంత", "తేది ఎంత",
                       "date entha", "eroju date entha", "eroju thedi entha")) {
            return JarvisCommand.GetDate
        }

        // 3. Volume
        if (matches(t, "వాల్యూమ్ పెంచు", "వాల్యూమ్ ఎక్కువ చేయి", "సౌండ్ పెంచు", "వాల్యూం పెంచు",
                       "volume penchu", "sound penchu")) {
            return JarvisCommand.VolumeUp
        }
        if (matches(t, "వాల్యూమ్ తగ్గించు", "వాల్యూమ్ తక్కువ చేయి", "సౌండ్ తగ్గించు", "వాల్యూం తగ్గించు",
                       "volume thagginchu", "sound thagginchu")) {
            return JarvisCommand.VolumeDown
        }
        if (matches(t, "మ్యూట్ చేయి", "సైలెంట్ లో పెట్టు", "silent lo pettu", "mute cheyi")) {
            return JarvisCommand.MuteMedia
        }

        // 4. Accessibility / Reels / System
        if (matches(t, "తరువాతి రీల్", "తరువాత రీల్", "నెక్స్ట్ రీల్", "next reel")) {
            return JarvisCommand.NextReel
        }
        if (matches(t, "కిందికి స్క్రోల్ చేయి", "scroll down")) {
            return JarvisCommand.ScrollDown
        }
        if (matches(t, "మునుపటి రీల్", "previous reel")) {
            return JarvisCommand.PreviousReel
        }
        if (matches(t, "పైకి స్క్రోల్ చేయి", "scroll up")) {
            return JarvisCommand.ScrollUp
        }
        if (matches(t, "లైక్ చేయి", "లైక్", "డబుల్ ట్యాప్ చేయి", "like cheyi")) {
            return JarvisCommand.LikeReel
        }
        if (matches(t, "స్క్రీన్‌షాట్ తీయి", "స్క్రీన్‌షాట్", "స్క్రీన్ షాట్", "screenshot theeyi", "screenshot thiyi")) {
            return JarvisCommand.TakeScreenshot
        }
        if (matches(t, "లాక్ చేయి", "ఫోన్ లాక్ చేయి", "స్క్రీన్ లాక్ చేయి", "lock cheyi")) {
            return JarvisCommand.LockScreen
        }
        if (matches(t, "హోమ్ స్క్రీన్", "హోమ్ కి వెళ్ళు", "హోమ్", "home ki vellu")) {
            return JarvisCommand.GoHome
        }
        if (matches(t, "వెనక్కి వెళ్ళు", "బ్యాక్ కి వెళ్ళు", "బ్యాక్", "back ki vellu")) {
            return JarvisCommand.GoBack
        }
        if (matches(t, "రీసెంట్ యాప్స్", "రీసెంట్స్", "recent apps open cheyi")) {
            return JarvisCommand.OpenRecents
        }
        if (matches(t, "నోటిఫికేషన్లు చదువు", "నా నోటిఫికేషన్లు", "నోటిఫికేషన్స్ చదువు", "notifications chaduvu")) {
            return JarvisCommand.ReadNotifications
        }

        // 5. In-call Actions
        if (matches(t, "కాల్ కట్ చేయి", "కాల్ కట్", "కాల్ ఎండ్ చేయి", "call cut cheyi", "call cut")) {
            return JarvisCommand.DeclineCall
        }
        if (matches(t, "కాల్ ఎత్తు", "కాల్ ఆన్సర్ చేయి", "call etthu", "call answer cheyi")) {
            return JarvisCommand.AnswerCall
        }

        // 6. Conversational Telugu
        if (matches(t, "నువ్వు ఎవరు", "నీ పేరేంటి", "నీ పేరు ఏమిటి", "nuvvu evaru", "nee peru enti")) {
            return JarvisCommand.Conversational("నేను బ్లూ, మీ వ్యక్తిగత ఏఐ అసిస్టెంట్! మీకు ఎలా సహాయపడగలను?")
        }
        if (matches(t, "ఎలా ఉన్నావు", "ఎలా ఉన్నారు", "బాగున్నావా", "ela unnav", "ela unnaru")) {
            return JarvisCommand.Conversational("నేను చాలా బాగున్నాను! మీరు ఎలా ఉన్నారు? మీకు ఏమి సహాయం కావాలి?")
        }
        if (matches(t, "హలో బ్లూ", "నమస్కారం", "హాయ్ బ్లూ", "నమస్తే", "namaskaram", "namaste")) {
            return JarvisCommand.Conversational("నమస్కారం! నేను వింటున్నాను, చెప్పండి.")
        }
        if (matches(t, "ధన్యవాదాలు", "థాంక్యూ", "చాలా థాంక్స్", "chala thanks", "dhanyavadhalu")) {
            return JarvisCommand.Conversational("పర్వాలేదండి, ఎల్లప్పుడూ మీ సేవలో ఉంటాను!")
        }

        // 7. Phone Calls with Contact Name
        val telCallSpeakerRegex = Regex("^(?:స్పీకర్ లో (.+?) కి కాల్ చేయి|(.+?) కి స్పీకర్ లో కాల్ చేయి|(.+?) ki speaker lo call cheyi)$")
        val telCallSpeakerMatch = telCallSpeakerRegex.find(t)
        if (telCallSpeakerMatch != null) {
            val name = telCallSpeakerMatch.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.trim() ?: ""
            if (name.isNotBlank()) return JarvisCommand.CallContact(name, onSpeaker = true)
        }

        val telCallRegex = Regex("^(?:(.+?) కి కాల్ చేయి|కాల్ చేయి (.+)|(.+?) ki call cheyi|(.+?) ki call kottu|call cheyi (.+))$")
        val telCallMatch = telCallRegex.find(t)
        if (telCallMatch != null) {
            val name = telCallMatch.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.trim() ?: ""
            if (name.isNotBlank()) return JarvisCommand.CallContact(name)
        }

        // 8. WhatsApp Messages / Open
        if (matches(t, "వాట్సాప్ ఓపెన్ చేయి", "వాట్సాప్", "whatsapp open cheyi")) {
            return JarvisCommand.WhatsAppOpen()
        }
        val telWaMsgRegex = Regex("^(?:(.+?) కి వాట్సాప్ చేయి (.+)|(.+?) ki whatsapp cheyi (.+))$")
        val telWaMatch = telWaMsgRegex.find(t)
        if (telWaMatch != null) {
            val name = telWaMatch.groupValues[1].ifBlank { telWaMatch.groupValues[3] }.trim()
            val msg = telWaMatch.groupValues[2].ifBlank { telWaMatch.groupValues[4] }.trim()
            if (name.isNotBlank() && msg.isNotBlank()) return JarvisCommand.WhatsAppMessage(name, msg)
        }

        // 9. Generic Open App in Telugu
        val openAppRegex = Regex("^(?:(.+?) ఓపెన్ చేయి|(.+?) open cheyi)$")
        val openAppMatch = openAppRegex.find(t)
        if (openAppMatch != null) {
            val rawName = openAppMatch.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.trim() ?: ""
            val canonical = appAliases[rawName] ?: rawName
            if (canonical.isNotBlank()) return JarvisCommand.OpenApp(canonical)
        }

        // 10. Telugu Questions / Knowledge Engine Queries
        // If text contains Telugu script (\u0C00..\u0C7F)
        if (t.any { it in '\u0C00'..'\u0C7F' }) {
            return JarvisCommand.AskAi(text)
        }

        // Teluglish Question triggers
        val teluglishQuestionWords = listOf("ante enti", "ante emiti", "gurinchi cheppu", "enduku", "ela pani chesthundi", "evaru", "ekkadundi")
        if (teluglishQuestionWords.any { t.contains(it) }) {
            return JarvisCommand.AskAi(text)
        }

        return null
    }

    private fun parseTranslation(text: String): JarvisCommand.Translate? {
        val t = text.trim()

        // 1. "translate this lambadi to telugu or english" / "translate this [source] to [target]"
        val pattern1 = Regex("^(?:translate this|translate) (?:from )?(lambadi|gorboli|telugu|hindi|english)? *(?:to|into) (telugu or english|telugu and english|telugu|english|hindi|lambadi|gorboli)(?: saying| that| colon|:)? *(.*)$")
        val m1 = pattern1.find(t)
        if (m1 != null) {
            val src = m1.groupValues[1].ifBlank { "auto" }
            val tgt = m1.groupValues[2].trim()
            val content = m1.groupValues[3].trim()
            return JarvisCommand.Translate(text = content, targetLanguage = tgt, sourceLanguage = src)
        }

        // 2. "translate [content] to [target]"
        val pattern2 = Regex("^translate (.+?) (?:to|into) (telugu|english|hindi|lambadi|gorboli)$")
        val m2 = pattern2.find(t)
        if (m2 != null) {
            val content = m2.groupValues[1].trim()
            val tgt = m2.groupValues[2].trim()
            if (content.isNotBlank()) {
                return JarvisCommand.Translate(text = content, targetLanguage = tgt)
            }
        }

        // 3. "translate to [target] [content]"
        val pattern3 = Regex("^translate (?:to|into) (telugu|english|hindi|lambadi|gorboli) (.+)$")
        val m3 = pattern3.find(t)
        if (m3 != null) {
            val tgt = m3.groupValues[1].trim()
            val content = m3.groupValues[2].trim()
            if (content.isNotBlank()) {
                return JarvisCommand.Translate(text = content, targetLanguage = tgt)
            }
        }

        // 4. Telugu syntax
        if (t.contains("అనువదించు") || t.contains("translate cheyi")) {
            val tgt = when {
                t.contains("తెలుగు") || t.contains("telugu") -> "telugu"
                t.contains("ఇంగ్లీష్") || t.contains("english") -> "english"
                t.contains("హిందీ") || t.contains("hindi") -> "hindi"
                t.contains("లంబాడీ") || t.contains("lambadi") -> "lambadi"
                else -> "telugu"
            }
            val src = if (t.contains("లంబాడీ") || t.contains("lambadi")) "lambadi" else "auto"
            val stripped = t.replace("అనువదించు", "")
                .replace("translate cheyi", "")
                .replace("దీన్ని", "")
                .replace("తెలుగులోకి", "")
                .replace("ఇంగ్లీషులోకి", "")
                .replace("హిందీలోకి", "")
                .replace("లంబాడీలోకి", "")
                .replace("నుండి", "")
                .trim()
            return JarvisCommand.Translate(text = stripped, targetLanguage = tgt, sourceLanguage = src)
        }

        // 5. Hindi syntax
        if (t.contains("translate karo") || t.contains("anuvad karo")) {
            val tgt = when {
                t.contains("hindi") || t.contains("हिंदी") -> "hindi"
                t.contains("telugu") || t.contains("तेलुगु") -> "telugu"
                t.contains("english") || t.contains("अंग्रेजी") -> "english"
                t.contains("lambadi") || t.contains("लम्बाडी") -> "lambadi"
                else -> "hindi"
            }
            val src = if (t.contains("lambadi") || t.contains("लम्बाडी")) "lambadi" else "auto"
            val stripped = t.replace("translate karo", "")
                .replace("anuvad karo", "")
                .replace("isko", "")
                .replace("me", "")
                .replace("mein", "")
                .replace("hindi", "")
                .replace("telugu", "")
                .replace("english", "")
                .replace("lambadi", "")
                .trim()
            return JarvisCommand.Translate(text = stripped, targetLanguage = tgt, sourceLanguage = src)
        }

        return null
    }

    private fun parseLookAtScreen(text: String): JarvisCommand.LookAtScreen? {
        val t = text.trim()
        val phrases = listOf(
            "look at my screen", "look at screen", "what is on my screen", "whats on my screen",
            "what s on my screen", "explain my screen", "explain this screen", "read my screen",
            "read screen", "summarize screen", "screen summary",
            // Telugu
            "screen choodu", "నా స్క్రీన్ చూడు", "స్క్రీన్ చూడు", "స్క్రీన్ లో ఏముంది",
            "screen lo emundi", "screen chaduvu", "స్క్రీన్ చదువు",
            // Hindi
            "meri screen dekho", "screen dekho", "screen par kya hai", "screen padho"
        )
        if (matches(t, *phrases.toTypedArray())) {
            return JarvisCommand.LookAtScreen
        }
        return null
    }

    private fun parseChatMode(text: String): JarvisCommand.ContinuousChatMode? {
        val t = text.trim()
        val onPhrases = listOf(
            "start chat mode", "enable chat mode", "chat mode on", "continuous chat mode",
            "continuous mode on", "voice chat mode", "start voice chat", "chat with me",
            "let s chat", "lets chat", "talk to me", "continuous chat",
            // Telugu
            "చాట్ మోడ్ ఆన్ చేయి", "మాట్లాడుతూ ఉండు", "chat mode on cheyi", "chat mode on",
            // Hindi
            "chat mode on karo", "baat karte raho", "chat mode chalu karo"
        )
        if (matches(t, *onPhrases.toTypedArray())) {
            return JarvisCommand.ContinuousChatMode(enabled = true)
        }

        val offPhrases = listOf(
            "stop chat mode", "exit chat mode", "disable chat mode", "chat mode off",
            "stop chatting", "end chat mode", "turn off chat mode", "continuous mode off",
            // Telugu
            "చాట్ మోడ్ ఆఫ్ చేయి", "చాట్ మోడ్ ఆపు", "chat mode off cheyi", "chat aapeyi",
            // Hindi
            "chat mode band karo", "chat band karo", "chat mode off karo"
        )
        if (matches(t, *offPhrases.toTypedArray())) {
            return JarvisCommand.ContinuousChatMode(enabled = false)
        }

        return null
    }

    private fun parseWorkflow(text: String): JarvisCommand? {
        val t = text.trim()
        // 1. Execute workflow: "run workflow night routine" / "start workflow gaming" / "execute workflow [name]"
        val execRegex = Regex("^(?:run|start|execute) workflow ([a-z0-9 ]+)$")
        val execMatch = execRegex.find(t)
        if (execMatch != null) {
            val name = execMatch.groupValues[1].trim()
            if (name.isNotBlank()) return JarvisCommand.ExecuteWorkflow(name)
        }

        // 2. Create workflow: "create workflow [name] steps [step1, step2, ...]"
        val createRegex = Regex("^(?:create|make|save) workflow ([a-z0-9 ]+?) (?:steps|actions|with) (.+)$")
        val createMatch = createRegex.find(t)
        if (createMatch != null) {
            val name = createMatch.groupValues[1].trim()
            val rawSteps = createMatch.groupValues[2].trim()
            val steps = rawSteps.split(Regex("[,;]| and then | then ")).map { it.trim() }.filter { it.isNotBlank() }
            if (name.isNotBlank() && steps.isNotEmpty()) {
                return JarvisCommand.CreateWorkflow(name, steps)
            }
        }
        return null
    }

    private fun parseHindi(text: String): JarvisCommand? {
        val t = text.trim()

        // 1. Flashlight
        if (matches(t, "torch on karo", "flashlight on karo", "torch jalao", "light on karo",
                       "torch on", "टॉर्च ऑन करो", "टॉर्च जलाओ", "टॉर्च ऑन")) {
            return JarvisCommand.FlashlightOn
        }
        if (matches(t, "torch off karo", "flashlight off karo", "torch band karo", "light off karo",
                       "torch off", "टॉर्च ऑफ करो", "टॉर्च बंद करो", "टॉर्च ऑफ")) {
            return JarvisCommand.FlashlightOff
        }
        if (matches(t, "torch", "टॉर्च")) {
            return JarvisCommand.FlashlightToggle
        }

        // 2. Battery / Time / Date
        if (matches(t, "battery kitni hai", "battery percentage kya hai", "battery check karo",
                       "battery kitna hai", "बैटरी कितनी है", "बैटरी प्रतिशत")) {
            return JarvisCommand.GetBattery
        }
        if (matches(t, "samay kya hai", "time kya hai", "kitne baje hain", "time batao",
                       "samay batao", "समय क्या है", "टाइम क्या है", "कितने बजे हैं")) {
            return JarvisCommand.GetTime
        }
        if (matches(t, "aaj ki date kya hai", "aaj konsi date hai", "aaj ki tarikh kya hai",
                       "tarikh kya hai", "आज की तारीख क्या है", "आज कौन सी तारीख है")) {
            return JarvisCommand.GetDate
        }

        // 3. Volume
        if (matches(t, "volume badhao", "sound badhao", "aawaz badhao", "वॉल्यूम बढ़ाओ", "आवाज बढ़ाओ")) {
            return JarvisCommand.VolumeUp
        }
        if (matches(t, "volume kam karo", "sound kam karo", "aawaz kam karo", "वॉल्यूम कम करो", "आवाज कम करो")) {
            return JarvisCommand.VolumeDown
        }
        if (matches(t, "mute karo", "silent karo", "aawaz band karo", "म्यूट करो", "शांत करो")) {
            return JarvisCommand.MuteMedia
        }

        // 4. Reels
        if (matches(t, "agli reel", "next reel", "agla video", "agli video", "अगली रील")) {
            return JarvisCommand.NextReel
        }
        if (matches(t, "pichli reel", "previous reel", "pichla video", "पिछली रील")) {
            return JarvisCommand.PreviousReel
        }
        if (matches(t, "like karo", "video like karo", "लाइक करो")) {
            return JarvisCommand.LikeReel
        }

        // 5. System / Navigation
        if (matches(t, "screenshot lo", "screenshot kheencho", "स्क्रीनशॉट लो", "स्क्रीनशॉट खींचो")) {
            return JarvisCommand.TakeScreenshot
        }
        if (matches(t, "phone lock karo", "screen lock karo", "lock karo", "फोन लॉक करो", "स्क्रीन लॉक करो")) {
            return JarvisCommand.LockScreen
        }
        if (matches(t, "home jao", "home screen par jao", "होम स्क्रीन पर जाओ", "होम जाओ")) {
            return JarvisCommand.GoHome
        }
        if (matches(t, "peeche jao", "back jao", "पीछे जाओ", "बैक जाओ")) {
            return JarvisCommand.GoBack
        }
        if (matches(t, "recent apps kholo", "recent apps", "रीसेंट ऐप्स")) {
            return JarvisCommand.OpenRecents
        }
        if (matches(t, "notifications padho", "mere messages padho", "notifications sunao", "नोटिफिकेशन पढ़ो")) {
            return JarvisCommand.ReadNotifications
        }

        // 6. Calls
        if (matches(t, "call uthao", "call receive karo", "phone uthao", "कॉल उठाओ", "फोन उठाओ")) {
            return JarvisCommand.AnswerCall
        }
        if (matches(t, "call kaato", "phone kaato", "call reject karo", "कॉल काटो", "फोन काटो")) {
            return JarvisCommand.DeclineCall
        }
        if (matches(t, "call silent karo", "ringer band karo", "शांत करो")) {
            return JarvisCommand.SilenceIncomingCall
        }

        val hindiCallSpeakerRegex = Regex("^(?:speaker par (.+?) ko call karo|(.+?) ko speaker par call karo)$")
        val mSpeaker = hindiCallSpeakerRegex.find(t)
        if (mSpeaker != null) {
            val name = mSpeaker.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.trim() ?: ""
            if (name.isNotBlank()) return JarvisCommand.CallContact(name, onSpeaker = true)
        }

        val hindiCallRegex = Regex("^(?:(.+?) ko call karo|call karo (.+)|(.+?) ko phone lagao|phone lagao (.+))$")
        val mCall = hindiCallRegex.find(t)
        if (mCall != null) {
            val name = mCall.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.trim() ?: ""
            if (name.isNotBlank()) return JarvisCommand.CallContact(name)
        }

        // 7. WhatsApp
        if (matches(t, "whatsapp kholo", "व्हाट्सएप खोलो", "whatsapp open karo")) {
            return JarvisCommand.WhatsAppOpen()
        }
        val hindiWaRegex = Regex("^(?:(.+?) ko whatsapp karo (.+)|(.+?) ko whatsapp par message bhejo (.+))$")
        val mWa = hindiWaRegex.find(t)
        if (mWa != null) {
            val name = mWa.groupValues[1].ifBlank { mWa.groupValues[3] }.trim()
            val msg = mWa.groupValues[2].ifBlank { mWa.groupValues[4] }.trim()
            if (name.isNotBlank() && msg.isNotBlank()) return JarvisCommand.WhatsAppMessage(name, msg)
        }

        // 8. Conversational Hindi
        if (matches(t, "aap kaun ho", "tum kaun ho", "aapka naam kya hai", "tera naam kya hai", "आप कौन हो", "आपका नाम क्या है")) {
            return JarvisCommand.Conversational("Main Blue hoon, aapki personal AI assistant! Main aapki kya madad kar sakti hoon?")
        }
        if (matches(t, "kaise ho", "kya haal hai", "aap kaise hain", "kya chal raha hai", "कैसे हो", "क्या हाल है")) {
            return JarvisCommand.Conversational("Main bahut acchi hoon! Aap bataiye, main aapki kya madad kar sakti hoon?")
        }
        if (matches(t, "namaste", "namaskar", "pranam", "नमस्ते", "नमस्कार", "प्रणाम")) {
            return JarvisCommand.Conversational("Namaste! Main sun rahi hoon, kahiye kya seva karoon?")
        }
        if (matches(t, "shukriya", "dhanyavaad", "bahut shukriya", "शुक्रिया", "धन्यवाद")) {
            return JarvisCommand.Conversational("Aapka swagat hai! Main hamesha aapki seva ke liye hazir hoon.")
        }

        // 9. Generic Open App in Hindi
        val openAppRegex = Regex("^(?:(.+?) kholo|(.+?) open karo)$")
        val mApp = openAppRegex.find(t)
        if (mApp != null) {
            val rawName = mApp.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.trim() ?: ""
            val canonical = appAliases[rawName] ?: rawName
            if (canonical.isNotBlank()) return JarvisCommand.OpenApp(canonical)
        }

        // 10. Hindi Devanagari script query fallback -> AskAi
        if (t.any { it in '\u0900'..'\u097F' }) {
            return JarvisCommand.AskAi(text)
        }

        return null
    }

    private fun parseLambadi(text: String): JarvisCommand? {
        val t = text.trim()

        // 1. Greetings & Conversational
        if (matches(t, "ram ram", "ram ram blue", "ram ram jarvis", "ram ram ji")) {
            return JarvisCommand.Conversational("Ram Ram! Main Blue chhu, thaar personal AI assistant. Kai madat karu?")
        }
        if (matches(t, "tharo naam kai che", "tharo naam kai cha", "tharu naam kai che", "tharo naam kai")) {
            return JarvisCommand.Conversational("Maro naam Blue che, thaar personal AI assistant!")
        }
        if (matches(t, "kasa cha", "kaso che", "kasa cho", "kasa chho", "kaso cha")) {
            return JarvisCommand.Conversational("Main sokhi chhu! Tame kasa cha? Mane bolo kai kaam che.")
        }
        if (matches(t, "kayi kare che", "kai kare che", "kai karri che", "kai kare cha")) {
            return JarvisCommand.Conversational("Main thaar saaru taiyyar chhu, bolo kai karu!")
        }
        if (matches(t, "dhanvaad", "dhanyavaad")) {
            return JarvisCommand.Conversational("Dhanvaad! Main hamesh thaar seva ma chhu.")
        }

        // 2. Flashlight
        if (matches(t, "light laga", "torch laga", "batti jala", "batti laga", "light on kar")) {
            return JarvisCommand.FlashlightOn
        }
        if (matches(t, "light bujha", "torch bujha", "batti bujha", "batti band kar", "light off kar")) {
            return JarvisCommand.FlashlightOff
        }

        // 3. Calls: "[Name] ne phone laga" / "[Name] ne call kar"
        val lamCallRegex = Regex("^(.+?) ne (?:phone laga|call kar|call koro)$")
        val mCall = lamCallRegex.find(t)
        if (mCall != null) {
            val name = mCall.groupValues[1].trim()
            if (name.isNotBlank()) return JarvisCommand.CallContact(name)
        }

        // 4. Lambadi questions / queries -> AskAi
        if (t.contains("kai che") || t.contains("kasa cha") || t.contains("tharo") || t.contains("maro")) {
            return JarvisCommand.AskAi(text)
        }

        return null
    }

    fun knownAppNames(): Set<String> = appAliases.keys
}
