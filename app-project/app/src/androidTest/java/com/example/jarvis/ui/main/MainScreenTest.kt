package com.example.jarvis.ui.main

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.jarvis.theme.JarvisTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun appName_isDisplayed() {
        composeTestRule.setContent {
            JarvisTheme { MainScreen() }
        }
        composeTestRule.onNodeWithText("J A R V I S").assertIsDisplayed()
    }
}
