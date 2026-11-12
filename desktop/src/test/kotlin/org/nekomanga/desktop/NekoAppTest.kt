package org.nekomanga.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class NekoAppTest {

    @Test
    fun rendersInLightTheme() = runComposeUiTest {
        setContent { NekoApp(darkTheme = false) }
        onNodeWithText(APP_NAME).assertIsDisplayed()
    }

    @Test
    fun rendersInDarkTheme() = runComposeUiTest {
        setContent { NekoApp(darkTheme = true) }
        onNodeWithText(APP_NAME).assertIsDisplayed()
    }
}
