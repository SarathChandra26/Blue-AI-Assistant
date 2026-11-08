package com.example.jarvis.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val JarvisTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Light,
        fontSize = 57.sp,
        letterSpacing = (-0.25).sp,
        color = JarvisTextPrimary
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Light,
        fontSize = 32.sp,
        letterSpacing = 4.sp,
        color = JarvisBlue
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        letterSpacing = 2.sp,
        color = JarvisTextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 0.5.sp,
        color = JarvisTextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.25.sp,
        color = JarvisTextSecondary
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp,
        color = JarvisTextDim
    )
)
