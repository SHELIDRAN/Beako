package org.nekomanga.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.skia.Image

/** Classpath path of the window icon (the same artwork as the Rust prototype's icon). */
const val APP_ICON = "neko-icon.png"

fun main() = application {
    val icon = remember { BitmapPainter(loadAppIcon()) }
    Window(
        onCloseRequest = ::exitApplication,
        title = APP_NAME,
        icon = icon,
        state = rememberWindowState(width = 1280.dp, height = 800.dp),
    ) {
        BeakoApp()
    }
}

/** Decodes the bundled app icon. */
fun loadAppIcon(): ImageBitmap {
    val bytes =
        checkNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream(APP_ICON)) {
                "Missing resource $APP_ICON"
            }
            .use { it.readBytes() }
    return Image.makeFromEncoded(bytes).toComposeImageBitmap()
}
