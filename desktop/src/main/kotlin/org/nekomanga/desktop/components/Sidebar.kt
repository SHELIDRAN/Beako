package org.nekomanga.desktop.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import org.jetbrains.skia.Image as SkiaImage
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.desktop.theme.GlassSurface
import org.nekomanga.desktop.theme.LocalAppearance
import org.nekomanga.desktop.theme.Motion
import org.nekomanga.desktop.theme.Radius
import org.nekomanga.desktop.theme.ShadowSpec
import org.nekomanga.desktop.theme.SidebarMode
import org.nekomanga.desktop.theme.dropShadows
import org.nekomanga.desktop.theme.dropletSpec
import org.nekomanga.desktop.theme.focusRing
import org.nekomanga.desktop.theme.press

/** The app's top-level screens, in sidebar order. */
enum class Destination(val label: String, val icon: ImageVector) {
    LIBRARY("Library", Icons.Outlined.CollectionsBookmark),
    UPDATES("Latest updates", Icons.Outlined.Update),
    BROWSE("Browse", Icons.Outlined.Explore),
    HISTORY("Reading history", Icons.Outlined.History),
    MORE("More", Icons.Outlined.MoreHoriz),
}

/** Width of the collapsed rail, including its outer margin: what screens always reserve. */
val SidebarRailWidth = 84.dp

/** Width the screens reserve when the menu is kept open (panel plus its margins). */
val SidebarPanelReserve = 264.dp

private val RailWidth = 68.dp
private val PanelWidth = 248.dp
private val RowHeight = 40.dp
private const val ExpandDelayMillis = 150L
private const val CollapseDelayMillis = 400L
private const val WidthMillis = 260

/** Where the selection droplet sits: a row of the menu. */
private object Rows {
    const val SEARCH_CHILD = "browse/search"
    const val DISCOVER = "browse/discover"
    const val FOOT = "more"
}

/** Ink circle offset so an icon sits centred in the 68 dp rail whatever the panel's width. */
private val IconInset = 13.dp

/**
 * The glass navigation menu down the left edge. Expanded it is the full panel: the logo and a
 * collapse control, a search with its shortcut, the Main destinations (Browse opens into Discover
 * and Search), the Accounts list and More at the foot. Collapsed it is a 68 dp rail of icons, where
 * Browse's pages open in a flyout. The Sidebar setting picks when it expands.
 */
@Composable
fun Sidebar(
    selected: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    accounts: List<AccountSlot> = DefaultAccountSlots,
    onAccountClick: (String) -> Unit = {},
    /** True while the Search page is open over Browse. */
    searchOpen: Boolean = false,
    onSearch: () -> Unit = {},
    /** Small counts shown at the end of a row, e.g. new chapters on Latest updates. */
    counts: Map<Destination, Int> = emptyMap(),
    /** Destinations with something new since they were last opened: an accent dot on the icon. */
    fresh: Set<Destination> = emptySet(),
    onModeChange: (SidebarMode) -> Unit = {},
) {
    val appearance = LocalAppearance.current
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    val wantExpanded =
        when (appearance.sidebarMode) {
            SidebarMode.ALWAYS_EXPANDED -> true
            SidebarMode.ICONS_ONLY -> false
            SidebarMode.AUTO_COLLAPSE -> hovered || focused
        }
    var expanded by remember { mutableStateOf(wantExpanded) }
    LaunchedEffect(wantExpanded, appearance.sidebarMode) {
        if (appearance.sidebarMode == SidebarMode.AUTO_COLLAPSE) {
            delay(if (wantExpanded) ExpandDelayMillis else CollapseDelayMillis)
        }
        expanded = wantExpanded
    }
    val width by
        animateDpAsState(
            if (expanded) PanelWidth else RailWidth,
            if (appearance.reduceMotion) snap() else tween(WidthMillis, easing = Motion.InOut),
        )

    var browseOpen by remember { mutableStateOf(selected == Destination.BROWSE) }
    LaunchedEffect(selected) { if (selected == Destination.BROWSE) browseOpen = true }
    val treeShown = expanded && browseOpen
    val current =
        when {
            selected == Destination.BROWSE && searchOpen ->
                if (treeShown) Rows.SEARCH_CHILD else Destination.BROWSE.name
            selected == Destination.BROWSE -> if (treeShown) Rows.DISCOVER else selected.name
            selected == Destination.MORE -> Rows.FOOT
            else -> selected.name
        }

    // Rows report where they are, so the droplet can slide between them.
    val rects = remember { mutableStateMapOf<String, Rect>() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val place: (String) -> Modifier = { key ->
        Modifier.onGloballyPositioned {
            val r = it.boundsInWindow()
            if (rects[key] != r) rects[key] = r
        }
    }
    val target = rects[current]?.translate(-origin)
    val droplet by animateRectAsState(target ?: Rect.Zero, dropletSpec(), label = "droplet")
    val counter = remember { RowCounter() }
    counter.reset()

    GlassSurface(
        shape = RoundedCornerShape(Radius.widget),
        modifier =
            modifier
                .width(width)
                .hoverable(hover)
                .onFocusChanged { focused = it.hasFocus }
                .semantics { contentDescription = "Main menu" },
    ) {
        Box(
            Modifier.fillMaxHeight().onGloballyPositioned {
                val p = it.positionInWindow()
                if (origin != p) origin = p
            },
        ) {
            if (target != null) {
                Box(
                    Modifier.offset { IntOffset(droplet.left.toInt(), droplet.top.toInt()) }
                        .size(
                            with(LocalDensity.current) { droplet.width.toDp() },
                            with(LocalDensity.current) { droplet.height.toDp() },
                        )
                        .dropShadow(CircleShape, ShadowSpec.droplet())
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(BeakoColors.DropletTop, BeakoColors.DropletBottom),
                            ),
                        )
                        .border(1.dp, BeakoColors.DropletTop, CircleShape),
                )
            }
            Column(Modifier.fillMaxHeight().padding(horizontal = 12.dp, vertical = 16.dp)) {
                Head(expanded, appearance.sidebarMode, onModeChange)
                Spacer(Modifier.height(8.dp))
                SearchRow(expanded, onSearch)
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    SectionHeader("Main", expanded)
                    for (destination in Destination.entries) {
                        if (destination == Destination.MORE) continue
                        if (destination == Destination.BROWSE) {
                            BrowseGroup(
                                expanded = expanded,
                                open = browseOpen,
                                treeShown = treeShown,
                                selected = selected == Destination.BROWSE,
                                current = current,
                                counter = counter,
                                place = place,
                                onParent = {
                                    if (selected != Destination.BROWSE || searchOpen) {
                                        onSelect(Destination.BROWSE)
                                        browseOpen = true
                                    } else browseOpen = !browseOpen
                                },
                                onDiscover = { onSelect(Destination.BROWSE) },
                                onSearch = onSearch,
                            )
                            continue
                        }
                        MenuRow(
                            label = destination.label,
                            expanded = expanded,
                            index = counter.next(),
                            onClick = { onSelect(destination) },
                            modifier = place(destination.name),
                            leading = { RowIcon(destination.icon, destination == selected) },
                            selected = destination == selected,
                            dot = destination in fresh,
                            count = counts[destination],
                        )
                    }
                    SectionHeader(
                        "Accounts",
                        expanded,
                        action = Icons.Outlined.Add to "Add an account",
                        onAction = { onAccountClick("") },
                    )
                    accounts.forEach { slot ->
                        val signedIn = slot.name != null
                        MenuRow(
                            label = slot.service,
                            sub = slot.name ?: "Not connected",
                            expanded = expanded,
                            index = counter.next(),
                            onClick = { onAccountClick(slot.service) },
                            leading = { Avatar(slot) },
                            description =
                                if (signedIn) "${slot.service}: signed in as ${slot.name}"
                                else "Connect ${slot.service}",
                            role = Role.Button,
                        )
                    }
                }
                Foot(
                    accounts.firstOrNull { it.name != null },
                    expanded = expanded,
                    selected = selected == Destination.MORE,
                    index = counter.next(),
                    modifier = place(Rows.FOOT),
                    onClick = { onSelect(Destination.MORE) },
                )
            }
        }
    }
}

/** Numbers the rows in reading order, so labels are uncovered row by row as the panel widens. */
private class RowCounter {
    private var n = 0

    fun reset() {
        n = 0
    }

    fun next() = n++
}

@Composable
private fun Head(expanded: Boolean, mode: SidebarMode, onModeChange: (SidebarMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(RowHeight).padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val logo = remember { appLogo() }
        val logoModifier = Modifier.size(28.dp).clip(CircleShape)
        if (!expanded) {
            // On the rail the logo is the expand control.
            Box(
                logoModifier
                    .clickable(role = Role.Button) { onModeChange(SidebarMode.ALWAYS_EXPANDED) }
                    .semantics { contentDescription = "Keep menu open" },
            ) {
                LogoImage(logo)
            }
            return@Row
        }
        Box(logoModifier) { LogoImage(logo) }
        Text(
            "Beako",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
            color = BeakoColors.Text,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.weight(1f),
        )
        val pinned = mode == SidebarMode.ALWAYS_EXPANDED
        Tooltip(if (pinned) "Collapse menu" else "Keep menu open") {
            Box(
                Modifier.size(28.dp)
                    .clip(CircleShape)
                    .background(BeakoColors.ChipFill)
                    .clickable(role = Role.Button) {
                        onModeChange(
                            if (pinned) SidebarMode.AUTO_COLLAPSE else SidebarMode.ALWAYS_EXPANDED,
                        )
                    }
                    .semantics {
                        contentDescription = if (pinned) "Collapse menu" else "Keep menu open"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (pinned) Icons.Outlined.KeyboardDoubleArrowLeft else Icons.Outlined.PushPin,
                    null,
                    tint = BeakoColors.TextMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun LogoImage(logo: ImageBitmap?) {
    if (logo != null) {
        Image(logo, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    } else {
        Box(Modifier.fillMaxSize().background(BeakoColors.ChipFill))
    }
}

/** The app icon, cropped to a circle where it is shown small. */
private fun appLogo(): ImageBitmap? = runCatching {
    val bytes =
        Thread.currentThread().contextClassLoader.getResourceAsStream("neko-icon.png")!!.use {
            it.readBytes()
        }
    SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
}
    .getOrNull()

@Composable
private fun SearchRow(expanded: Boolean, onSearch: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(Radius.field)
    Row(
        Modifier.press(interaction)
            .fillMaxWidth()
            .height(36.dp)
            .focusRing(interaction, shape)
            .clip(shape)
            .background(BeakoColors.ChipFill)
            .border(1.dp, BeakoColors.Rim, shape)
            .hoverSweep(interaction)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onSearch,
            )
            .semantics { contentDescription = "Search Comick (Ctrl+K)" }
            .padding(start = IconInset, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Outlined.Search,
            null,
            tint = BeakoColors.TextMuted,
            modifier = Modifier.size(18.dp),
        )
        if (expanded) {
            Text(
                "Search",
                style = MaterialTheme.typography.bodyMedium,
                color = BeakoColors.TextMuted,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.weight(1f).reveal(expanded, 0),
            )
            Kbd("Ctrl K", Modifier.reveal(expanded, 0))
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    expanded: Boolean,
    action: Pair<ImageVector, String>? = null,
    onAction: () -> Unit = {},
) {
    if (!expanded) {
        // On the rail a section is a short hairline: its title doesn't fit in 44 dp.
        val rim = BeakoColors.Rim
        Box(
            Modifier.fillMaxWidth().height(28.dp).semantics { contentDescription = title },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = 20.dp, height = 1.dp).background(rim))
        }
        return
    }
    Row(
        Modifier.fillMaxWidth()
            .height(28.dp)
            .padding(top = 4.dp, start = if (expanded) 10.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
    ) {
        Text(
            title.uppercase(),
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (expanded) 11.sp else 9.sp,
                    letterSpacing = if (expanded) 0.6.sp else 0.3.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                ),
            color = BeakoColors.TextMuted,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = if (expanded) Modifier.weight(1f) else Modifier,
        )
        if (expanded && action != null) {
            Tooltip(action.second) {
                Box(
                    Modifier.size(24.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onAction)
                        .semantics { contentDescription = action.second },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        action.first,
                        null,
                        tint = BeakoColors.TextMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/**
 * Labels are uncovered left to right as the panel widens, each row 22 ms after the one above, with
 * a 3 px blur clearing. They hide at once when it narrows.
 */
@Composable
private fun Modifier.reveal(expanded: Boolean, index: Int): Modifier {
    val reduceMotion = LocalAppearance.current.reduceMotion
    val progress by
        animateFloatAsState(
            if (expanded) 1f else 0f,
            when {
                reduceMotion -> snap()
                expanded -> tween(220, delayMillis = 60 + index * 22, easing = Motion.Out)
                else -> tween(90)
            },
            label = "reveal",
        )
    return graphicsLayer { alpha = progress }
        .blur((3f * (1f - progress)).dp)
        .drawWithContent {
            clipRect(right = size.width * progress) { this@drawWithContent.drawContent() }
        }
}

@Composable
private fun RowIcon(icon: ImageVector, active: Boolean, onInk: Boolean = false) {
    Icon(
        icon,
        null,
        tint =
            when {
                onInk -> BeakoColors.InkOnFill
                active -> BeakoColors.Text
                else -> BeakoColors.TextMuted
            },
        modifier = Modifier.size(18.dp),
    )
}

/**
 * One row of the menu: an icon, a label (and an optional line under it), then a count or
 * [trailing].
 */
@Composable
private fun MenuRow(
    label: String,
    expanded: Boolean,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    sub: String? = null,
    selected: Boolean? = null,
    dot: Boolean = false,
    count: Int? = null,
    height: Dp = RowHeight,
    /** The open group's parent: an ink pill. */
    ink: Boolean = false,
    description: String = label,
    role: Role = Role.Tab,
    state: String? = null,
    startPadding: Dp = IconInset,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = CircleShape
    // On the rail only the icon shows, so it gets its name as a tooltip.
    Tooltip(if (expanded) null else description) {
        Row(
            modifier
                .press(interaction)
                .fillMaxWidth()
                .height(height)
                .focusRing(interaction, shape)
                .clip(shape)
                .then(if (ink) Modifier.background(BeakoColors.InkFill) else Modifier)
                .hoverSweep(interaction)
                .hoverable(interaction)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = role,
                    onClick = onClick,
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    if (selected != null) selectedState(selected)
                    if (state != null) stateDescription = state
                }
                .padding(start = startPadding, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (leading != null) {
                Box {
                    leading()
                    if (dot) {
                        Box(
                            Modifier.align(Alignment.TopEnd)
                                .offset(x = 3.dp, y = (-3).dp)
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(BeakoColors.Surface)
                                .padding(1.dp)
                                .clip(CircleShape)
                                .background(BeakoColors.Accent)
                                .semantics { contentDescription = "new" },
                        )
                    }
                }
            }
            if (expanded) {
                Column(Modifier.weight(1f).reveal(expanded, index)) {
                    Text(
                        label,
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            ),
                        color =
                            when {
                                ink -> BeakoColors.InkOnFill
                                leading == null && selected != true -> BeakoColors.TextMuted
                                else -> BeakoColors.Text
                            },
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (sub != null) {
                        Text(
                            sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = BeakoColors.TextMuted,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (count != null && count > 0) {
                    Text(
                        if (count > 999) "999+" else count.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = BeakoColors.InkOnFill,
                        modifier =
                            Modifier.reveal(expanded, index)
                                .defaultMinSize(minWidth = 22.dp)
                                .clip(CircleShape)
                                .background(BeakoColors.InkFill)
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                if (trailing != null) Box(Modifier.reveal(expanded, index)) { trailing() }
            }
        }
    }
}

/**
 * Browse and its pages. Expanded, the parent folds open (+ turns into −) and its pages hang off a
 * tree line; on the rail they open in a flyout that blooms out of the icon.
 */
@Composable
private fun ColumnScope.BrowseGroup(
    expanded: Boolean,
    open: Boolean,
    treeShown: Boolean,
    selected: Boolean,
    current: String,
    counter: RowCounter,
    place: (String) -> Modifier,
    onParent: () -> Unit,
    onDiscover: () -> Unit,
    onSearch: () -> Unit,
) {
    val reduceMotion = LocalAppearance.current.reduceMotion
    val index = counter.next()
    var flyout by remember { mutableStateOf(false) }
    val anchorHover = remember { MutableInteractionSource() }
    val anchorHovered by anchorHover.collectIsHoveredAsState()
    var anchorFocused by remember { mutableStateOf(false) }
    val flyoutHover = remember { MutableInteractionSource() }
    val flyoutHovered by flyoutHover.collectIsHoveredAsState()
    LaunchedEffect(expanded, anchorHovered, anchorFocused, flyoutHovered) {
        val want = !expanded && (anchorHovered || anchorFocused || flyoutHovered)
        if (!want) delay(200)
        flyout = want
    }
    val pages = listOf("Discover" to Rows.DISCOVER, "Search with filters" to Rows.SEARCH_CHILD)
    Box {
        MenuRow(
            label = Destination.BROWSE.label,
            expanded = expanded,
            index = index,
            onClick = onParent,
            modifier =
                // Focus opens the flyout on the rail too, so keyboard users reach the pages.
                place(Destination.BROWSE.name).hoverable(anchorHover).onFocusChanged {
                    anchorFocused = it.hasFocus
                },
            leading = { RowIcon(Destination.BROWSE.icon, selected, onInk = treeShown) },
            selected = selected,
            ink = treeShown,
            state = if (expanded) (if (open) "Expanded" else "Collapsed") else null,
            trailing = {
                PlusMinusGlyph(
                    minus = open,
                    color = if (treeShown) BeakoColors.InkOnFill else BeakoColors.TextMuted,
                )
            },
        )
        if (flyout) {
            val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
            Popup(popupPositionProvider = remember(gap) { RightOfAnchor(gap) }) {
                Flyout(pages, current, flyoutHover) { key ->
                    if (key == Rows.DISCOVER) onDiscover() else onSearch()
                    flyout = false
                }
            }
        }
    }
    AnimatedVisibility(
        visible = treeShown,
        enter =
            if (reduceMotion) fadeIn(snap())
            else expandVertically(tween(360, easing = Motion.InOut)) + fadeIn(tween(200)),
        exit =
            if (reduceMotion) fadeOut(snap())
            else shrinkVertically(tween(360, easing = Motion.InOut)) + fadeOut(tween(120)),
    ) {
        val line = BeakoColors.TreeLine
        Column(
            Modifier.padding(start = 21.dp, top = 4.dp, bottom = 4.dp)
                .drawBehind {
                    drawRect(
                        line,
                        topLeft = Offset(0f, 4.dp.toPx()),
                        size =
                            androidx.compose.ui.geometry.Size(
                                1.dp.toPx(),
                                size.height - 8.dp.toPx(),
                            ),
                    )
                }
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            pages.forEach { (name, key) ->
                val isCurrent = key == current
                Box {
                    if (isCurrent) {
                        val dotColor = BeakoColors.Text
                        Box(
                            Modifier.align(Alignment.CenterStart)
                                .offset(x = (-15.5).dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(dotColor),
                        )
                    }
                    MenuRow(
                        label = name,
                        expanded = expanded,
                        index = counter.next(),
                        onClick = { if (key == Rows.DISCOVER) onDiscover() else onSearch() },
                        modifier = place(key),
                        height = 36.dp,
                        selected = isCurrent,
                        role = Role.Button,
                        startPadding = 10.dp,
                    )
                }
            }
        }
    }
}

/** Browse's pages beside the rail: blooms out of the icon (scale 0.7 → 1, blur clearing). */
@Composable
private fun Flyout(
    pages: List<Pair<String, String>>,
    current: String,
    hover: MutableInteractionSource,
    onPick: (String) -> Unit,
) {
    val reduceMotion = LocalAppearance.current.reduceMotion
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val grow by
        animateFloatAsState(
            if (shown) 1f else 0f,
            if (reduceMotion) snap() else dropletSpec(),
            label = "bloom",
        )
    val shape = RoundedCornerShape(Radius.field)
    Column(
        Modifier.graphicsLayer {
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.2f)
                scaleX = 0.7f + 0.3f * grow
                scaleY = 0.7f + 0.3f * grow
                alpha = grow.coerceIn(0f, 1f)
            }
            .blur((6f * (1f - grow.coerceIn(0f, 1f))).dp)
            .width(168.dp)
            .dropShadows(shape, ShadowSpec.float())
            .clip(shape)
            .background(BeakoColors.Surface)
            .hoverable(hover)
            .padding(6.dp)
            .semantics { contentDescription = "Browse" },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        pages.forEachIndexed { i, (name, key) ->
            val rise by
                animateFloatAsState(
                    if (shown) 1f else 0f,
                    if (reduceMotion) snap()
                    else tween(240, delayMillis = 40 * i, easing = Motion.Out),
                )
            val isCurrent = key == current
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrent) BeakoColors.Text else BeakoColors.TextMuted,
                maxLines = 1,
                modifier =
                    Modifier.graphicsLayer {
                            alpha = rise
                            translationY = 6.dp.toPx() * (1f - rise)
                        }
                        .fillMaxWidth()
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isCurrent) BeakoColors.Hover
                            else androidx.compose.ui.graphics.Color.Transparent,
                        )
                        .clickable(role = Role.Button) { onPick(key) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

/** Places a popup to the right of its anchor, top-aligned. */
private class RightOfAnchor(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ) =
        IntOffset(
            // The row sits inside the rail's padding: step out past the rail's edge.
            anchorBounds.right + gap + 12,
            anchorBounds.top
                .coerceAtMost(windowSize.height - popupContentSize.height)
                .coerceAtLeast(0),
        )
}

@Composable
private fun Avatar(slot: AccountSlot) {
    Box(
        Modifier.size(20.dp)
            .clip(CircleShape)
            .background(BeakoColors.ChipFill)
            .border(
                1.dp,
                if (slot.name != null) BeakoColors.RimBright else BeakoColors.Rim,
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            slot.avatarUrl != null ->
                AsyncImage(
                    slot.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            slot.name != null ->
                Text(
                    slot.name.take(1).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = BeakoColors.Text,
                )
            else ->
                Icon(
                    Icons.Outlined.Add,
                    null,
                    tint = BeakoColors.TextMuted,
                    modifier = Modifier.size(12.dp),
                )
        }
    }
}

/** More at the foot: the signed-in account's name above "Settings", under a hairline. */
@Composable
private fun Foot(
    account: AccountSlot?,
    expanded: Boolean,
    selected: Boolean,
    index: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val rim = BeakoColors.Rim
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp).drawBehind {
            drawRect(rim, size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
        },
    ) {
        Spacer(Modifier.height(12.dp))
        MenuRow(
            label = account?.name ?: Destination.MORE.label,
            sub = "Settings",
            expanded = expanded,
            index = index,
            onClick = onClick,
            modifier = modifier,
            height = 48.dp,
            leading = {
                if (account != null) Avatar(account) else RowIcon(Destination.MORE.icon, selected)
            },
            selected = selected,
            description = Destination.MORE.label,
        )
    }
}

/** An account shown in the menu; [name] is null when signed out. */
data class AccountSlot(val service: String, val name: String? = null, val avatarUrl: String? = null)

val DefaultAccountSlots = listOf(AccountSlot("Comick"), AccountSlot("MangaDex"))
