package org.nekomanga.desktop.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
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
import org.nekomanga.desktop.state.Loadable
import org.nekomanga.desktop.state.LocalWindowControl
import org.nekomanga.desktop.state.ReaderModel
import org.nekomanga.desktop.state.ReadingMode
import org.nekomanga.desktop.state.displayName
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.desktop.theme.LocalAppearance
import org.nekomanga.desktop.theme.glass

/** Height÷width assumed for a webtoon page until it has loaded. */
private const val DefaultAspect = 1.45f

/**
 * The reader. Webtoon mode scrolls a long strip and hides the controls while scrolling down,
 * showing them again on scrolling up. Paged modes turn with clicks, the wheel or the keyboard.
 *
 * Keys: ← → Space PageUp PageDown turn or scroll, `[` `]` change chapter, F or F11 toggle full
 * screen, Esc leaves full screen first and then closes the reader.
 */
@Composable
fun ReaderScreen(model: ReaderModel, onClose: () -> Unit) {
    LaunchedEffect(model) { model.start() }
    val window = LocalWindowControl.current
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var controls by remember { mutableStateOf(true) }
    val listState = remember(model.index) { LazyListState() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun close() {
        window?.setFullscreen(false)
        onClose()
    }
    fun scroll(pagesOfWindow: Float) {
        scope.launch {
            listState.animateScrollBy(listState.layoutInfo.viewportSize.height * pagesOfWindow)
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val rtl = model.mode == ReadingMode.RIGHT_TO_LEFT
                val paged = model.mode.isPaged
                when (event.key) {
                    Key.Escape ->
                        if (window?.isFullscreen == true) window.setFullscreen(false) else close()
                    Key.F,
                    Key.F11 -> window?.toggleFullscreen()
                    Key.LeftBracket -> model.previousChapter()
                    Key.RightBracket -> model.nextChapter()
                    Key.DirectionRight -> if (paged) model.turn(forward = !rtl) else scroll(0.85f)
                    Key.DirectionLeft -> if (paged) model.turn(forward = rtl) else scroll(-0.85f)
                    Key.Spacebar,
                    Key.PageDown,
                    Key.DirectionDown -> if (paged) model.turn(true) else scroll(0.85f)
                    Key.PageUp,
                    Key.DirectionUp -> if (paged) model.turn(false) else scroll(-0.85f)
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
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
                            onToggleControls = { controls = !controls },
                            onScrollDirection = { down -> controls = !down },
                        )
                    else ->
                        PagedView(
                            model,
                            pages.value.urls,
                            onToggleControls = { controls = !controls },
                        )
                }
        }
        ReaderControls(
            model,
            visible = controls || model.pages !is Loadable.Ready,
            onClose = ::close,
        )
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
    onToggleControls: () -> Unit,
    onScrollDirection: (down: Boolean) -> Unit,
) {
    // Height÷width of each loaded page, so a page keeps its size once known.
    val aspects = remember(model.index) { mutableStateMapOf<Int, Float>() }
    LaunchedEffect(listState) {
        var last = 0L
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
            .collect { (item, offset) ->
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
    ) {
        itemsIndexed(urls, key = { _, url -> url }) { index, url ->
            AsyncImage(
                model = url,
                contentDescription = "Page ${index + 1} of ${urls.size}",
                contentScale = ContentScale.FillWidth,
                onSuccess = { state ->
                    val image = state.result.image
                    if (image.width > 0) aspects[index] = image.height.toFloat() / image.width
                },
                modifier =
                    Modifier.fillMaxWidth(0.62f)
                        .widthIn(max = 980.dp)
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
private fun PagedView(model: ReaderModel, urls: List<String>, onToggleControls: () -> Unit) {
    val rtl = model.mode == ReadingMode.RIGHT_TO_LEFT
    val page = model.page.coerceIn(0, urls.lastIndex)
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = urls[page],
            contentDescription = "Page ${page + 1} of ${urls.size}",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        // Read ahead, so the next page is ready when it is turned to.
        urls.getOrNull(page + 1)?.let {
            AsyncImage(model = it, contentDescription = null, modifier = Modifier.height(1.dp))
        }
        // Click zones: the outer thirds turn the page, the middle shows or hides the controls.
        Row(Modifier.fillMaxSize()) {
            ClickZone(0.33f, "Previous side") { model.turn(forward = rtl) }
            ClickZone(0.5f, "Show or hide controls", onToggleControls)
            ClickZone(1f, "Next side") { model.turn(forward = !rtl) }
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
private fun BoxScope.ReaderControls(model: ReaderModel, visible: Boolean, onClose: () -> Unit) {
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
                    "Previous chapter ([)",
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
                GlassIconButton(Icons.Outlined.ChevronRight, "Next chapter (])", model::nextChapter)
            }
        }
    }
}
