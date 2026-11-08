package com.example.jarvis.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jarvis.theme.JarvisBackground
import com.example.jarvis.theme.JarvisBlue
import com.example.jarvis.theme.JarvisBlueDark
import com.example.jarvis.theme.JarvisBlueGlow
import com.example.jarvis.theme.JarvisError
import com.example.jarvis.theme.JarvisPanel
import com.example.jarvis.theme.JarvisSuccess
import com.example.jarvis.theme.JarvisTextDim
import com.example.jarvis.theme.JarvisTextPrimary
import com.example.jarvis.theme.JarvisWarning

@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            TopBar(ui, vm)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                JarvisSiriOrb(ui, vm)
                Spacer(Modifier.height(36.dp))
                StateLabel(ui.jarvisState)
                Spacer(Modifier.height(18.dp))
                StatusCard(ui)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PermissionStrip(ui, vm)
            }
        }

        if (ui.showSettings) {
            SettingsSheet(vm = vm, onDismiss = { vm.toggleSettings(false) })
        }

        if (ui.showSetup) {
            SetupSheet(ui = ui, onDismiss = { vm.toggleSetup(false) })
        }
    }
}

// ── Top Bar ───────────────────────────────────────────────────────────────

@Composable
private fun TopBar(ui: JarvisUiState, vm: MainViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 56.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "B  L  U  E",
                style = MaterialTheme.typography.headlineLarge,
                color = JarvisBlue
            )
            Text(
                text = "AI Voice Assistant",
                style = MaterialTheme.typography.labelSmall,
                color = JarvisTextDim
            )
        }
        IconButton(onClick = { vm.toggleSettings(true) }) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = JarvisBlue)
        }
    }
}

// ── Siri-like Glowing Floating Orb ────────────────────────────────────────

@Composable
private fun JarvisSiriOrb(ui: JarvisUiState, vm: MainViewModel) {
    val isListening = ui.jarvisState == JarvisState.Listening
    val isProcessing = ui.jarvisState == JarvisState.Processing
    val isExecuting = ui.jarvisState == JarvisState.Executing

    val infiniteTransition = rememberInfiniteTransition(label = "jarvisSiriAnim")

    // Gentle floating levitation
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isListening) 1200 else 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatOffset"
    )

    // Siri aura wave breathing scale
    val waveScale by infiniteTransition.animateFloat(
        initialValue = if (isListening) 1.0f else 0.98f,
        targetValue = if (isListening) 1.25f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isListening) 700 else 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveScale"
    )

    // Pulsing glow alpha
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = if (isListening) 0.50f else 0.20f,
        targetValue = if (isListening) 0.95f else 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isListening) 600 else 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // Rotating arc reactor outer ring
    val rotOuter by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isListening) 3000 else 9000, easing = LinearEasing)
        ),
        label = "rotOuter"
    )

    // Counter-rotating middle ring
    val rotMiddle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isListening) 2200 else 7000, easing = LinearEasing)
        ),
        label = "rotMiddle"
    )

    // State color transition
    val primaryGlowColor by animateColorAsState(
        targetValue = when (ui.jarvisState) {
            JarvisState.Listening  -> Color(0xFF00E5FF)
            JarvisState.Processing -> JarvisWarning
            JarvisState.Executing  -> JarvisBlue
            JarvisState.Success    -> JarvisSuccess
            JarvisState.Error      -> JarvisError
            JarvisState.Idle       -> Color(0xFF00B4FF)
        },
        label = "primaryGlowColor"
    )

    val secondaryGlowColor = when (ui.jarvisState) {
        JarvisState.Listening  -> Color(0xFF7C4DFF)
        JarvisState.Processing -> Color(0xFFFF9100)
        JarvisState.Success    -> Color(0xFF00E676)
        JarvisState.Error      -> Color(0xFFFF1744)
        else                   -> Color(0xFF1E88E5)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(240.dp)
            .offset(y = floatOffset.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (isListening) vm.stopListening()
                else vm.startListening()
            }
    ) {
        // High-tech Canvas for Siri-style glowing undulating ribbons & arc reactor rings
        Canvas(
            modifier = Modifier
                .size(230.dp)
                .scale(waveScale)
        ) {
            val centerPt = center
            val radiusMax = size.minDimension / 2f

            // 1. Siri-style Radial Background Glow Aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryGlowColor.copy(alpha = glowAlpha * 0.40f),
                        secondaryGlowColor.copy(alpha = glowAlpha * 0.20f),
                        Color.Transparent
                    ),
                    center = centerPt,
                    radius = radiusMax
                ),
                radius = radiusMax
            )

            // 2. Outer Rotating Dashed Arc Reactor Ring
            rotate(rotOuter, pivot = centerPt) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            primaryGlowColor.copy(alpha = 0.8f),
                            secondaryGlowColor.copy(alpha = 0.6f),
                            primaryGlowColor.copy(alpha = 0.1f),
                            primaryGlowColor.copy(alpha = 0.8f)
                        )
                    ),
                    radius = radiusMax * 0.88f,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(45f, 25f, 75f, 30f), 0f)
                    )
                )
            }

            // 3. Middle Counter-Rotating Fluid Segment Ring
            rotate(rotMiddle, pivot = centerPt) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            secondaryGlowColor.copy(alpha = 0.8f),
                            primaryGlowColor.copy(alpha = 0.5f),
                            Color.Transparent,
                            secondaryGlowColor.copy(alpha = 0.8f)
                        )
                    ),
                    radius = radiusMax * 0.72f,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(30f, 35f, 60f, 25f), 0f)
                    )
                )
            }

            // 4. Inner Energy Halo Ring
            drawCircle(
                color = primaryGlowColor.copy(alpha = if (isListening) 0.6f else 0.25f),
                radius = radiusMax * 0.56f,
                style = Stroke(width = if (isListening) 2.5.dp.toPx() else 1.dp.toPx())
            )
        }

        // Inner Core Glass Button
        Surface(
            modifier = Modifier.size(116.dp),
            shape = CircleShape,
            color = JarvisPanel,
            border = BorderStroke(
                width = if (isListening) 2.5.dp else 1.5.dp,
                color = primaryGlowColor.copy(alpha = if (isListening) 0.9f else 0.5f)
            ),
            shadowElevation = if (isListening) 16.dp else 6.dp
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                primaryGlowColor.copy(alpha = if (isListening) 0.25f else 0.08f),
                                Color.Transparent
                            )
                        )
                    )
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Filled.Mic else Icons.Filled.MicOff,
                    contentDescription = if (isListening) "Listening" else "Tap to speak",
                    modifier = Modifier.size(46.dp),
                    tint = primaryGlowColor
                )
            }
        }
    }
}

@Composable
private fun StateLabel(state: JarvisState) {
    val (label, color) = when (state) {
        JarvisState.Idle       -> "Tap or say 'Hey Blue'" to JarvisTextDim
        JarvisState.Listening  -> "Listening…"               to JarvisBlueGlow
        JarvisState.Processing -> "Processing…"              to JarvisWarning
        JarvisState.Executing  -> "Executing…"               to JarvisBlue
        JarvisState.Success    -> "Done"                      to JarvisSuccess
        JarvisState.Error      -> "Error"                     to JarvisError
    }
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.5.sp),
        color = color
    )
}

@Composable
private fun StatusCard(ui: JarvisUiState) {
    val isError = ui.jarvisState == JarvisState.Error
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isError) JarvisError.copy(alpha = 0.12f)
                else         JarvisBlue.copy(alpha = 0.10f),
        border = BorderStroke(
            1.dp,
            if (isError) JarvisError.copy(alpha = 0.4f)
            else         JarvisBlue.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (ui.lastCommand.isNotBlank()) {
                Text(
                    text = "\"${ui.lastCommand}\"",
                    style = MaterialTheme.typography.bodyLarge,
                    color = JarvisTextPrimary
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                text = ui.statusMessage.ifBlank { "Ready for voice command" },
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) JarvisError else JarvisTextDim
            )
        }
    }
}

@Composable
private fun PermissionStrip(ui: JarvisUiState, vm: MainViewModel) {
    val indicators = listOf(
        Triple("MIC",   ui.micPermission,      "Microphone"),
        Triple("A11Y",  ui.accessibilityActive, "Accessibility"),
        Triple("PHONE", ui.contactPermission,   "Contacts"),
        Triple("OD",    ui.onDeviceSpeech,      "On-device STT"),
        Triple("WAKE",  ui.wakeWordActive,      "Wake Word"),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        indicators.forEach { (label, active, _) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = if (active) JarvisSuccess else JarvisError.copy(alpha = 0.6f),
                            shape = CircleShape
                        )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = if (active) JarvisSuccess else JarvisTextDim
                )
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = { vm.toggleSetup(true) }) {
        Text("Setup & Permissions",
             style = MaterialTheme.typography.labelSmall,
             color = JarvisTextDim)
    }
}

// ── Settings Sheet ────────────────────────────────────────────────────────

@Composable
fun SettingsSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisBackground)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Settings, contentDescription = null, tint = JarvisBlue)
            }
            Text("SETTINGS", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(24.dp))

        // Wake Word toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Hey Blue (Wake Word)", color = JarvisTextPrimary)
                Text("Continuously listen in background for 'Hey Blue'",
                     color = JarvisTextDim,
                     style = MaterialTheme.typography.labelSmall)
            }
            Switch(
                checked = ui.wakeWordActive,
                onCheckedChange = { vm.toggleWakeWord(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = JarvisBlue)
            )
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = JarvisPanel)
        Spacer(Modifier.height(16.dp))

        // Floating Overlay Orb toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Floating HUD Orb", color = JarvisTextPrimary)
                Text("Draggable floating orb over Reels & games",
                     color = JarvisTextDim,
                     style = MaterialTheme.typography.labelSmall)
            }
            Switch(
                checked = ui.floatingOrbActive,
                onCheckedChange = { vm.toggleFloatingOrb(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = JarvisBlue)
            )
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = JarvisPanel)
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Silent Mode", color = JarvisTextPrimary)
                Text("Blue won't speak confirmations",
                     color = JarvisTextDim,
                     style = MaterialTheme.typography.labelSmall)
            }
            Switch(
                checked = ui.silentMode,
                onCheckedChange = { vm.updateSilentMode(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = JarvisBlue)
            )
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = JarvisPanel)
        Spacer(Modifier.height(24.dp))

        Text("Speech Rate: ${String.format("%.2f", ui.ttsRate)}×", color = JarvisTextPrimary)
        Slider(
            value = ui.ttsRate,
            onValueChange = { vm.updateTtsRate(it) },
            valueRange = 0.5f..2.0f,
            colors = SliderDefaults.colors(thumbColor = JarvisBlue, activeTrackColor = JarvisBlue)
        )

        Spacer(Modifier.height(16.dp))

        Text("Pitch: ${String.format("%.2f", ui.ttsPitch)}", color = JarvisTextPrimary)
        Slider(
            value = ui.ttsPitch,
            onValueChange = { vm.updateTtsPitch(it) },
            valueRange = 0.5f..1.5f,
            colors = SliderDefaults.colors(thumbColor = JarvisBlue, activeTrackColor = JarvisBlue)
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = JarvisPanel)
        Spacer(Modifier.height(16.dp))

        Text("ELEVENLABS NEURAL VOICE", style = MaterialTheme.typography.titleMedium, color = JarvisBlue)
        Spacer(Modifier.height(4.dp))
        Text(
            "Selected Voice: Jessica (Voice ID: cgSgspJ2msm6clMCkdW9)\n" +
            "Playful, bright, warm female voice. Speaks English, Telugu, and Hindi naturally on ElevenLabs free tier.",
            color = JarvisTextDim,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Enable ElevenLabs", color = JarvisTextPrimary)
                Text("Use ElevenLabs neural audio when API key is set",
                     color = JarvisTextDim,
                     style = MaterialTheme.typography.labelSmall)
            }
            Switch(
                checked = ui.elevenLabsEnabled,
                onCheckedChange = { vm.toggleElevenLabs(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = JarvisBlue)
            )
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = ui.elevenLabsApiKey,
            onValueChange = { vm.updateElevenLabsKey(it) },
            label = { Text("ElevenLabs API Key") },
            placeholder = { Text("Paste free key from elevenlabs.io") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JarvisBlue,
                unfocusedBorderColor = JarvisPanel,
                focusedLabelColor = JarvisBlue,
                unfocusedLabelColor = JarvisTextDim
            )
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = JarvisPanel)
        Spacer(Modifier.height(16.dp))

        Text("HIGH-SPEED AI ENGINE (GROQ LPU)", style = MaterialTheme.typography.titleMedium, color = JarvisBlue)
        Spacer(Modifier.height(4.dp))
        Text(
            "Generates responses in 200–400ms (LPU Instant).\n" +
            "If blank, Blue uses instant Wikipedia definitions & background neural engine.",
            color = JarvisTextDim,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = ui.groqApiKey,
            onValueChange = { vm.updateGroqKey(it) },
            label = { Text("Groq API Key (Optional)") },
            placeholder = { Text("Paste free key from console.groq.com") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JarvisBlue,
                unfocusedBorderColor = JarvisPanel,
                focusedLabelColor = JarvisBlue,
                unfocusedLabelColor = JarvisTextDim
            )
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = JarvisPanel)
        Spacer(Modifier.height(16.dp))

        Text("ASSISTANT LANGUAGE (తెలుగు / ENGLISH)", style = MaterialTheme.typography.titleMedium, color = JarvisBlue)
        Spacer(Modifier.height(4.dp))
        Text(
            "Auto accepts both Telugu & English seamlessly. You can also lock Blue to Telugu.",
            color = JarvisTextDim,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val languages = listOf(
                "auto" to "Auto (EN+TE)",
                "te-IN" to "తెలుగు",
                "en-US" to "English"
            )
            languages.forEach { (code, label) ->
                val isSelected = ui.assistantLanguage == code
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) JarvisBlueDark else JarvisPanel,
                    border = BorderStroke(1.dp, if (isSelected) JarvisBlue else JarvisPanel),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { vm.updateAssistantLanguage(code) }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 10.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) JarvisBlueGlow else JarvisTextDim,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = JarvisPanel)
        Spacer(Modifier.height(16.dp))

        // ── Continuous Voice Chat Mode (v5) ──────────────────────────────
        Text("CONTINUOUS VOICE CHAT MODE (v5)", style = MaterialTheme.typography.titleMedium, color = JarvisBlue)
        Spacer(Modifier.height(4.dp))
        Text(
            "Hands-free back-and-forth conversational loop (ChatGPT Voice style).",
            color = JarvisTextDim,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Continuous Chat Loop", color = JarvisTextPrimary)
                Text("Blue automatically listens again after answering",
                     color = JarvisTextDim,
                     style = MaterialTheme.typography.labelSmall)
            }
            Switch(
                checked = ui.continuousChatMode,
                onCheckedChange = { vm.toggleContinuousChat(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = JarvisBlue)
            )
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = JarvisPanel)
        Spacer(Modifier.height(16.dp))

        // ── Relationship & Memory Options (v5) ─────────────────────────────
        Text("RELATIONSHIP MEMORY (v5)", style = MaterialTheme.typography.titleMedium, color = JarvisBlue)
        Spacer(Modifier.height(4.dp))
        Text(
            "Manage remembered contacts and relationships (e.g., Harsha JR is your Bamardhi). " +
            "Say 'call bamardhi' or 'forget my bamardhi' anytime.",
            color = JarvisTextDim,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(12.dp))

        if (ui.relationships.isEmpty()) {
            Text("No relationships saved yet. Say 'Teja is my brother' or 'Harsha JR is my Bamardhi' to teach Blue!",
                 color = JarvisTextDim,
                 style = MaterialTheme.typography.bodySmall)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.relationships.forEach { (rel, contact) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(JarvisPanel, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rel.replaceFirstChar { it.uppercase() }, color = JarvisBlue, style = MaterialTheme.typography.titleSmall)
                            Text(contact, color = JarvisTextPrimary, style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(onClick = { vm.deleteRelationship(rel) }) {
                            Text("Erase", color = JarvisError, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = JarvisPanel)
        Spacer(Modifier.height(16.dp))

        // ── Custom Workflows & Routines (v5) ───────────────────────────────
        Text("CUSTOM WORKFLOWS & ROUTINES (v5)", style = MaterialTheme.typography.titleMedium, color = JarvisBlue)
        Spacer(Modifier.height(4.dp))
        Text(
            "Execute multi-action routines with a single command (e.g. 'run workflow gaming').",
            color = JarvisTextDim,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(12.dp))

        if (ui.workflows.isEmpty()) {
            Text("No custom workflows yet. Say 'create workflow gaming steps volume 12 and torch off' to create one!",
                 color = JarvisTextDim,
                 style = MaterialTheme.typography.bodySmall)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.workflows.forEach { (name, steps) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(JarvisPanel, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name.replaceFirstChar { it.uppercase() }, color = JarvisBlue, style = MaterialTheme.typography.titleSmall)
                            Text(steps.joinToString(" → "), color = JarvisTextDim, style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { vm.deleteWorkflow(name) }) {
                            Text("Delete", color = JarvisError, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = JarvisBlueDark)
        ) { Text("Done") }
    }
}

// ── Setup / Help Sheet ────────────────────────────────────────────────────

@Composable
fun SetupSheet(ui: JarvisUiState, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisBackground)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Settings, contentDescription = null, tint = JarvisBlue)
            }
            Text("SETUP & PERMISSIONS", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(16.dp))

        SetupItem("Microphone", ui.micPermission,
            "Required for voice commands. Grant via Settings → Apps → BLUE → Permissions.")
        SetupItem("Accessibility Service", ui.accessibilityActive,
            "Required for navigation, scrolling Reels, text entry, screen lock.\n" +
            "Enable: Settings → Accessibility → Installed Services → Blue Voice Control → ON")
        SetupItem("Contacts / Phone", ui.contactPermission,
            "Required to call or message contacts by name.")
        SetupItem("On-Device Speech", ui.onDeviceSpeech,
            "On-device recognition is available — no internet needed for speech.\n" +
            "If OFF, Google STT uses cloud (internet required).")

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = JarvisPanel)
        Spacer(Modifier.height(24.dp))

        Text("QUICK COMMANDS", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        val commands = listOf(
            "Hey Blue [command]"       to "Wake word background activation",
            "Next reel / Next video"   to "Fling scroll Instagram Reels & Shorts",
            "Previous reel"            to "Scroll up to previous Reel",
            "Like reel / Double tap"   to "Double tap center (heart on Reels)",
            "Open Chrome / Insta / YT" to "Launch any app by name/nickname",
            "Type: [message]"          to "Insert text into any chat/prompt box",
            "Send text to [name] saying [msg]" to "Compose SMS message",
            "What's my battery?"       to "Battery percentage & charging status",
            "Play / Pause / Next"      to "Background media playback",
            "Set timer for 5 minutes"  to "Clock countdown timer",
            "Set alarm for 7:00 AM"    to "System alarm",
            "Night mode"               to "DND on, volume 0, lock screen",
            "Morning mode"             to "DND off, volume 8, battery info",
            "Search for weather"       to "Google web search",
            "Volume up / Volume down"  to "Media volume steps",
            "Set volume to 8"          to "Exact level 0–15",
            "Mute / Unmute"            to "Media audio",
            "Flashlight on/off/toggle" to "Torch",
            "Lock screen"              to "Requires Accessibility Service",
            "Go back / Go home"        to "Navigation",
            "Scroll up / Scroll down"  to "Scroll active view",
            "Call John"                to "Calls contact named John",
            "Open WhatsApp"            to "Opens WhatsApp",
            "Do not disturb"           to "Enable DND",
            "Help"                     to "Show this screen",
        )
        commands.forEach { (cmd, desc) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text("\"$cmd\"",
                     color = JarvisBlue,
                     style = MaterialTheme.typography.bodyMedium,
                     modifier = Modifier.weight(1f))
                Text(desc,
                     color = JarvisTextDim,
                     style = MaterialTheme.typography.labelSmall,
                     modifier = Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = JarvisPanel)
        Spacer(Modifier.height(24.dp))

        Text("TELUGU COMMANDS (తెలుగు వాయిస్ కమాండ్స్)", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        val teluguCommands = listOf(
            "టార్చ్ ఆన్ చేయి / torch on cheyi"    to "టార్చ్ లైట్ ఆన్ చేస్తుంది",
            "టార్చ్ ఆఫ్ చేయి / torch off cheyi"   to "టార్చ్ లైట్ ఆఫ్ చేస్తుంది",
            "బ్యాటరీ ఎంత ఉంది / battery entha"     to "బ్యాటరీ శాతం తెలుపుతుంది",
            "సమయం ఎంత / time entha"               to "ప్రస్తుత సమయం చెబుతుంది",
            "ఈరోజు తేదీ ఎంత / date entha"         to "ఈరోజు తేదీ చెబుతుంది",
            "వాల్యూమ్ పెంచు / sound penchu"       to "వాల్యూమ్ పెంచుతుంది",
            "వాల్యూమ్ తగ్గించు / sound thagginchu" to "వాల్యూమ్ తగ్గిస్తుంది",
            "మ్యూట్ చేయి / mute cheyi"            to "శబ్దం మ్యూట్ చేస్తుంది",
            "నెక్స్ట్ రీల్ / next reel"             to "తరువాతి రీల్ కి స్క్రోల్",
            "మునుపటి రీల్ / previous reel"        to "మునుపటి రీల్ కి స్క్రోల్",
            "లైక్ చేయి / like cheyi"              to "రీల్ లేదా వీడియో లైక్",
            "స్క్రీన్‌షాట్ తీయి / screenshot theeyi" to "స్క్రీన్‌షాట్ క్యాప్చర్",
            "లాక్ చేయి / lock cheyi"              to "ఫోన్ లాక్ చేస్తుంది",
            "హోమ్ కి వెళ్ళు / home ki vellu"       to "హోమ్ స్క్రీన్ కి వెళ్తుంది",
            "వెనక్కి వెళ్ళు / back ki vellu"       to "బ్యాక్ నావిగేట్ చేస్తుంది",
            "[పేరు] కి కాల్ చేయి"                to "కాంటాక్ట్ కి కాల్ చేస్తుంది",
            "వాట్సాప్ ఓపెన్ చేయి"                 to "వాట్సాప్ ఓపెన్ చేస్తుంది",
            "యూట్యూబ్ ఓపెన్ చేయి"                 to "యూట్యూబ్ ఓపెన్ చేస్తుంది",
            "నువ్వు ఎవరు / ఎలా ఉన్నావు"           to "తెలుగులో నేచురల్ సంభాషణ",
            "భూమి అంటే ఏమిటి / ఎందుకు..."         to "తెలుగు AI సమాధానం (Groq / Wiki)",
        )
        teluguCommands.forEach { (cmd, desc) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text("\"$cmd\"",
                     color = JarvisBlue,
                     style = MaterialTheme.typography.bodyMedium,
                     modifier = Modifier.weight(1.1f))
                Text(desc,
                     color = JarvisTextDim,
                     style = MaterialTheme.typography.labelSmall,
                     modifier = Modifier.weight(0.9f))
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = JarvisPanel)
        Spacer(Modifier.height(24.dp))

        Text("BLUE v5 INTELLIGENCE & MULTILINGUAL", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        val v5Commands = listOf(
            "Look at my screen / స్క్రీన్ చూడు" to "Analyzes on-screen content using Groq Vision",
            "Translate this lambadi to telugu" to "Polyglot translation (Lambadi/Telugu/Hindi/EN)",
            "Start chat mode / చాట్ మోడ్" to "Continuous hands-free voice loop (ChatGPT Voice style)",
            "[Name] is my [Relation]" to "e.g. 'Harsha JR is my Bamardhi' / 'Teja is my brother'",
            "Call [Relation]" to "e.g. 'Call bamardhi' dials Harsha JR immediately",
            "Forget my [Relation]" to "e.g. 'Forget my bamardhi' erases from memory",
            "Run workflow [name]" to "Executes custom multi-step routine",
            "Torch on karo / battery kitni hai" to "Hindi (हिंदी / Hinglish) device commands",
            "Ram Ram / Tharo naam kai che" to "Lambadi / Gorboli natural conversation",
        )
        v5Commands.forEach { (cmd, desc) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text("\"$cmd\"",
                     color = JarvisBlue,
                     style = MaterialTheme.typography.bodyMedium,
                     modifier = Modifier.weight(1.1f))
                Text(desc,
                     color = JarvisTextDim,
                     style = MaterialTheme.typography.labelSmall,
                     modifier = Modifier.weight(0.9f))
            }
        }

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = JarvisBlueDark)
        ) { Text("Got it") }
    }
}

@Composable
private fun SetupItem(label: String, ok: Boolean, hint: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(10.dp)
                .background(
                    color = if (ok) JarvisSuccess else JarvisError.copy(alpha = 0.7f),
                    shape = CircleShape
                )
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, color = JarvisTextPrimary, style = MaterialTheme.typography.bodyLarge)
            Text(hint,  color = JarvisTextDim,     style = MaterialTheme.typography.bodySmall)
        }
    }
}
