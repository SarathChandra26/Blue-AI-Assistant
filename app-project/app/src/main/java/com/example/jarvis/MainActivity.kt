package com.example.jarvis

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.jarvis.theme.JarvisTheme
import com.example.jarvis.ui.main.MainScreen
import com.example.jarvis.ui.main.MainViewModel

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JarvisTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(vm = vm)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkAutoStart(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkAutoStart(intent)
    }

    private fun checkAutoStart(intent: Intent?) {
        if (intent == null) return
        val testCommand = intent.getStringExtra("EXTRA_COMMAND")
        if (!testCommand.isNullOrBlank()) {
            intent.removeExtra("EXTRA_COMMAND")
            vm.executeCommand(testCommand)
            return
        }

        val autoStart = intent.getBooleanExtra("EXTRA_AUTO_START_SPEECH", false) ||
                intent.action == Intent.ACTION_VOICE_COMMAND
        if (autoStart) {
            intent.removeExtra("EXTRA_AUTO_START_SPEECH")
            intent.action = null
            vm.startListening()
        }
    }
}
