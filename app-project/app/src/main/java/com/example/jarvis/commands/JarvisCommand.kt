package com.example.jarvis.commands

/**
 * Every voice command resolves to one of these intents.
 * Actions are executed by ActionExecutor which reads this sealed class.
 */
sealed class JarvisCommand {

    // ── App control ─────────────────────────────────────────────────────────
    data class OpenApp(val appNameRaw: String) : JarvisCommand()

    // ── Browser & Clipboard ──────────────────────────────────────────────────
    data class OpenUrl(val url: String) : JarvisCommand()
    data class WebSearch(val query: String) : JarvisCommand()
    data class OpenSiteInBrowser(val siteOrQuery: String, val browserName: String) : JarvisCommand()
    object OpenCopiedLink : JarvisCommand()

    // ── Screen / navigation ──────────────────────────────────────────────────
    object GoBack       : JarvisCommand()
    object GoHome       : JarvisCommand()
    object OpenRecents  : JarvisCommand()
    object ScrollUp     : JarvisCommand()
    object ScrollDown   : JarvisCommand()
    object SwipeLeft    : JarvisCommand()
    object SwipeRight   : JarvisCommand()
    object TapCenter    : JarvisCommand()

    // ── Reels / Shorts Short-Video Feed Gestures ──────────────────────────────
    object NextReel     : JarvisCommand()
    object PreviousReel : JarvisCommand()
    object LikeReel     : JarvisCommand()

    // ── Volume / audio ───────────────────────────────────────────────────────
    object VolumeUp        : JarvisCommand()
    object VolumeDown      : JarvisCommand()
    object MuteMedia       : JarvisCommand()
    object UnmuteMedia     : JarvisCommand()
    object SilenceRinger   : JarvisCommand()
    data class SetVolume(val level: Int) : JarvisCommand()   // 0-15
    object EnableDnd       : JarvisCommand()
    object DisableDnd      : JarvisCommand()

    // ── Media Playback ────────────────────────────────────────────────────────
    object MediaPlayPause  : JarvisCommand()
    object MediaNext       : JarvisCommand()
    object MediaPrevious   : JarvisCommand()

    // ── Device & Battery Status ───────────────────────────────────────────────
    object GetBattery      : JarvisCommand()
    object GetTime         : JarvisCommand()
    object GetDate         : JarvisCommand()

    // ── Clock & Timers ────────────────────────────────────────────────────────
    data class SetTimer(val seconds: Int, val label: String = "Blue Timer") : JarvisCommand()
    data class SetAlarm(val hour: Int, val minute: Int) : JarvisCommand()
    object OpenClock       : JarvisCommand()

    // ── Routines / Macros ─────────────────────────────────────────────────────
    object NightMode       : JarvisCommand()
    object MorningMode     : JarvisCommand()
    object FocusMode       : JarvisCommand()

    // ── Flashlight ───────────────────────────────────────────────────────────
    object FlashlightOn     : JarvisCommand()
    object FlashlightOff    : JarvisCommand()
    object FlashlightToggle : JarvisCommand()

    // ── Screen, System & Accessibility Actions ────────────────────────────────
    object LockScreen            : JarvisCommand()
    object TakeScreenshot        : JarvisCommand()
    object OpenNotificationsPanel: JarvisCommand()
    object OpenQuickSettings     : JarvisCommand()
    object OpenPowerMenu         : JarvisCommand()
    object ToggleSplitScreen     : JarvisCommand()

    // ── Spoken Notifications (v2) ─────────────────────────────────────────────
    object ReadNotifications     : JarvisCommand()
    object ReadMessages          : JarvisCommand()
    object ClearNotifications    : JarvisCommand()

    // ── Context & Relationship Memory (v2 & v5) ──────────────────────────────
    data class RememberUserName(val name: String) : JarvisCommand()
    object GetUserName : JarvisCommand()
    data class RememberRelationship(val relation: String, val contactName: String) : JarvisCommand()
    data class GetRelationship(val relation: String) : JarvisCommand()
    data class EraseRelationship(val relation: String) : JarvisCommand()
    object ClearMemory : JarvisCommand()

    // ── Deep Media & Music Automation (v2) ───────────────────────────────────
    data class PlayOnSpotify(val query: String) : JarvisCommand()
    data class PlayOnYouTube(val query: String) : JarvisCommand()

    // ── Telephony ────────────────────────────────────────────────────────────
    data class CallContact(val name: String, val onSpeaker: Boolean = false) : JarvisCommand()
    data class WhatsAppCall(val contactName: String)                         : JarvisCommand()
    object AnswerCall                                                        : JarvisCommand()
    object DeclineCall                                                       : JarvisCommand()
    object SilenceIncomingCall                                               : JarvisCommand()

    // ── Text entry ───────────────────────────────────────────────────────────
    data class TypeText(val text: String)       : JarvisCommand()

    // ── WhatsApp & Messaging ──────────────────────────────────────────────────
    data class WhatsAppOpen(val contactName: String? = null) : JarvisCommand()
    data class WhatsAppMessage(val contact: String, val message: String) : JarvisCommand()
    data class SendSms(val contact: String, val message: String) : JarvisCommand()

    // ── Conversational & System Diagnostics (v2) ──────────────────────────────
    object SystemDiagnostics : JarvisCommand()
    data class Conversational(val reply: String) : JarvisCommand()
    data class AskAi(val question: String) : JarvisCommand()

    // ── Blue v5 Intelligence, Translation, Vision & Workflows ─────────────────
    data class Translate(val text: String, val targetLanguage: String, val sourceLanguage: String = "auto") : JarvisCommand()
    object LookAtScreen : JarvisCommand()
    data class ContinuousChatMode(val enabled: Boolean) : JarvisCommand()
    data class ExecuteWorkflow(val name: String) : JarvisCommand()
    data class CreateWorkflow(val name: String, val actions: List<String>) : JarvisCommand()

    // ── Meta ─────────────────────────────────────────────────────────────────
    object OpenSettings    : JarvisCommand()
    object OpenJarvisSettings : JarvisCommand()
    object Help            : JarvisCommand()

    // ── Unknown / unmatched ──────────────────────────────────────────────────
    data class Unknown(val rawText: String) : JarvisCommand()
}
