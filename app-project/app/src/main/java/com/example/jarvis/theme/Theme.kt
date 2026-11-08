package com.example.jarvis.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val JarvisDarkColorScheme = darkColorScheme(
    primary          = JarvisBlue,
    onPrimary        = JarvisBackground,
    primaryContainer = JarvisBlueDark,
    secondary        = JarvisBlueGlow,
    background       = JarvisBackground,
    surface          = JarvisSurface,
    onBackground     = JarvisTextPrimary,
    onSurface        = JarvisTextPrimary,
    error            = JarvisError,
    onError          = JarvisBackground
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JarvisDarkColorScheme,
        typography  = JarvisTypography,
        content     = content
    )
}
