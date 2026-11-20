package com.nuvio.app.features.sports

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.nuvio.app.core.ui.NuvioTheme
import org.junit.Rule
import kotlin.test.Test

class SportsDesktopScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun hubShowsTopBarAndSportBars() {
        compose.setContent { NuvioTheme { SportsDesktopScreen(onBack = {}, onWatch = {}) } }
        compose.onNodeWithText("Sports").assertExists()
        compose.onNodeWithText("All Events", substring = true).assertExists()
        compose.onNodeWithText("Tables", substring = true).assertExists()
        compose.onNodeWithText("Today").assertExists()
    }

    @Test
    fun teamSearchBarIsVisible() {
        compose.setContent { NuvioTheme { SportsDesktopScreen(onBack = {}, onWatch = {}) } }
        compose.onNodeWithText("Search teams", substring = true).assertExists()
    }
}
