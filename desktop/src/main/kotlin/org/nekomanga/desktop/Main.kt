package org.nekomanga.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = APP_NAME,
        state = rememberWindowState(width = 1280.dp, height = 800.dp),
    ) {
        NekoApp()
    }
}
