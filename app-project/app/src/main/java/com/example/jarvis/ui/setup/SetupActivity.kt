package com.example.jarvis.ui.setup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.jarvis.theme.JarvisTheme
import com.example.jarvis.ui.main.SetupSheet
import com.example.jarvis.ui.main.JarvisUiState

/**
 * Accessibility service settings activity.
 * Referenced in accessibility_service_config.xml as the settings target.
 * Opens the same Setup sheet that's available inside the main app.
 */
class SetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JarvisTheme {
                SetupSheet(
                    ui = JarvisUiState(),
                    onDismiss = { finish() }
                )
            }
        }
    }
}
