package org.nekomanga.desktop.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.PhotoSizeSelectActual
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WidthFull
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.awt.Desktop
import java.net.URI
import kotlinx.coroutines.launch
import org.nekomanga.desktop.components.DropletSegmented
import org.nekomanga.desktop.components.GlassButton
import org.nekomanga.desktop.components.GlassIconButton
import org.nekomanga.desktop.components.LoadingMessage
import org.nekomanga.desktop.components.StateMessage
import org.nekomanga.desktop.platform.KeepAwake
import org.nekomanga.desktop.state.KeyBindings
import org.nekomanga.desktop.state.Loadable
import org.nekomanga.desktop.state.LocalKeyRouter
import org.nekomanga.desktop.state.LocalWindowControl
import org.nekomanga.desktop.state.ReaderAction
import org.nekomanga.desktop.state.ReaderModel
import org.nekomanga.desktop.state.ReadingMode
import org.nekomanga.desktop.state.displayName
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.desktop.theme.LocalAppearance
import org.nekomanga.desktop.theme.Palette
import org.nekomanga.desktop.theme.glass
import org.nekomanga.shared.library.ReaderPrefs

/**
 * The reader. Webtoon mode scrolls a long strip and hides the controls while scrolling down,
 * showing them again on scrolling up. Paged modes turn with clicks, the wheel or the keyboard.
 *
 * Keys (rebindable in More → Keyboard shortcuts): ← → Space PageUp PageDown turn or scroll, `[` `]`
 * change chapter, F or F11 toggle full screen, O opens reader settings. Esc closes panels, leaves
 * full screen and then closes the reader.
 */
@Composable
fun ReaderScreen(
    model: ReaderModel,
    onClose: () -> Unit,
    prefs: ReaderPrefs = ReaderPrefs(),
    onPrefs: (ReaderPrefs) -> Unit = {},
    keys: KeyBindings = KeyBindings(emptyMap()),
) {
    LaunchedEffect(model) { model.start() }
    // The reader's background is fixed (black, gray or white) whatever the app's palette, so its
    // text and controls take the palette whose ink reads on it.
    val chosen = BeakoColors.chosenPalette
    DisposableEffect(prefs.background, chosen) {
        BeakoColors.override = readerPalette(prefs.background, chosen)
        onDispose { BeakoColors.override = null }
    }
    val window = LocalWindowControl.current
    // Keeps the display awake while the reader is open, if asked.
    DisposableEffect(prefs.keepScreenOn) {
        if (prefs.keepScreenOn) KeepAwake.hold()
        onDispose { KeepAwake.release() }
    }
    LaunchedEffect(Unit) { if (prefs.openFullscreen) window?.setFullscreen(true) }
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var controls by remember { mutableStateOf(true) }
    var settingsOpen by remember { mutableStateOf(false) }
    val listState = remember(model.index) { LazyListState() }
    // Each chapter opens with its controls showing and the reader holding focus, whatever was
    // clicked to get there.
    LaunchedEffect(model.index) {
        controls = true
        runCatching { focus.requestFocus() }
    }

    fun close() {
        window?.setFullscreen(false)
        onClose()
    }
    fun scroll(pagesOfWindow: Float) {
        scope.launch {
            listState.animateScrollBy(listState.layoutInfo.viewportSize.height * pagesOfWindow)
        }
    }

    fun handleKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val rtl = model.mode == ReadingMode.RIGHT_TO_LEFT
        val paged = model.mode.isPaged
        if (event.key == Key.Escape) {
            when {
                settingsOpen -> settingsOpen = false
                window?.isFullscreen == true -> window.setFullscreen(false)
                else -> close()
            }
            return true
        }
        when (keys.actionFor(event.key.nativeKeyCode)) {
            ReaderAction.FULL_SCREEN -> window?.toggleFullscreen()
            ReaderAction.PREVIOUS_CHAPTER -> model.previousChapter()
            ReaderAction.NEXT_CHAPTER -> model.nextChapter()
            ReaderAction.RIGHT -> if (paged) model.turn(forward = !rtl) else scroll(0.85f)
            ReaderAction.LEFT -> if (paged) model.turn(forward = rtl) else scroll(-0.85f)
            ReaderAction.NEXT_PAGE -> if (paged) model.turn(true) else scroll(0.85f)
            ReaderAction.PREVIOUS_PAGE -> if (paged) model.turn(false) else scroll(-0.85f)
            ReaderAction.SETTINGS -> settingsOpen = !settingsOpen
            null -> return false
        }
        return true
    }

    // Keys come from the window while the reader is open, so they work whatever has focus.
    val router = LocalKeyRouter.current
    val latestHandler = androidx.compose.runtime.rememberUpdatedState(::handleKey)
    androidx.compose.runtime.DisposableEffect(router) {
        router?.handler = { event -> latestHandler.value(event) }
        onDispose { router?.handler = null }
    }

    Box(
        Modifier.fillMaxSize()
            .background(readerBackground(prefs.background))
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event -> if (router == null) handleKey(event) else false },
    ) {
        val chapter = model.chapter
        when (val pages = model.pages) {
            is Loadable.Loading ->
                LoadingMessage(
                    "Loading ${chapter.displayName()}…",
                    Modifier.align(Alignment.Center),
                )
            is Loadable.Failed ->
                Box(Modifier.align(Alignment.Center)) {
                    StateMessage("Couldn’t open this chapter", pages.message) {
                        GlassButton("Try again", onClick = model::retry, primary = true)
                    }
                }
            is Loadable.Ready ->
                when {
                    chapter.externalUrl != null ->
                        Box(Modifier.align(Alignment.Center)) {
                            StateMessage(
                                "This chapter is on the publisher’s site",
                                "Beako can’t show it here, but it can open it in your browser.",
                            ) {
                                GlassButton(
                                    "Open in browser",
                                    onClick = { openInBrowser(chapter.externalUrl!!) },
                                    primary = true,
                                )
                            }
                        }
                    pages.value.urls.isEmpty() ->
                        Box(Modifier.align(Alignment.Center)) {
                            StateMessage("This chapter has no pages")
                        }
                    model.mode == ReadingMode.WEBTOON ->
                        WebtoonStrip(
                            model,
                            pages.value.urls,
                            listState,
                            prefs,
                            onToggleControls = { controls = !controls },
                            onScrollDirection = { down -> controls = !down },
                        )
                    else ->
                        PagedView(
                            model,
                            pages.value.urls,
                            prefs,
                            onToggleControls = { controls = !controls },
                        )
                }
        }
        if (
            prefs.showPageNumber &&
                !controls &&
                model.pages is Loadable.Ready &&
                !model.atTransition
        ) {
            PageNumber(model, Modifier.align(Alignment.BottomCenter))
        }
        ReaderControls(
            model,
            visible = controls || model.pages !is Loadable.Ready,
            onClose = ::close,
            onSettings = { settingsOpen = !settingsOpen },
            keys = keys,
        )
        ReaderSettingsPanel(
            visible = settingsOpen,
            prefs = prefs,
            paged = model.mode.isPaged,
            onPrefs = onPrefs,
            onClose = { settingsOpen = false },
        )
        if (model.autoSwitched) {
            var shown by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(5_000)
                shown = false
            }
            AnimatedVisibility(
                shown,
                Modifier.align(Alignment.BottomCenter).padding(bottom = 84.dp),
            ) {
                Text(
                    "Switched to Webtoon for this long-strip series. Change it at the top.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BeakoColors.Text,
                    modifier =
                        Modifier.glass(
                                RoundedCornerShape(14.dp),
                                tint = BeakoColors.GlassSolid.copy(alpha = 0.78f),
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

private fun openInBrowser(url: String) {
    runCatching { if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url)) }
}

/** How a page fits the window in the paged modes. */
enum class PageFit(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    SCREEN("Fit screen", Icons.Outlined.FitScreen),
    WIDTH("Fit width", Icons.Outlined.WidthFull),
    HEIGHT("Fit height", Icons.Outlined.Height),
    ORIGINAL("Original size", Icons.Outlined.PhotoSizeSelectActual);

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: SCREEN
    }
}

@Composable
private fun BoxScope.ReaderControls(
    model: ReaderModel,
    visible: Boolean,
    onClose: () -> Unit,
    onSettings: () -> Unit,
    keys: KeyBindings,
) {
    val window = LocalWindowControl.current
    val reduceMotion = LocalAppearance.current.reduceMotion
    val enter = if (reduceMotion) fadeIn(snap()) else fadeIn(tween(180))
    val exit = if (reduceMotion) fadeOut(snap()) else fadeOut(tween(180))
    val shape = RoundedCornerShape(22.dp)

    AnimatedVisibility(visible, Modifier.align(Alignment.TopCenter), enter, exit) {
        Row(
            Modifier.padding(12.dp)
                .fillMaxWidth()
                .glass(shape, tint = BeakoColors.GlassSolid.copy(alpha = 0.78f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassIconButton(
                Icons.AutoMirrored.Outlined.ArrowBack,
                "Close reader (Esc)",
                onClose,
                swingFrom = -90f,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    model.title.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = BeakoColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    model.chapter.displayName(),
                    style = MaterialTheme.typography.bodySmall,
                    color = BeakoColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DropletSegmented(
                options = ReadingMode.entries.map { it.label },
                selectedIndex = model.mode.ordinal,
                onSelect = { model.mode = ReadingMode.entries[it] },
                label = "Reading mode",
                optionWidth = 104.dp,
            )
            GlassIconButton(
                Icons.Outlined.Tune,
                "Reader settings${keys.hint(ReaderAction.SETTINGS)}",
                onSettings,
            )
            if (window != null) {
                // The label comes from the real window state, so it can't go stale.
                val full = window.isFullscreen
                GlassButton(
                    if (full) "Exit full screen" else "Full screen",
                    onClick = window::toggleFullscreen,
                    icon = if (full) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                )
            }
        }
    }

    AnimatedVisibility(visible, Modifier.align(Alignment.BottomCenter), enter, exit) {
        Row(
            Modifier.padding(16.dp)
                .glass(shape, tint = BeakoColors.GlassSolid.copy(alpha = 0.78f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (model.hasPrevious) {
                GlassIconButton(
                    Icons.Outlined.ChevronLeft,
                    "Previous chapter${keys.hint(ReaderAction.PREVIOUS_CHAPTER)}",
                    { model.previousChapter() },
                    swingFrom = -90f,
                )
            }
            val count = model.pageCount
            Text(
                if (count > 0) "${(model.page + 1).coerceAtMost(count)} of $count" else "—",
                style = MaterialTheme.typography.labelLarge,
                color = BeakoColors.Text,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            if (model.hasNext) {
                GlassIconButton(
                    Icons.Outlined.ChevronRight,
                    "Next chapter${keys.hint(ReaderAction.NEXT_CHAPTER)}",
                    model::nextChapter,
                    swingFrom = 90f,
                )
            }
        }
    }
}

/** The palette whose ink reads on reader background [name], given the user's [chosen] one. */
internal fun readerPalette(name: String, chosen: Palette): Palette? =
    when {
        name == "WHITE" -> if (chosen.light) null else Palette.PORCELAIN
        chosen.light -> Palette.NEUTRAL
        else -> null
    }

private fun readerBackground(name: String): Color =
    when (name) {
        "WHITE" -> Color(0xFFF4F4F5)
        "GRAY" -> Color(0xFF26272B)
        else -> Color.Black
    }
