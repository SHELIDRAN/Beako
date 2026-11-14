package org.nekomanga.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import org.jetbrains.skia.Image
import org.nekomanga.desktop.components.AccountSlot
import org.nekomanga.desktop.components.Destination
import org.nekomanga.desktop.screens.AccountsSection
import org.nekomanga.desktop.screens.BackupSection
import org.nekomanga.desktop.screens.BrowseScreen
import org.nekomanga.desktop.screens.ComickReleasesShelf
import org.nekomanga.desktop.screens.DetailsScreen
import org.nekomanga.desktop.screens.HistoryScreen
import org.nekomanga.desktop.screens.LibraryScreen
import org.nekomanga.desktop.screens.MoreScreen
import org.nekomanga.desktop.screens.ReaderScreen
import org.nekomanga.desktop.screens.SearchScreen
import org.nekomanga.desktop.screens.UpdatesScreen
import org.nekomanga.desktop.state.AppContainer
import org.nekomanga.desktop.state.AppState
import org.nekomanga.desktop.state.LocalWindowControl
import org.nekomanga.desktop.state.Page
import org.nekomanga.desktop.state.UpdateChecker
import org.nekomanga.desktop.state.WindowControl
import org.nekomanga.desktop.theme.BeakoAppearance
import org.nekomanga.desktop.theme.SidebarMode

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
        val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
        // A tray icon carries Windows notifications for new chapters.
        val trayState = rememberTrayState()
        if (isTraySupported) {
            Tray(
                icon = icon,
                state = trayState,
                tooltip = APP_NAME,
                onAction = { windowState.isMinimized = false },
            ) {
                Item("Open $APP_NAME", onClick = { windowState.isMinimized = false })
                Item(
                    "Quit",
                    onClick = {
                        container.library.flush()
                        exitApplication()
                    },
                )
            }
        }
        val windowControl = remember { WindowControl(windowState) }
        Window(
            onCloseRequest = {
                container.library.flush()
                exitApplication()
            },
            title = APP_NAME,
            icon = icon,
            state = windowState,
        ) {
            val scope = rememberCoroutineScope()
            val state = remember {
                AppState(
                    container,
                    scope,
                    onUpdates = { updates ->
                        trayState.sendNotification(
                            Notification(
                                APP_NAME,
                                UpdateChecker.summary(updates),
                                Notification.Type.Info,
                            ),
                        )
                    },
                )
            }
            LaunchedEffect(state) {
                state.accounts.restore()
                // Only when the library loaded: a backup of an unreadable library would be empty.
                if (container.library.loadError == null) state.backups.runAutomatic()
                state.updates.start()
            }
            // Developer aid for screenshots: BEAKO_OPEN=search:<query>, title:<query> or
            // read:<query> starts on that page, using the first MangaDex match.
            LaunchedEffect(Unit) { openForDevelopment(state, System.getenv("BEAKO_OPEN")) }
            CompositionLocalProvider(LocalWindowControl provides windowControl) {
                val settings by container.settings.settings.collectAsState()
                BeakoApp(
                    appearance =
                        BeakoAppearance(
                            reduceMotion = settings.reduceMotion,
                            reduceTransparency = settings.reduceTransparency,
                            sidebarMode =
                                runCatching { SidebarMode.valueOf(settings.sidebarMode) }
                                    .getOrDefault(SidebarMode.AUTO_COLLAPSE),
                        ),
                    start = startScreen(System.getenv("BEAKO_OPEN")),
                    navigator = state.navigator,
                    page = { AppPage(state, it) },
                    screen = { AppScreen(state, it) },
                    accounts = accountSlots(state),
                    onAccountClick = { state.navigator.show(Destination.MORE) },
                )
            }
        }
    }
}

/** `BEAKO_OPEN=screen:library` (or updates, browse, history, more) starts on that screen. */
private fun startScreen(request: String?): Destination =
    request
        ?.removePrefix("screen:")
        ?.takeIf { request.startsWith("screen:") }
        ?.let { name ->
            Destination.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        } ?: Destination.BROWSE

private suspend fun openForDevelopment(state: AppState, request: String?) {
    val (kind, query) = request?.split(":", limit = 2)?.takeIf { it.size == 2 } ?: return
    if (kind == "screen") return
    if (kind == "search") {
        state.navigator.open(Page.Search(query))
        return
    }
    val title =
        runCatching { state.container.mangaDex.search(query, limit = 1).items.firstOrNull() }
            .getOrNull() ?: return
    state.navigator.open(Page.Details(title))
    if (kind == "read") {
        val chapters = runCatching {
            state.container.mangaDex.allChapters(title.id)
        }
            .getOrNull()
            ?.reversed()
            .orEmpty()
        val first = chapters.indexOfFirst { it.externalUrl == null && it.pages > 0 }
        if (first >= 0) state.navigator.open(Page.Reader(title, chapters, first))
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
        Destination.LIBRARY ->
            LibraryScreen(
                state.libraryScreen.also {
                    it.downloadedOnly =
                        state.container.settings.settings.collectAsState().value.downloadedOnly
                },
                downloadChanges = state.container.downloads.changes.collectAsState().value,
                onOpen = { state.navigator.open(Page.Details(it)) },
                onSearchSources = { state.navigator.open(Page.Search(it)) },
                onBrowse = { state.navigator.show(Destination.BROWSE) },
            )
        Destination.UPDATES ->
            UpdatesScreen(
                state.container.library,
                state.updates,
                onOpen = { state.navigator.open(Page.Details(it)) },
                onLibrary = { state.navigator.show(Destination.LIBRARY) },
                shelf = {
                    ComickReleasesShelf(state.accounts) { state.navigator.open(Page.Details(it)) }
                },
            )
        Destination.HISTORY ->
            HistoryScreen(
                state.container.library,
                onResume = { state.navigator.open(Page.Details(it.title)) },
                onBrowse = { state.navigator.show(Destination.BROWSE) },
            )
        Destination.MORE ->
            MoreScreen(
                state.container.settings,
                state.container.library,
                state.container.downloads,
                state.container.dirs.downloads,
                accounts = {
                    AccountsSection(
                        state.accounts,
                        state.container.settings,
                        state.container.vault.persistent,
                    )
                },
                backup = { BackupSection(state.backups, state.container.settings) },
            )
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
        is Page.Details ->
            DetailsScreen(
                state.details(page.title),
                onBack = { state.navigator.back() },
                onRead = { chapters, index, start ->
                    state.navigator.open(Page.Reader(page.title, chapters, index, start))
                },
                tracking = state.tracking(page.title),
                onOpenTitle = { state.navigator.open(Page.Details(it)) },
            )
        is Page.Reader -> {
            val settings by state.container.settings.settings.collectAsState()
            ReaderScreen(
                state.reader(page),
                onClose = { state.navigator.back() },
                prefs = settings.reader,
                onPrefs = { prefs -> state.container.settings.update { it.copy(reader = prefs) } },
            )
        }
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

/** The sidebar's account avatars: Comick and MangaDex always, AniList once connected. */
@Composable
private fun accountSlots(state: AppState): List<AccountSlot> {
    val comick by state.accounts.comick.account.collectAsState()
    val aniList by state.accounts.aniList.account.collectAsState()
    val mangaDex by state.accounts.mangaDex.username.collectAsState()
    return listOfNotNull(
        AccountSlot("Comick", comick?.name, comick?.avatarUrl),
        AccountSlot("MangaDex", mangaDex),
        aniList?.let { AccountSlot("AniList", it.name, it.avatarUrl) },
    )
}
