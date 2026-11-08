package com.example.jarvis.assist

import android.app.Activity
import android.os.Bundle
import com.example.jarvis.overlay.JarvisOverlayService

/**
 * Triggered when user holds the hardware Power button (or long-presses Home)
 * when Jarvis is set as the Default Digital Assistant app.
 * Immediately brings up the transparent holographic Jarvis HUD without interrupting the current screen.
 */
class JarvisAssistActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        JarvisOverlayService.activate(applicationContext, "")
        finish()
    }
}
