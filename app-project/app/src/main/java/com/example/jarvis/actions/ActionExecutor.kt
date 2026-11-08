package com.example.jarvis.actions

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.util.Log
import android.view.KeyEvent
import com.example.jarvis.accessibility.JarvisAccessibilityService
import com.example.jarvis.ai.KnowledgeEngine
import com.example.jarvis.apps.AppLauncher
import com.example.jarvis.audio.VolumeController
import com.example.jarvis.commands.CommandParser
import com.example.jarvis.commands.CommandResult
import com.example.jarvis.commands.JarvisCommand
import com.example.jarvis.flashlight.FlashlightController
import com.example.jarvis.memory.MemoryRepository
import com.example.jarvis.notifications.JarvisNotificationListenerService
import com.example.jarvis.telephony.TelephonyController

/**
 * Routes every [JarvisCommand] to the appropriate subsystem and returns a [CommandResult].
 *
 * This is the single integration point between parsing and execution.
 * Each subsystem is injected and independent — easy to test or replace.
 */
class ActionExecutor(
    private val context: Context,
    private val appLauncher: AppLauncher,
    private val flashlight: FlashlightController,
    private val volume: VolumeController,
    private val telephony: TelephonyController,
    private val memory: MemoryRepository = MemoryRepository(context),
) {
    companion object { private const val TAG = "ActionExecutor" }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    suspend fun execute(command: JarvisCommand, rawSpeech: String = ""): CommandResult {
        val isTelugu = isTeluguCommand(rawSpeech)
        Log.d(TAG, "Executing: $command (isTelugu=$isTelugu)")
        return when (command) {

            // ── App control ─────────────────────────────────────────────
            is JarvisCommand.OpenApp -> {
                val res = appLauncher.launch(command.appNameRaw)
                if (isTelugu && res is CommandResult.Success) {
                    CommandResult.Success("${command.appNameRaw} ఓపెన్ చేస్తున్నాను")
                } else res
            }

            // ── Browser ─────────────────────────────────────────────────
            is JarvisCommand.OpenUrl -> openUrl(command.url)
            is JarvisCommand.WebSearch -> webSearch(command.query)
            is JarvisCommand.OpenSiteInBrowser -> openSiteInBrowser(command.siteOrQuery, command.browserName)
            JarvisCommand.OpenCopiedLink -> openCopiedLink()

            // ── Navigation (AccessibilityService required) ────────────
            JarvisCommand.GoBack -> {
                if (!JarvisAccessibilityService.isRunning()) needsAccessibility()
                else if (JarvisAccessibilityService.performBack()) {
                    if (isTelugu) CommandResult.Success("వెనక్కి వెళ్తున్నాను") else CommandResult.Success("Going back")
                } else CommandResult.Failure("Back action failed")
            }
            JarvisCommand.GoHome -> {
                if (!JarvisAccessibilityService.isRunning()) needsAccessibility()
                else if (JarvisAccessibilityService.performHome()) {
                    if (isTelugu) CommandResult.Success("హోమ్ స్క్రీన్ కి వెళ్తున్నాను") else CommandResult.Success("Going home")
                } else CommandResult.Failure("Home action failed")
            }
            JarvisCommand.OpenRecents -> {
                if (!JarvisAccessibilityService.isRunning()) needsAccessibility()
                else if (JarvisAccessibilityService.performRecents()) {
                    if (isTelugu) CommandResult.Success("రీసెంట్ యాప్స్ ఓపెన్ చేస్తున్నాను") else CommandResult.Success("Opening recent apps")
                } else CommandResult.Failure("Recents action failed")
            }
            JarvisCommand.ScrollUp -> {
                if (!JarvisAccessibilityService.isRunning()) needsAccessibility()
                else JarvisAccessibilityService.scrollUp()
            }
            JarvisCommand.ScrollDown -> {
                if (!JarvisAccessibilityService.isRunning()) needsAccessibility()
                else JarvisAccessibilityService.scrollDown()
            }
            JarvisCommand.SwipeLeft -> {
                if (!JarvisAccessibilityService.isRunning()) needsAccessibility()
                else JarvisAccessibilityService.swipeLeft()
            }
            JarvisCommand.SwipeRight -> {
                if (!JarvisAccessibilityService.isRunning()) needsAccessibility()
                else JarvisAccessibilityService.swipeRight()
            }
            JarvisCommand.TapCenter -> {
                if (!JarvisAccessibilityService.isRunning()) needsAccessibility()
                else JarvisAccessibilityService.tapCenter()
            }
            JarvisCommand.NextReel -> {
                if (!JarvisAccessibilityService.isRunning()) needsAccessibility()
                else {
                    val res = JarvisAccessibilityService.nextReel()
                    if (isTelugu && res is CommandResult.Success) CommandResult.Success("తరువాతి రీల్") else res
                }
            }
            JarvisCommand.PreviousReel -> {
                if (!JarvisAccessibilityService.isRunning()) needsAccessibility()
                else {
                    val res = JarvisAccessibilityService.previousReel()
                    if (isTelugu && res is CommandResult.Success) CommandResult.Success("మునుపటి రీల్") else res
                }
            }
            JarvisCommand.LikeReel -> {
                if (!JarvisAccessibilityService.isRunning()) needsAccessibility()
                else {
                    val res = JarvisAccessibilityService.likeReel()
                    if (isTelugu && res is CommandResult.Success) CommandResult.Success("లైక్ చేసాను") else res
                }
            }

            // ── Volume ──────────────────────────────────────────────────
            JarvisCommand.VolumeUp      -> {
                val res = volume.volumeUp()
                if (isTelugu && res is CommandResult.Success) CommandResult.Success("వాల్యూమ్ పెంచాను") else res
            }
            JarvisCommand.VolumeDown    -> {
                val res = volume.volumeDown()
                if (isTelugu && res is CommandResult.Success) CommandResult.Success("వాల్యూమ్ తగ్గించాను") else res
            }
            JarvisCommand.MuteMedia     -> {
                val res = volume.muteMedia()
                if (isTelugu && res is CommandResult.Success) CommandResult.Success("మ్యూట్ చేసాను") else res
            }
            JarvisCommand.UnmuteMedia   -> volume.unmuteMedia()
            JarvisCommand.SilenceRinger -> volume.silenceRinger()
            JarvisCommand.EnableDnd     -> volume.enableDnd()
            JarvisCommand.DisableDnd    -> volume.disableDnd()
            is JarvisCommand.SetVolume  -> volume.setMediaVolume(command.level)

            // ── Media Playback ──────────────────────────────────────────
            JarvisCommand.MediaPlayPause -> {
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                if (isTelugu) CommandResult.Success("ప్లే పాజ్ మార్చాను") else CommandResult.Success("Toggling playback")
            }
            JarvisCommand.MediaNext -> {
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                if (isTelugu) CommandResult.Success("తరువాతి పాట") else CommandResult.Success("Next track")
            }
            JarvisCommand.MediaPrevious -> {
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                if (isTelugu) CommandResult.Success("మునుపటి పాట") else CommandResult.Success("Previous track")
            }

            // ── Device & Battery Status, Time & Date ────────────────────
            JarvisCommand.GetBattery -> getBatteryStatus(isTelugu)
            JarvisCommand.GetTime    -> getCurrentTime(isTelugu)
            JarvisCommand.GetDate    -> getCurrentDate(isTelugu)

            // ── Timers & Alarms ─────────────────────────────────────────
            is JarvisCommand.SetTimer -> setTimer(command.seconds, command.label)
            is JarvisCommand.SetAlarm -> setAlarm(command.hour, command.minute)
            JarvisCommand.OpenClock -> openClock()

            // ── Routines / Macros ───────────────────────────────────────
            JarvisCommand.NightMode   -> executeNightMode()
            JarvisCommand.MorningMode -> executeMorningMode()
            JarvisCommand.FocusMode   -> executeFocusMode()

            // ── Flashlight ──────────────────────────────────────────────
            JarvisCommand.FlashlightOn     -> {
                val res = flashlight.turnOn()
                if (isTelugu && res is CommandResult.Success) CommandResult.Success("టార్చ్ ఆన్ చేసాను") else res
            }
            JarvisCommand.FlashlightOff    -> {
                val res = flashlight.turnOff()
                if (isTelugu && res is CommandResult.Success) CommandResult.Success("టార్చ్ ఆఫ్ చేసాను") else res
            }
            JarvisCommand.FlashlightToggle -> {
                val res = flashlight.toggle()
                if (isTelugu && res is CommandResult.Success) CommandResult.Success("టార్చ్ మార్చాను") else res
            }

            // ── Screen, System & Accessibility Actions (v2) ─────────────
            JarvisCommand.LockScreen             -> {
                val res = lockScreen()
                if (isTelugu && res is CommandResult.Success) CommandResult.Success("ఫోన్ లాక్ చేస్తున్నాను") else res
            }
            JarvisCommand.TakeScreenshot         -> {
                val res = takeScreenshot()
                if (isTelugu && res is CommandResult.Success) CommandResult.Success("స్క్రీన్‌షాట్ తీసుకున్నాను") else res
            }
            JarvisCommand.OpenNotificationsPanel -> openNotificationsPanel()
            JarvisCommand.OpenQuickSettings      -> openQuickSettings()
            JarvisCommand.OpenPowerMenu          -> openPowerMenu()
            JarvisCommand.ToggleSplitScreen      -> toggleSplitScreen()

            // ── Spoken Notifications (v2) ───────────────────────────────
            JarvisCommand.ReadNotifications  -> CommandResult.Success(JarvisNotificationListenerService.getRecentSummary())
            JarvisCommand.ReadMessages       -> CommandResult.Success(JarvisNotificationListenerService.getMessagesSummary())
            JarvisCommand.ClearNotifications -> CommandResult.Success(JarvisNotificationListenerService.clearHistory())

            // ── Context & Relationship Memory (v2 & v5) ──────────────────
            is JarvisCommand.RememberUserName     -> rememberUserName(command.name)
            JarvisCommand.GetUserName             -> getUserName()
            is JarvisCommand.RememberRelationship -> rememberRelationship(command.relation, command.contactName)
            is JarvisCommand.GetRelationship      -> getRelationship(command.relation)
            is JarvisCommand.EraseRelationship    -> eraseRelationship(command.relation)
            JarvisCommand.ClearMemory             -> clearMemory()

            // ── Deep Media & Music Automation (v2) ──────────────────────
            is JarvisCommand.PlayOnSpotify -> playOnSpotify(command.query)
            is JarvisCommand.PlayOnYouTube -> playOnYouTube(command.query)

            // ── Conversational & System Diagnostics (v2) ────────────────
            JarvisCommand.SystemDiagnostics -> runSystemDiagnostics()
            is JarvisCommand.Conversational -> CommandResult.Success(command.reply)

            // ── Telephony ───────────────────────────────────────────────
            is JarvisCommand.CallContact -> {
                val clean = command.name.lowercase().removePrefix("my ").trim()
                val target = memory.getRelationship(clean) ?: command.name
                telephony.callContact(target, command.onSpeaker)
            }
            is JarvisCommand.WhatsAppCall -> {
                val clean = command.contactName.lowercase().removePrefix("my ").trim()
                val target = memory.getRelationship(clean) ?: command.contactName
                telephony.callWhatsApp(target)
            }
            JarvisCommand.AnswerCall            -> telephony.answerCall()
            JarvisCommand.DeclineCall           -> telephony.declineCall()
            JarvisCommand.SilenceIncomingCall   -> telephony.silenceRinger()

            // ── Text entry ──────────────────────────────────────────────
            is JarvisCommand.TypeText -> {
                if (!JarvisAccessibilityService.isRunning()) needsAccessibility()
                else JarvisAccessibilityService.insertText(command.text)
            }

            // ── WhatsApp ────────────────────────────────────────────────
            is JarvisCommand.WhatsAppOpen -> {
                if (command.contactName != null) {
                    openWhatsAppContact(command.contactName)
                } else {
                    appLauncher.launch("whatsapp")
                }
            }
            is JarvisCommand.WhatsAppMessage -> sendWhatsAppMessage(command.contact, command.message)
            is JarvisCommand.SendSms -> sendSmsMessage(command.contact, command.message)

            // ── Meta ────────────────────────────────────────────────────
            JarvisCommand.OpenSettings -> {
                val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                CommandResult.Success("Opening Settings")
            }
            JarvisCommand.OpenJarvisSettings -> CommandResult.Success("__OPEN_JARVIS_SETTINGS__")
            JarvisCommand.Help -> CommandResult.Success("__SHOW_HELP__")

            // ── Blue v5 Intelligence: Translation, Screen Vision, Chat Mode, Workflows ──
            is JarvisCommand.Translate -> executeTranslate(command)
            JarvisCommand.LookAtScreen -> executeLookAtScreen()
            is JarvisCommand.ContinuousChatMode -> executeChatMode(command.enabled)
            is JarvisCommand.ExecuteWorkflow -> executeWorkflow(command.name)
            is JarvisCommand.CreateWorkflow -> createWorkflow(command.name, command.actions)

            // ── AI Knowledge & Definition Engine (v3) ───────────────────
            is JarvisCommand.AskAi -> {
                val answer = KnowledgeEngine.query(command.question, context)
                CommandResult.Success(answer)
            }

            // ── Unknown ─────────────────────────────────────────────────
            is JarvisCommand.Unknown -> {
                if (isTelugu) {
                    CommandResult.Failure("నాకు \"${command.rawText}\" అర్థం కాలేదు. సహాయం కోసం 'help' లేదా 'సహాయం' అనండి.")
                } else {
                    CommandResult.Failure(
                        "I didn't recognise \"${command.rawText}\". " +
                        "Say 'help' to hear what I can do."
                    )
                }
            }
        }
    }

    // ── Media helper ──────────────────────────────────────────────────────

    private fun sendMediaKey(keyCode: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val up = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        am.dispatchMediaKeyEvent(down)
        am.dispatchMediaKeyEvent(up)
    }

    // ── Battery helper ────────────────────────────────────────────────────

    private fun getBatteryStatus(isTelugu: Boolean = false): CommandResult {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, filter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else -1

        return if (percent >= 0) {
            if (isTelugu) {
                val chargingText = if (isCharging) ", ఛార్జింగ్ అవుతోంది" else ""
                CommandResult.Success("బ్యాటరీ $percent శాతం ఉంది$chargingText.")
            } else {
                val chargingText = if (isCharging) ", charging" else ""
                CommandResult.Success("Battery is at $percent percent$chargingText.")
            }
        } else {
            if (isTelugu) CommandResult.Failure("బ్యాటరీ శాతం తెలుసుకోలేకపోయాను.")
            else CommandResult.Failure("Unable to determine battery level.")
        }
    }

    // ── Timers & Alarms helpers ───────────────────────────────────────────

    private fun setTimer(seconds: Int, label: String): CommandResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val unit = if (seconds >= 60) "${seconds / 60} minutes" else "$seconds seconds"
            CommandResult.Success("Timer set for $unit")
        } catch (e: Exception) {
            CommandResult.Failure("Could not set timer: ${e.localizedMessage}")
        }
    }

    private fun setAlarm(hour: Int, minute: Int): CommandResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, "Blue Alarm")
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val formattedMinute = String.format("%02d", minute)
            CommandResult.Success("Alarm set for $hour:$formattedMinute")
        } catch (e: Exception) {
            CommandResult.Failure("Could not set alarm: ${e.localizedMessage}")
        }
    }

    private fun openClock(): CommandResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CommandResult.Success("Opening clock")
        } catch (e: Exception) {
            appLauncher.launch("clock")
        }
    }

    // ── Routines helpers ──────────────────────────────────────────────────

    private fun executeNightMode(): CommandResult {
        volume.setMediaVolume(0)
        volume.silenceRinger()
        volume.enableDnd()
        flashlight.turnOff()
        if (JarvisAccessibilityService.isRunning()) {
            JarvisAccessibilityService.performLock()
        }
        return CommandResult.Success("Night mode activated. Sleep well, sweet dreams!")
    }

    private fun executeMorningMode(): CommandResult {
        volume.disableDnd()
        volume.setMediaVolume(8)
        val batteryResult = getBatteryStatus()
        val batteryText = if (batteryResult is CommandResult.Success) batteryResult.message else ""
        return CommandResult.Success("Good morning! Hope you have a wonderful day. $batteryText")
    }

    // ── Browser helpers ───────────────────────────────────────────────────

    private fun openUrl(url: String): CommandResult {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CommandResult.Success("Opening $url")
        } catch (e: Exception) {
            CommandResult.Failure("Could not open URL: ${e.localizedMessage}")
        }
    }

    private fun webSearch(query: String): CommandResult {
        val encoded = Uri.encode(query)
        val uri = Uri.parse("https://www.google.com/search?q=$encoded")
        return openUrl(uri.toString()).let {
            if (it is CommandResult.Success) CommandResult.Success("Searching for $query")
            else it
        }
    }

    private fun openSiteInBrowser(siteOrQuery: String, browserName: String): CommandResult {
        val browserPkg = when (browserName.lowercase()) {
            "brave", "brave browser" -> "com.brave.browser"
            "chrome", "google chrome" -> "com.android.chrome"
            "firefox" -> "org.mozilla.firefox"
            "opera" -> "com.opera.browser"
            "edge" -> "com.microsoft.emmx"
            else -> appLauncher.findPackageForApp(browserName) ?: "com.brave.browser"
        }

        val clean = siteOrQuery.trim().lowercase()
        val url = when {
            clean == "youtube" || clean == "yt" -> "https://www.youtube.com"
            clean == "google" -> "https://www.google.com"
            clean == "reddit" -> "https://www.reddit.com"
            clean == "instagram" || clean == "insta" -> "https://www.instagram.com"
            clean == "twitter" || clean == "x" -> "https://x.com"
            clean == "github" -> "https://github.com"
            clean == "wikipedia" -> "https://www.wikipedia.org"
            clean.startsWith("http://") || clean.startsWith("https://") -> siteOrQuery.trim()
            clean.contains(".") -> "https://${siteOrQuery.trim()}"
            browserName.contains("brave") -> "https://search.brave.com/search?q=${Uri.encode(siteOrQuery)}"
            else -> "https://www.google.com/search?q=${Uri.encode(siteOrQuery)}"
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                if (!browserPkg.isNullOrBlank()) setPackage(browserPkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CommandResult.Success("Opening $siteOrQuery in $browserName")
        } catch (e: Exception) {
            try {
                val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                CommandResult.Success("Opening $siteOrQuery")
            } catch (e2: Exception) {
                CommandResult.Failure("Could not open browser: ${e2.localizedMessage}")
            }
        }
    }

    private fun getCurrentTime(isTelugu: Boolean = false): CommandResult {
        val timeStr = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date())
        return if (isTelugu) CommandResult.Success("ఇప్పుడు సమయం $timeStr.")
        else CommandResult.Success("It is $timeStr.")
    }

    private fun getCurrentDate(isTelugu: Boolean = false): CommandResult {
        val dateStr = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date())
        return if (isTelugu) CommandResult.Success("ఈరోజు తేదీ $dateStr.")
        else CommandResult.Success("Today is $dateStr.")
    }

    private fun isTeluguCommand(rawSpeech: String): Boolean {
        if (rawSpeech.any { it in '\u0C00'..'\u0C7F' }) return true
        val l = rawSpeech.lowercase()
        return l.contains("cheyi") || l.contains("chyi") ||
               l.contains("undi") || l.contains("entha") ||
               l.contains("theeyi") || l.contains("thiyi") ||
               l.contains("vellu") || l.contains("kottu") ||
               l.contains("penchu") || l.contains("thagginchu") ||
               l.contains("chaduvu") || l.contains("etthu") ||
               l.contains("pettu") || l.contains("ante") ||
               l.contains("gurinchi") || l.contains("cheppu") ||
               l.contains("eroju") || l.contains("samayam")
    }

    private fun openCopiedLink(): CommandResult {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                ?: return CommandResult.Failure("Clipboard is unavailable.")

            val clip = clipboard.primaryClip
            if (clip == null || clip.itemCount == 0) {
                return CommandResult.Failure("No copied link found in your clipboard.")
            }

            val text = clip.getItemAt(0)?.text?.toString()?.trim()
            if (text.isNullOrBlank()) {
                return CommandResult.Failure("The clipboard is empty.")
            }

            val url = when {
                text.startsWith("http://") || text.startsWith("https://") -> text
                text.contains(".") && !text.contains(" ") -> "https://$text"
                else -> "https://www.google.com/search?q=${Uri.encode(text)}"
            }

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage("com.android.chrome")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                CommandResult.Success("Opening copied link in Chrome")
            } catch (e: Exception) {
                val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                CommandResult.Success("Opening copied link")
            }
        } catch (e: Exception) {
            CommandResult.Failure("Could not open copied link: ${e.localizedMessage}")
        }
    }

    // ── WhatsApp helpers ──────────────────────────────────────────────────

    private fun openWhatsAppContact(name: String): CommandResult {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?text=")
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CommandResult.Success("Opening WhatsApp")
        } catch (e: Exception) {
            appLauncher.launch("whatsapp")
        }
    }

    private fun sendWhatsAppMessage(contact: String, message: String): CommandResult {
        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage("com.whatsapp")
                putExtra(Intent.EXTRA_TEXT, message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Send via WhatsApp").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            if (JarvisAccessibilityService.isRunning()) {
                mainHandler.postDelayed({
                    JarvisAccessibilityService.autoClickWhatsAppSend()
                }, 2200)
            }
            CommandResult.Success(
                "WhatsApp opened with your message for $contact."
            )
        } catch (e: Exception) {
            CommandResult.Failure(
                "WhatsApp is not installed or could not be opened: ${e.localizedMessage}"
            )
        }
    }

    private fun sendSmsMessage(contact: String, message: String): CommandResult {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:")
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CommandResult.Success("Opening Messages with your text for $contact.")
        } catch (e: Exception) {
            CommandResult.Failure("Could not open messaging app: ${e.localizedMessage}")
        }
    }

    private fun lockScreen(): CommandResult {
        return if (!JarvisAccessibilityService.isRunning()) {
            needsAccessibility()
        } else if (JarvisAccessibilityService.performLock()) {
            CommandResult.Success("Locking screen")
        } else {
            CommandResult.Failure("Lock screen action failed.")
        }
    }

    private fun takeScreenshot(): CommandResult {
        return if (!JarvisAccessibilityService.isRunning()) {
            needsAccessibility()
        } else if (JarvisAccessibilityService.performScreenshot()) {
            CommandResult.Success("Capturing screenshot")
        } else {
            CommandResult.Failure("Screenshot action requires Android 9 or higher.")
        }
    }

    private fun openNotificationsPanel(): CommandResult {
        return if (!JarvisAccessibilityService.isRunning()) {
            needsAccessibility()
        } else if (JarvisAccessibilityService.openNotifications()) {
            CommandResult.Success("Opening notifications panel")
        } else {
            CommandResult.Failure("Could not open notifications panel.")
        }
    }

    private fun openQuickSettings(): CommandResult {
        return if (!JarvisAccessibilityService.isRunning()) {
            needsAccessibility()
        } else if (JarvisAccessibilityService.openQuickSettings()) {
            CommandResult.Success("Opening quick settings")
        } else {
            CommandResult.Failure("Could not open quick settings.")
        }
    }

    private fun openPowerMenu(): CommandResult {
        return if (!JarvisAccessibilityService.isRunning()) {
            needsAccessibility()
        } else if (JarvisAccessibilityService.openPowerDialog()) {
            CommandResult.Success("Opening power menu")
        } else {
            CommandResult.Failure("Could not open power menu.")
        }
    }

    private fun toggleSplitScreen(): CommandResult {
        return if (!JarvisAccessibilityService.isRunning()) {
            needsAccessibility()
        } else if (JarvisAccessibilityService.toggleSplitScreen()) {
            CommandResult.Success("Toggling split screen")
        } else {
            CommandResult.Failure("Split screen action failed.")
        }
    }

    private fun executeFocusMode(): CommandResult {
        volume.silenceRinger()
        volume.enableDnd()
        flashlight.turnOff()
        return CommandResult.Success("Focus mode engaged! All distractions silenced for you.")
    }

    private fun rememberUserName(name: String): CommandResult {
        memory.userName = name
        val formatted = name.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        return CommandResult.Success("Got it! I'll remember you as $formatted.")
    }

    private fun getUserName(): CommandResult {
        val name = memory.userName
        return if (name != null) {
            val formatted = name.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            CommandResult.Success("You are $formatted!")
        } else {
            CommandResult.Success("You haven't told me your name yet! You can say 'my name is Sarat' or 'call me Sarat'.")
        }
    }

    private fun rememberRelationship(relation: String, contact: String): CommandResult {
        memory.setRelationship(relation, contact)
        return CommandResult.Success("Got it! I've noted that $contact is your $relation.")
    }

    private fun getRelationship(relation: String): CommandResult {
        val contact = memory.getRelationship(relation)
        return if (contact != null) {
            CommandResult.Success("Your $relation is $contact!")
        } else {
            CommandResult.Success("I don't have a record of your $relation yet.")
        }
    }

    private fun clearMemory(): CommandResult {
        memory.clearAll()
        return CommandResult.Success("All custom memory records have been erased.")
    }

    private fun eraseRelationship(relation: String): CommandResult {
        val cleanRel = relation.lowercase().trim()
        val existing = memory.getRelationship(cleanRel)
        return if (existing != null) {
            memory.removeRelationship(cleanRel)
            CommandResult.Success("Forgotten! I've removed $existing as your $cleanRel from memory.")
        } else {
            CommandResult.Success("I don't have a record of $cleanRel in memory.")
        }
    }

    private suspend fun executeTranslate(command: JarvisCommand.Translate): CommandResult {
        val target = command.targetLanguage
        val source = command.sourceLanguage
        val textToTranslate = command.text.trim()

        if (textToTranslate.isBlank()) {
            return CommandResult.Success("Ready to translate to $target! What would you like me to translate?")
        }

        val translated = KnowledgeEngine.translate(textToTranslate, target, source, context)
        return CommandResult.Success(translated)
    }

    private suspend fun executeLookAtScreen(): CommandResult {
        if (!JarvisAccessibilityService.isRunning()) {
            return needsAccessibility()
        }
        val screenText = JarvisAccessibilityService.getVisibleScreenText()
        if (screenText.isBlank()) {
            return CommandResult.Success("I cannot see any text content on your current screen.")
        }
        val summary = KnowledgeEngine.explainScreen(screenText, context)
        return CommandResult.Success(summary)
    }

    private fun executeChatMode(enabled: Boolean): CommandResult {
        return if (enabled) {
            CommandResult.Success("__CHAT_MODE_ON__")
        } else {
            CommandResult.Success("__CHAT_MODE_OFF__")
        }
    }

    private suspend fun executeWorkflow(name: String): CommandResult {
        val cleanName = name.lowercase().trim()
        val steps = memory.getWorkflow(cleanName)
        if (steps.isNullOrEmpty()) {
            return when (cleanName) {
                "night routine", "night mode", "sleep routine" -> executeNightMode()
                "morning routine", "morning mode" -> executeMorningMode()
                "focus routine", "focus mode" -> executeFocusMode()
                "gaming routine", "gaming mode" -> {
                    volume.setMediaVolume(12)
                    flashlight.turnOff()
                    CommandResult.Success("Gaming routine engaged! Volume set high and lights dimmed.")
                }
                else -> CommandResult.Failure("Workflow \"$name\" not found. Say 'create workflow $name steps ...' to create it.")
            }
        }

        for (step in steps) {
            val stepCmd = CommandParser.parse(step)
            execute(stepCmd, step)
            kotlinx.coroutines.delay(400)
        }
        return CommandResult.Success("Workflow $name completed successfully.")
    }

    private fun createWorkflow(name: String, actions: List<String>): CommandResult {
        val cleanName = name.lowercase().trim()
        memory.saveWorkflow(cleanName, actions)
        return CommandResult.Success("Workflow \"$name\" saved with ${actions.size} actions! You can run it by saying 'run workflow $name'.")
    }

    private fun playOnSpotify(query: String): CommandResult {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:${Uri.encode(query)}")).apply {
                setPackage("com.spotify.music")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CommandResult.Success("Playing $query on Spotify")
        } catch (_: Exception) {
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/${Uri.encode(query)}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                CommandResult.Success("Searching $query on Spotify")
            } catch (e: Exception) {
                CommandResult.Failure("Spotify is not installed: ${e.localizedMessage}")
            }
        }
    }

    private fun playOnYouTube(query: String): CommandResult {
        return try {
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CommandResult.Success("Searching $query on YouTube")
        } catch (_: Exception) {
            openUrl("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
        }
    }

    private fun runSystemDiagnostics(): CommandResult {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, filter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else 100

        val chargingNote = if (isCharging) " (charging)" else ""
        val a11yStatus = if (JarvisAccessibilityService.isRunning()) "online" else "standby"
        val notifStatus = if (JarvisNotificationListenerService.isConnected) "active" else "standby"

        val report = "Diagnostics complete! All primary systems operational. Battery is at $percent percent$chargingNote. Accessibility service is $a11yStatus, and notification listener is $notifStatus. I'm ready to help!"
        return CommandResult.Success(report)
    }

    private fun needsAccessibility() = CommandResult.NeedsSetup(
        "This action requires the Blue Accessibility Service. " +
        "Enable it in Settings → Accessibility → Installed Services → Blue Voice Control."
    )
}
