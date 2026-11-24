package org.nekomanga.desktop.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Size
import coil3.transform.Transformation
import kotlinx.coroutines.launch
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.IRect
import org.nekomanga.desktop.components.ArrowButton
import org.nekomanga.desktop.components.ArrowStyle
import org.nekomanga.desktop.components.BeakoLoader
import org.nekomanga.desktop.components.GlassButton
import org.nekomanga.desktop.state.ReaderModel
import org.nekomanga.desktop.state.ReadingMode
import org.nekomanga.desktop.state.displayName
import org.nekomanga.desktop.state.pageModel
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.desktop.theme.LocalAppearance
import org.nekomanga.desktop.theme.glass
import org.nekomanga.shared.library.ReaderPrefs

/** The page area's accessible name. */
internal const val PAGE_AREA = "Page area: click the sides to turn pages, the middle for controls"

/** Height÷width assumed for a webtoon page until it has loaded. */
private const val DefaultAspect = 1.45f

// ───────────── Image requests ─────────────

/**
 * What to load for a page: with "Crop borders" a request that trims plain margins, else the page
 * itself. Preloading builds the same request, so the cache serves it.
 */
internal fun pageRequest(url: String, crop: Boolean): Any =
    if (!crop) pageModel(url)
    else
        ImageRequest.Builder(PlatformContext.INSTANCE)
            .data(pageModel(url))
            .transformations(CropBorders)
            .build()

/** Grey pages for the "Grayscale" setting. */
internal fun pageFilter(prefs: ReaderPrefs): ColorFilter? =
    if (prefs.grayscale) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    else null

/** Loads the next [ReaderPrefs.preload] pages ahead of [page], so they're ready on turning. */
@Composable
internal fun Preload(urls: List<String>, page: Int, prefs: ReaderPrefs) {
    LaunchedEffect(urls, page, prefs.preload, prefs.cropBorders) {
        val loader = SingletonImageLoader.get(PlatformContext.INSTANCE)
        for (i in 1..prefs.preload.coerceIn(1, 10)) {
            val url = urls.getOrNull(page + i) ?: break
            val data = pageRequest(url, prefs.cropBorders)
            loader.enqueue(
                data as? ImageRequest
                    ?: ImageRequest.Builder(PlatformContext.INSTANCE).data(data).build(),
            )
        }
    }
}

/**
 * Trims plain white or black margins (scan borders, letterboxing). An edge row or column counts as
 * margin when nearly all of it is within a small distance of the corner colour. Never cuts more
 * than a third from any side.
 */
internal object CropBorders : Transformation() {
    override val cacheKey = "beako.cropBorders.v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height
        if (w < 32 || h < 32) return input
        val corner = input.getColor(0, 0)
        if (!isPlain(corner)) return input
        fun rowPlain(y: Int) = (0 until w step 3).all { near(input.getColor(it, y), corner) }
        fun colPlain(x: Int, top: Int, bottom: Int) =
            (top until bottom step 3).all { near(input.getColor(x, it), corner) }
        var top = 0
        while (top < h / 3 && rowPlain(top)) top++
        var bottom = h - 1
        while (bottom > h - h / 3 && rowPlain(bottom)) bottom--
        var left = 0
        while (left < w / 3 && colPlain(left, top, bottom)) left++
        var right = w - 1
        while (right > w - w / 3 && colPlain(right, top, bottom)) right--
        // Keep a sliver of margin so text never touches the edge.
        val pad = 4
        val rect =
            IRect.makeLTRB(
                (left - pad).coerceAtLeast(0),
                (top - pad).coerceAtLeast(0),
                (right + pad + 1).coerceAtMost(w),
                (bottom + pad + 1).coerceAtMost(h),
            )
        if (rect.width >= w - 8 && rect.height >= h - 8) return input
        val out = Bitmap()
        return if (input.extractSubset(out, rect)) out else input
    }

    /** Near white or near black: the only margins worth trimming. */
    private fun isPlain(color: Int): Boolean {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        return max - min < 24 && (min > 225 || max < 30)
    }

    private fun near(a: Int, b: Int): Boolean =
        kotlin.math.abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) < 28 &&
            kotlin.math.abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) < 28 &&
            kotlin.math.abs((a and 0xFF) - (b and 0xFF)) < 28
}

// ───────────── Click zones ─────────────

/** What a click on the page does. */
internal enum class Tap {
    LEFT,
    RIGHT,
    MENU,
}

/**
 * The click-zone layouts, for clicks at ([x], [y]) as fractions of the page area: DEFAULT (outer
 * thirds turn, middle shows the controls), KINDLE (left third back, the rest forward, a strip at
 * the top for the controls), EDGE (narrow side edges turn), L_SHAPE (left column and top row one
 * way, right column and bottom row the other) or DISABLED (every click shows the controls).
 */
internal fun tapAt(zones: String, x: Float, y: Float): Tap =
    when (zones) {
        "DISABLED" -> Tap.MENU
        "KINDLE" ->
            when {
                y < 0.18f -> Tap.MENU
                x < 1 / 3f -> Tap.LEFT
                else -> Tap.RIGHT
            }
        "EDGE" ->
            when {
                x < 0.15f -> Tap.LEFT
                x > 0.85f -> Tap.RIGHT
                else -> Tap.MENU
            }
        "L_SHAPE" ->
            when {
                (x < 1 / 3f && y < 2 / 3f) || (y < 1 / 3f && x < 2 / 3f) -> Tap.LEFT
                (x > 2 / 3f && y > 1 / 3f) || (y > 2 / 3f && x > 1 / 3f) -> Tap.RIGHT
                else -> Tap.MENU
            }
        else ->
            when {
                x < 1 / 3f -> Tap.LEFT
                x > 2 / 3f -> Tap.RIGHT
                else -> Tap.MENU
            }
    }

// ───────────── Paged ─────────────

@Composable
internal fun PagedView(
    model: ReaderModel,
    urls: List<String>,
    prefs: ReaderPrefs,
    onToggleControls: () -> Unit,
) {
    val rtl = model.mode == ReadingMode.RIGHT_TO_LEFT
    val reduceMotion = LocalAppearance.current.reduceMotion
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fit = PageFit.of(prefs.fit)
        // Two pages when asked, or automatically when the window is wider than tall; only for the
        // fits that show a whole page.
        val two =
            (fit == PageFit.SCREEN || fit == PageFit.HEIGHT) &&
                when (prefs.pageLayout) {
                    "DOUBLE" -> true
                    "SINGLE" -> false
                    else -> maxWidth > maxHeight * 1.05f
                }
        SideEffect { model.twoPages = two }
        val page = model.page.coerceIn(0, urls.lastIndex)
        Preload(urls, page, prefs)
        val key = Triple(page, model.half, model.atTransition)
        AnimatedContent(
            targetState = key,
            transitionSpec = {
                if (!prefs.animateTurns || reduceMotion) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    val forward =
                        targetState.third ||
                            targetState.first > initialState.first ||
                            (targetState.first == initialState.first &&
                                targetState.second > initialState.second)
                    // Pages slide the way they're read: leftward for left-to-right.
                    val sign = if (forward != rtl) 1 else -1
                    (slideInHorizontally(tween(220)) { it / 4 * sign } + fadeIn(tween(220)))
                        .togetherWith(
                            slideOutHorizontally(tween(180)) { -it / 4 * sign } +
                                fadeOut(tween(150)),
                        )
                }
            },
            label = "page",
        ) { (shown, half, transition) ->
            when {
                transition -> TransitionPage(model)
                model.isSplit(shown) ->
                    SplitPage(
                        pageRequest(urls[shown], prefs.cropBorders),
                        "Page ${shown + 1} of ${urls.size}",
                        model.aspectOf(shown),
                        showLeft = (half == 0) != rtl,
                        prefs,
                    )
                model.pairedWithNext(shown) -> DoublePage(model, urls, shown, rtl, prefs)
                else -> FittedPage(model, urls, shown, fit, prefs)
            }
        }
        val leftForward = rtl != prefs.invertClickZones
        Box(
            Modifier.fillMaxSize()
                .pointerInput(prefs.tapZones, leftForward) {
                    detectTapGestures { offset ->
                        when (
                            tapAt(
                                prefs.tapZones,
                                offset.x / size.width,
                                offset.y / size.height,
                            )
                        ) {
                            Tap.LEFT -> model.turn(forward = leftForward)
                            Tap.RIGHT -> model.turn(forward = !leftForward)
                            Tap.MENU -> onToggleControls()
                        }
                    }
                }
                .semantics { contentDescription = PAGE_AREA },
        )
    }
}

@Composable
private fun PageImage(
    model: ReaderModel,
    url: String,
    index: Int,
    prefs: ReaderPrefs,
    modifier: Modifier,
    contentScale: ContentScale,
    alignment: Alignment = Alignment.Center,
) {
    SubcomposeAsyncImage(
        model = pageRequest(url, prefs.cropBorders),
        contentDescription = null,
        // Named on the node itself, so the page keeps its name while it loads.
        modifier =
            modifier.semantics { contentDescription = "Page ${index + 1} of ${model.pageCount}" },
        contentScale = contentScale,
        alignment = alignment,
        colorFilter = pageFilter(prefs),
        loading = { Box(Modifier.fillMaxSize(), Alignment.Center) { BeakoLoader() } },
        onSuccess = { state -> report(model, index, state) },
    )
}

private fun report(model: ReaderModel, index: Int, state: AsyncImagePainter.State.Success) {
    val image = state.result.image
    if (image.width > 0) model.reportAspect(index, image.height.toFloat() / image.width)
}

@Composable
private fun FittedPage(
    model: ReaderModel,
    urls: List<String>,
    page: Int,
    fit: PageFit,
    prefs: ReaderPrefs,
) {
    // Each page starts scrolled to its top-left corner.
    val vertical = remember(page) { ScrollState(0) }
    val horizontal = remember(page) { ScrollState(0) }
    val url = urls[page]
    when (fit) {
        PageFit.SCREEN ->
            PageImage(model, url, page, prefs, Modifier.fillMaxSize(), ContentScale.Fit)
        PageFit.WIDTH ->
            Box(Modifier.fillMaxSize().verticalScroll(vertical)) {
                PageImage(model, url, page, prefs, Modifier.fillMaxWidth(), ContentScale.FillWidth)
            }
        PageFit.HEIGHT ->
            Box(
                Modifier.fillMaxSize().horizontalScroll(horizontal),
                contentAlignment = Alignment.Center,
            ) {
                PageImage(
                    model,
                    url,
                    page,
                    prefs,
                    Modifier.fillMaxHeight(),
                    ContentScale.FillHeight,
                )
            }
        PageFit.ORIGINAL ->
            Box(Modifier.fillMaxSize().verticalScroll(vertical).horizontalScroll(horizontal)) {
                PageImage(model, url, page, prefs, Modifier, ContentScale.None)
            }
    }
}

/** Two pages side by side, meeting in the middle, in reading order. */
@Composable
private fun DoublePage(
    model: ReaderModel,
    urls: List<String>,
    first: Int,
    rtl: Boolean,
    prefs: ReaderPrefs,
) {
    // The first page goes on the left for left-to-right, on the right for right-to-left.
    val firstOnLeft = rtl == prefs.invertDouble
    val (left, right) = if (firstOnLeft) first to first + 1 else first + 1 to first
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        PageImage(
            model,
            urls[left],
            left,
            prefs,
            Modifier.weight(1f).fillMaxHeight(),
            ContentScale.Fit,
            Alignment.CenterEnd,
        )
        Spacer(Modifier.width(prefs.doubleGap.coerceIn(0, 48).dp))
        PageImage(
            model,
            urls[right],
            right,
            prefs,
            Modifier.weight(1f).fillMaxHeight(),
            ContentScale.Fit,
            Alignment.CenterStart,
        )
    }
}

/** One half of a double-page spread, as large as fits the window. */
@Composable
private fun SplitPage(
    data: Any,
    description: String,
    aspect: Float,
    showLeft: Boolean,
    prefs: ReaderPrefs,
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // A half is twice as tall, relative to its width, as the whole spread.
        val halfAspect = (aspect * 2).coerceAtLeast(0.1f)
        val widthLimited = maxHeight / maxWidth > halfAspect
        val halfWidth = if (widthLimited) maxWidth else maxHeight / halfAspect
        val height = if (widthLimited) maxWidth * halfAspect else maxHeight
        Box(Modifier.size(halfWidth, height).clipToBounds()) {
            SubcomposeAsyncImage(
                data,
                "$description, ${if (showLeft) "left" else "right"} half",
                Modifier.requiredSize(halfWidth * 2, height)
                    .offset(x = if (showLeft) halfWidth / 2 else -halfWidth / 2),
                contentScale = ContentScale.FillBounds,
                colorFilter = pageFilter(prefs),
                loading = { Box(Modifier.fillMaxSize(), Alignment.Center) { BeakoLoader() } },
            )
        }
    }
}

/** Between chapters: what you finished and what's next. */
@Composable
private fun TransitionPage(model: ReaderModel) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.glass(
                    RoundedCornerShape(24.dp),
                    tint = BeakoColors.GlassSolid.copy(alpha = 0.85f),
                )
                .padding(horizontal = 32.dp, vertical = 28.dp)
                .width(420.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Finished",
                style = MaterialTheme.typography.labelMedium,
                color = BeakoColors.TextMuted,
            )
            Text(
                model.chapter.displayName(),
                style = MaterialTheme.typography.titleLarge,
                color = BeakoColors.Text,
            )
            Spacer(Modifier.size(6.dp))
            if (model.hasNext) {
                val next = model.chapters[model.nextIndex]
                Text(
                    "Next",
                    style = MaterialTheme.typography.labelMedium,
                    color = BeakoColors.TextMuted,
                )
                Text(
                    next.displayName(),
                    style = MaterialTheme.typography.titleLarge,
                    color = BeakoColors.Text,
                )
                if (model.nextIndex > model.index + 1) {
                    Text(
                        "Skipping ${model.nextIndex - model.index - 1} chapters you've read.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BeakoColors.TextMuted,
                    )
                }
                Spacer(Modifier.size(8.dp))
                ArrowButton("Next chapter", model::nextChapter, style = ArrowStyle.ACCENT)
            } else {
                Text(
                    "You’re all caught up.",
                    style = MaterialTheme.typography.titleMedium,
                    color = BeakoColors.Text,
                )
            }
            Text(
                "Turn the page to carry on, or back to return to the last page.",
                style = MaterialTheme.typography.bodySmall,
                color = BeakoColors.TextMuted,
            )
        }
    }
}

// ───────────── Webtoon ─────────────

@Composable
internal fun WebtoonStrip(
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
    val scope = rememberCoroutineScope()
    LaunchedEffect(listState, urls) {
        listState.scrollToItem(model.page.coerceIn(0, urls.lastIndex))
        positioned = true
    }
    // How far the strip must move before the controls hide or show.
    val threshold =
        when (prefs.hideOnScroll) {
            "LOW" -> 64
            "HIGH" -> 6
            else -> 20
        }
    LaunchedEffect(listState, threshold) {
        var last = 0L
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
            .collect { (item, offset) ->
                if (!positioned) return@collect
                model.page = item.coerceAtMost(urls.lastIndex)
                // Position along the strip; only real movement counts as a direction.
                val position = item * 100_000L + offset
                if (position > last + threshold) onScrollDirection(true)
                else if (position < last - threshold) onScrollDirection(false)
                if (kotlin.math.abs(position - last) > threshold) last = position
            }
    }
    Preload(urls, model.page, prefs)
    LazyColumn(
        state = listState,
        modifier =
            Modifier.fillMaxSize().pointerInput(prefs.tapZones) {
                detectTapGestures { offset ->
                    val x = offset.x / size.width
                    val y = offset.y / size.height
                    // Default zones run top to bottom here: top third back, bottom third on.
                    val tap =
                        if (prefs.tapZones == "DEFAULT")
                            when {
                                y < 1 / 3f -> Tap.LEFT
                                y > 2 / 3f -> Tap.RIGHT
                                else -> Tap.MENU
                            }
                        else tapAt(prefs.tapZones, x, y)
                    val height = listState.layoutInfo.viewportSize.height
                    when (tap) {
                        Tap.LEFT -> scope.launch { listState.animateScrollBy(-height * 0.8f) }
                        Tap.RIGHT -> scope.launch { listState.animateScrollBy(height * 0.8f) }
                        Tap.MENU -> onToggleControls()
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(prefs.pageGap.coerceIn(0, 32).dp),
    ) {
        itemsIndexed(urls, key = { _, url -> url }) { index, url ->
            SubcomposeAsyncImage(
                model = pageRequest(url, prefs.cropBorders),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                colorFilter = pageFilter(prefs),
                loading = { Box(Modifier.fillMaxSize(), Alignment.Center) { BeakoLoader() } },
                onSuccess = { state ->
                    val image = state.result.image
                    if (image.width > 0) {
                        aspects[index] = image.height.toFloat() / image.width
                        model.reportAspect(index, aspects[index]!!)
                    }
                },
                modifier =
                    Modifier.fillMaxWidth(prefs.webtoonWidth.coerceIn(30, 100) / 100f)
                        .aspectRatio(1f / (aspects[index] ?: DefaultAspect))
                        .semantics { contentDescription = "Page ${index + 1} of ${urls.size}" },
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

/** A small page number shown while the controls are hidden. */
@Composable
internal fun PageNumber(model: ReaderModel, modifier: Modifier = Modifier) {
    val count = model.pageCount
    if (count == 0) return
    val page = model.page.coerceIn(0, count - 1)
    val text =
        if (model.pairedWithNext(page)) "${page + 1}–${page + 2} / $count"
        else "${page + 1} / $count"
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = BeakoColors.Text,
        modifier =
            modifier
                .padding(10.dp)
                .glass(RoundedCornerShape(10.dp), tint = BeakoColors.GlassSolid.copy(alpha = 0.7f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
