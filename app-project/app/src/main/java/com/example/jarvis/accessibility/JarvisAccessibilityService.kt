package com.example.jarvis.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.jarvis.commands.CommandResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Jarvis AccessibilityService.
 *
 * Provides:
 * - Global actions: BACK, HOME, RECENTS, LOCK_SCREEN
 * - Gesture-based scrolling, swiping, and Instagram Reels/Shorts flicking
 * - Smart text insertion with clipboard paste fallback
 */
class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "JarvisA11y"

        @Volatile
        private var instance: JarvisAccessibilityService? = null

        fun isRunning(): Boolean = instance != null

        // ── Public API ─────────────────────────────────────────────────────────

        fun performBack()    = instance?.performGlobalAction(GLOBAL_ACTION_BACK)    ?: false
        fun performHome()    = instance?.performGlobalAction(GLOBAL_ACTION_HOME)    ?: false
        fun performRecents() = instance?.performGlobalAction(GLOBAL_ACTION_RECENTS) ?: false
        fun performLock()    = instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false
        fun performScreenshot(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                instance?.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT) ?: false
            } else {
                false
            }
        }
        fun openNotifications() = instance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) ?: false
        fun openQuickSettings() = instance?.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS) ?: false
        fun openPowerDialog()   = instance?.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)   ?: false
        fun toggleSplitScreen() = instance?.performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN) ?: false

        fun scrollUp()   = instance?.scroll(direction = ScrollDir.UP)   ?: CommandResult.Failure(NOT_RUNNING)
        fun scrollDown() = instance?.scroll(direction = ScrollDir.DOWN) ?: CommandResult.Failure(NOT_RUNNING)
        fun swipeLeft()  = instance?.swipe(direction = SwipeDir.LEFT)   ?: CommandResult.Failure(NOT_RUNNING)
        fun swipeRight() = instance?.swipe(direction = SwipeDir.RIGHT)  ?: CommandResult.Failure(NOT_RUNNING)

        fun nextReel()     = instance?.reelFlick(isNext = true)  ?: CommandResult.Failure(NOT_RUNNING)
        fun previousReel() = instance?.reelFlick(isNext = false) ?: CommandResult.Failure(NOT_RUNNING)
        fun likeReel()     = instance?.doubleTapCenter()         ?: CommandResult.Failure(NOT_RUNNING)
        fun tapCenter()    = instance?.singleTapCenter()         ?: CommandResult.Failure(NOT_RUNNING)

        fun insertText(text: String) = instance?.doInsertText(text) ?: CommandResult.Failure(NOT_RUNNING)

        fun getVisibleScreenText(): String {
            val inst = instance ?: return ""
            val root = inst.rootInActiveWindow ?: return ""
            val sb = StringBuilder()
            val seen = mutableSetOf<String>()

            fun traverse(node: AccessibilityNodeInfo?) {
                if (node == null) return
                val text = node.text?.toString()?.trim()
                val desc = node.contentDescription?.toString()?.trim()
                val piece = if (!text.isNullOrBlank()) text else if (!desc.isNullOrBlank()) desc else null
                if (!piece.isNullOrBlank() && seen.add(piece)) {
                    sb.append(piece).append("\n")
                }
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    traverse(child)
                    child.recycle()
                }
            }

            traverse(root)
            root.recycle()
            return sb.toString().trim()
        }

        fun autoClickWhatsAppSend(): Boolean {
            val inst = instance ?: return false
            val root = inst.rootInActiveWindow ?: return false
            val nodesById = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
            if (!nodesById.isNullOrEmpty()) {
                for (node in nodesById) {
                    if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        root.recycle()
                        return true
                    }
                }
            }
            val sendTexts = listOf("Send", "send", "పంపు", "भेजें")
            for (st in sendTexts) {
                val nodes = root.findAccessibilityNodeInfosByText(st)
                if (!nodes.isNullOrEmpty()) {
                    for (node in nodes) {
                        var curr: AccessibilityNodeInfo? = node
                        while (curr != null) {
                            if (curr.isClickable && curr.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                root.recycle()
                                return true
                            }
                            curr = curr.parent
                        }
                    }
                }
            }
            root.recycle()
            return false
        }

        private const val NOT_RUNNING =
            "Accessibility service is not running. Enable it in Settings → Accessibility → Blue Voice Control."

        private val _serviceActive = MutableStateFlow(false)
        val serviceActive: StateFlow<Boolean> = _serviceActive
    }

    enum class ScrollDir { UP, DOWN }
    enum class SwipeDir  { LEFT, RIGHT }

    private var screenWidth  = 1080f
    private var screenHeight = 2400f

    override fun onServiceConnected() {
        instance = this
        _serviceActive.value = true
        val wm = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        screenWidth  = metrics.widthPixels.toFloat()
        screenHeight = metrics.heightPixels.toFloat()
        Log.i(TAG, "Service connected — screen ${screenWidth}×${screenHeight}")
    }

    override fun onDestroy() {
        instance = null
        _serviceActive.value = false
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* not used */ }
    override fun onInterrupt() { Log.w(TAG, "Service interrupted") }

    // ── Scrolling & Reels Gestures ──────────────────────────────────────────

    private fun scroll(direction: ScrollDir): CommandResult {
        val cx = screenWidth / 2f
        val startY = if (direction == ScrollDir.DOWN) screenHeight * 0.75f else screenHeight * 0.25f
        val endY   = if (direction == ScrollDir.DOWN) screenHeight * 0.25f else screenHeight * 0.75f
        return performLinearGesture(cx, startY, cx, endY, 250, "Scrolling ${direction.name.lowercase()}")
    }

    private fun reelFlick(isNext: Boolean): CommandResult {
        val cx = screenWidth / 2f
        val startY = if (isNext) screenHeight * 0.80f else screenHeight * 0.20f
        val endY   = if (isNext) screenHeight * 0.20f else screenHeight * 0.80f
        val label  = if (isNext) "Next reel" else "Previous reel"
        return performLinearGesture(cx, startY, cx, endY, 200, label)
    }

    private fun swipe(direction: SwipeDir): CommandResult {
        val cy = screenHeight / 2f
        val (startX, endX) = when (direction) {
            SwipeDir.LEFT  -> Pair(screenWidth * 0.85f, screenWidth * 0.15f)
            SwipeDir.RIGHT -> Pair(screenWidth * 0.15f, screenWidth * 0.85f)
        }
        return performLinearGesture(startX, cy, endX, cy, 250, "Swiping ${direction.name.lowercase()}")
    }

    private fun singleTapCenter(): CommandResult {
        val cx = screenWidth / 2f
        val cy = screenHeight / 2f
        val path = Path().apply { moveTo(cx, cy) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val dispatched = dispatchGesture(gesture, null, null)
        return if (dispatched) CommandResult.Success("Tapped center")
        else CommandResult.Failure("Tap failed")
    }

    private fun doubleTapCenter(): CommandResult {
        val cx = screenWidth / 2f
        val cy = screenHeight / 2f
        val path1 = Path().apply { moveTo(cx, cy) }
        val stroke1 = GestureDescription.StrokeDescription(path1, 0, 50)
        val stroke2 = GestureDescription.StrokeDescription(path1, 120, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke1).addStroke(stroke2).build()
        val dispatched = dispatchGesture(gesture, null, null)
        return if (dispatched) CommandResult.Success("Liked reel")
        else CommandResult.Failure("Double tap failed")
    }

    // ── Smart Text Insertion ────────────────────────────────────────────────

    private fun doInsertText(text: String): CommandResult {
        val root = rootInActiveWindow
            ?: return CommandResult.Failure("No active window found. Tap a text field first.")

        var targetNode = findFocusedEditable(root)
        if (targetNode == null) {
            targetNode = findAnyEditable(root)
        }

        if (targetNode == null) {
            root.recycle()
            return CommandResult.NeedsSetup("No text box found. Tap the message field, then say 'type $text'.")
        }

        targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

        // 1. Try standard Accessibility ACTION_SET_TEXT
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val setOk = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

        if (setOk) {
            targetNode.recycle()
            root.recycle()
            return CommandResult.Success("Text entered")
        }

        // 2. Fallback to clipboard + paste
        try {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Jarvis", text))
            val pasteOk = targetNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            targetNode.recycle()
            root.recycle()
            return if (pasteOk) CommandResult.Success("Text pasted")
            else CommandResult.Success("Copied to clipboard. Tap Paste in the field.")
        } catch (e: Exception) {
            targetNode.recycle()
            root.recycle()
            return CommandResult.Failure("Could not insert text: ${e.localizedMessage}")
        }
    }

    private fun findFocusedEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused && (node.isEditable || isEditTextClass(node))) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFocusedEditable(child)
            if (result != null) return result
            child.recycle()
        }
        return null
    }

    private fun findAnyEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable || isEditTextClass(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findAnyEditable(child)
            if (result != null) return result
            child.recycle()
        }
        return null
    }

    private fun isEditTextClass(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString() ?: return false
        return className.contains("EditText", ignoreCase = true) ||
               className.contains("TextInput", ignoreCase = true) ||
               className.contains("InputField", ignoreCase = true)
    }

    // ── Gesture helper ──────────────────────────────────────────────────────

    private fun performLinearGesture(
        startX: Float, startY: Float,
        endX: Float,   endY: Float,
        durationMs: Long,
        successMsg: String
    ): CommandResult {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val dispatched = dispatchGesture(gesture, null, null)
        return if (dispatched) CommandResult.Success(successMsg)
        else CommandResult.Failure("Gesture dispatch failed")
    }
}
