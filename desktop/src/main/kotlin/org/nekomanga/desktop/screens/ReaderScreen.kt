package org.nekomanga.desktop.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.awt.Desktop
import java.net.URI
import kotlinx.coroutines.launch
import org.nekomanga.desktop.components.DropletSegmented
import org.nekomanga.desktop.components.GlassButton
import org.nekomanga.desktop.components.GlassIconButton
import org.nekomanga.desktop.components.StateMessage
import org.nekomanga.desktop.state.KeyBindings
import org.nekomanga.desktop.state.Loadable
import org.nekomanga.desktop.state.LocalKeyRouter
import org.nekomanga.desktop.state.LocalWindowControl
import org.nekomanga.desktop.state.ReaderAction
import org.nekomanga.desktop.state.ReaderModel
import org.nekomanga.desktop.state.ReadingMode
import org.nekomanga.desktop.state.displayName
import org.nekomanga.desktop.state.pageModel
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.desktop.theme.LocalAppearance
import org.nekomanga.desktop.theme.glass
import org.nekomanga.shared.library.ReaderPrefs

/** Height÷width assumed for a webtoon page until it has loaded. */
private const val DefaultAspect = 1.45f

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
    val window = LocalWindowControl.current
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
                Text(
                    "Loading ${chapter.displayName()}…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = BeakoColors.TextMuted,
                    modifier = Modifier.align(Alignment.Center),
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
                        Modifier.glass(RoundedCornerShape(14.dp), tint = Color(0xB3101114))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

private fun openInBrowser(url: String) {
    runCatching { if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url)) }
}

@Composable
private fun WebtoonStrip(
    model: ReaderModel,
    urls: List<String>,
    listState: LazyListState,
    prefs: ReaderPrefs,
    onToggleControls: () -> Unit,
    onScrollDirection: (down: Boolean) -> Unit,
) {
    // Height÷width of each loaded page, so a page keeps its size once known.
    val aspects = remember(model.index) { mutableStateMapOf<Int, Float>() }
    // Open at the saved page (or the last page, when paging back from the next chapter) before
    // scroll positions count as reading.
    var positioned by remember(model.index) { mutableStateOf(false) }
    LaunchedEffect(listState, urls) {
        listState.scrollToItem(model.page.coerceIn(0, urls.lastIndex))
        positioned = true
    }
    LaunchedEffect(listState) {
        var last = 0L
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
            .collect { (item, offset) ->
                if (!positioned) return@collect
                model.page = item.coerceAtMost(urls.lastIndex)
                // Position along the strip; only real movement counts as a direction.
                val position = item * 100_000L + offset
                if (position > last + 12) onScrollDirection(true)
                else if (position < last - 12) onScrollDirection(false)
                last = position
            }
    }
    LazyColumn(
        state = listState,
        modifier =
            Modifier.fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleControls,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(prefs.pageGap.coerceIn(0, 32).dp),
    ) {
        itemsIndexed(urls, key = { _, url -> url }) { index, url ->
            AsyncImage(
                model = pageModel(url),
                contentDescription = "Page ${index + 1} of ${urls.size}",
                contentScale = ContentScale.FillWidth,
                onSuccess = { state ->
                    val image = state.result.image
                    if (image.width > 0) {
                        aspects[index] = image.height.toFloat() / image.width
                        model.reportAspect(index, aspects[index]!!)
                    }
                },
                modifier =
                    Modifier.fillMaxWidth(prefs.webtoonWidth.coerceIn(30, 100) / 100f)
                        .aspectRatio(1f / (aspects[index] ?: DefaultAspect)),
            )
        }
        item("end") {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (model.hasNext) {
                    GlassButton("Next chapter", onClick = model::nextChapter, primary = true)
                } else {
                    Text(
                        "You’re all caught up",
                        style = MaterialTheme.typography.titleLarge,
                        color = BeakoColors.TextMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun PagedView(
    model: ReaderModel,
    urls: List<String>,
    prefs: ReaderPrefs,
    onToggleControls: () -> Unit,
) {
    val rtl = model.mode == ReadingMode.RIGHT_TO_LEFT
    val page = model.page.coerceIn(0, urls.lastIndex)
    val report: (coil3.compose.AsyncImagePainter.State.Success) -> Unit = { state ->
        val image = state.result.image
        if (image.width > 0) model.reportAspect(page, image.height.toFloat() / image.width)
    }
    Box(Modifier.fillMaxSize()) {
        val url = pageModel(urls[page])
        val description = "Page ${page + 1} of ${urls.size}"
        if (model.isSplit(page)) {
            // The half read first is on the left for left-to-right, on the right for right-to-left.
            SplitPage(url, description, model.aspectOf(page), showLeft = (model.half == 0) != rtl)
        } else {
            FittedPage(url, description, page, PageFit.of(prefs.fit), report)
        }
        // Read ahead, so the next page is ready when it is turned to.
        urls.getOrNull(page + 1)?.let {
            AsyncImage(
                model = pageModel(it),
                contentDescription = null,
                modifier = Modifier.height(1.dp),
            )
        }
        // Click zones: the outer thirds turn the page, the middle shows or hides the controls.
        val leftForward = rtl != prefs.invertClickZones
        Row(Modifier.fillMaxSize()) {
            ClickZone(0.33f, "Left side: turn the page") { model.turn(forward = leftForward) }
            ClickZone(0.5f, "Show or hide controls", onToggleControls)
            ClickZone(1f, "Right side: turn the page") { model.turn(forward = !leftForward) }
        }
    }
}

/** How a page fits the window in the paged modes. */
enum class PageFit(val label: String) {
    SCREEN("Fit screen"),
    WIDTH("Fit width"),
    HEIGHT("Fit height"),
    ORIGINAL("Original size");

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: SCREEN
    }
}

@Composable
private fun FittedPage(
    url: Any,
    description: String,
    page: Int,
    fit: PageFit,
    onLoaded: (coil3.compose.AsyncImagePainter.State.Success) -> Unit,
) {
    // Each page starts scrolled to its top-left corner.
    val vertical = remember(page) { ScrollState(0) }
    val horizontal = remember(page) { ScrollState(0) }
    when (fit) {
        PageFit.SCREEN ->
            AsyncImage(
                url,
                description,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                onSuccess = onLoaded,
            )
        PageFit.WIDTH ->
            Box(Modifier.fillMaxSize().verticalScroll(vertical)) {
                AsyncImage(
                    url,
                    description,
                    Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth,
                    onSuccess = onLoaded,
                )
            }
        PageFit.HEIGHT ->
            Box(
                Modifier.fillMaxSize().horizontalScroll(horizontal),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    url,
                    description,
                    Modifier.fillMaxHeight(),
                    contentScale = ContentScale.FillHeight,
                    onSuccess = onLoaded,
                )
            }
        PageFit.ORIGINAL ->
            Box(Modifier.fillMaxSize().verticalScroll(vertical).horizontalScroll(horizontal)) {
                AsyncImage(url, description, contentScale = ContentScale.None, onSuccess = onLoaded)
            }
    }
}

/** One half of a double-page spread, as large as fits the window. */
@Composable
private fun SplitPage(url: Any, description: String, aspect: Float, showLeft: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // A half is twice as tall, relative to its width, as the whole spread.
        val halfAspect = (aspect * 2).coerceAtLeast(0.1f)
        val widthLimited = maxHeight / maxWidth > halfAspect
        val halfWidth = if (widthLimited) maxWidth else maxHeight / halfAspect
        val height = if (widthLimited) maxWidth * halfAspect else maxHeight
        Box(Modifier.size(halfWidth, height).clipToBounds()) {
            AsyncImage(
                url,
                "$description, ${if (showLeft) "left" else "right"} half",
                Modifier.requiredSize(halfWidth * 2, height)
                    .offset(x = if (showLeft) halfWidth / 2 else -halfWidth / 2),
                contentScale = ContentScale.FillBounds,
            )
        }
    }
}

@Composable
private fun ClickZone(fraction: Float, description: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxHeight()
            .fillMaxWidth(fraction)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
    )
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
                .glass(shape, tint = Color(0xB3101114))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Close reader (Esc)", onClose)
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
                .glass(shape, tint = Color(0xB3101114))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (model.hasPrevious) {
                GlassIconButton(
                    Icons.Outlined.ChevronLeft,
                    "Previous chapter${keys.hint(ReaderAction.PREVIOUS_CHAPTER)}",
                    { model.previousChapter() },
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
                )
            }
        }
    }
}

private fun readerBackground(name: String): Color =
    when (name) {
        "WHITE" -> Color(0xFFF4F4F5)
        "GRAY" -> Color(0xFF26272B)
        else -> Color.Black
    }
