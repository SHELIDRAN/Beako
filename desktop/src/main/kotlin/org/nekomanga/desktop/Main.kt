package org.nekomanga.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import org.jetbrains.skia.Image
import org.nekomanga.desktop.components.Destination
import org.nekomanga.desktop.screens.BrowseScreen
import org.nekomanga.desktop.screens.SearchScreen
import org.nekomanga.desktop.state.AppContainer
import org.nekomanga.desktop.state.AppState
import org.nekomanga.desktop.state.Page

/** Classpath path of the window icon (the same artwork as the Rust prototype's icon). */
const val APP_ICON = "neko-icon.png"

fun main() {
    val container = AppContainer()
    // Covers and pages load through the app's own HTTP client, so they carry Beako's User-Agent.
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.httpClient })) }
            .crossfade(true)
            .build()
    }
    application {
        val icon = remember { BitmapPainter(loadAppIcon()) }
        Window(
            onCloseRequest = ::exitApplication,
            title = APP_NAME,
            icon = icon,
            state = rememberWindowState(width = 1280.dp, height = 800.dp),
        ) {
            val scope = rememberCoroutineScope()
            val state = remember { AppState(container, scope) }
            // Developer aid: BEAKO_OPEN=search:<query> starts on the Search page.
            remember {
                System.getenv("BEAKO_OPEN")?.removePrefix("search:")?.let {
                    state.navigator.open(Page.Search(it))
                }
            }
            BeakoApp(
                navigator = state.navigator,
                page = { AppPage(state, it) },
                screen = { AppScreen(state, it) },
            )
        }
    }
}

@Composable
private fun AppScreen(state: AppState, destination: Destination) {
    when (destination) {
        Destination.BROWSE ->
            BrowseScreen(
                state.browse,
                onSearch = { state.navigator.open(Page.Search()) },
                onOpen = { state.navigator.open(Page.Details(it)) },
            )
        else -> PlaceholderScreen(destination)
    }
}

@Composable
private fun AppPage(state: AppState, page: Page) {
    when (page) {
        is Page.Search ->
            SearchScreen(
                state.search,
                page.query,
                onBack = { state.navigator.back() },
                onOpen = { state.navigator.open(Page.Details(it)) },
            )
        is Page.Details -> PlaceholderPage(page.title.title) { state.navigator.back() }
        is Page.Reader -> PlaceholderPage("Reader") { state.navigator.back() }
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
