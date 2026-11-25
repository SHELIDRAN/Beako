package org.nekomanga.desktop.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.desktop.theme.LocalAppearance
import org.nekomanga.desktop.theme.Motion
import org.nekomanga.desktop.theme.Radius
import org.nekomanga.desktop.theme.ShadowSpec
import org.nekomanga.desktop.theme.Sizes
import org.nekomanga.desktop.theme.dropShadows
import org.nekomanga.desktop.theme.dropletSpec
import org.nekomanga.desktop.theme.easeOutSpec
import org.nekomanga.desktop.theme.focusRing
import org.nekomanga.desktop.theme.innerShadows
import org.nekomanga.desktop.theme.press
import org.nekomanga.desktop.theme.sweepSpec

// ───────────── Sweep ─────────────

/**
 * Sweep: on hover or keyboard focus a [color] wash grows in from the left edge (360 ms). The caller
 * clips to its shape and makes the element hoverable with the same [interaction].
 */
@Composable
fun Modifier.hoverSweep(
    interaction: InteractionSource,
    color: Color = BeakoColors.Hover,
): Modifier {
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val progress by
        animateFloatAsState(if (hovered || focused) 1f else 0f, sweepSpec(360), label = "sweep")
    return drawBehind {
        if (progress > 0f) drawRect(color, size = Size(size.width * progress, size.height))
    }
}

// ───────────── ArrowButton ─────────────

enum class ArrowStyle {
    /** `surface` pill: secondary forward actions. */
    SURFACE,
    /** `ink-fill` pill: strong, but not the accent. */
    INK,
    /** Already filled with the accent: the one accent action on a screen. */
    ACCENT,
}

/**
 * A large pill call to action with a round arrow knob on the right. On hover or focus the knob's
 * accent fill **sweeps** across the whole pill and the arrow nudges forward. For moving on
 * ("Continue reading"), never for toggles or destructive actions.
 */
@Composable
fun ArrowButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ArrowStyle = ArrowStyle.SURFACE,
    icon: ImageVector = Icons.AutoMirrored.Outlined.ArrowForward,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val active = hovered || focused
    val filled = active || style == ArrowStyle.ACCENT
    val sweep by animateFloatAsState(if (filled) 1f else 0f, sweepSpec(420), label = "arrow")
    val nudge by animateDpAsState(if (active) 3.dp else 0.dp, sweepSpec(420), label = "nudge")
    val glow by
        animateFloatAsState(
            if (active && style == ArrowStyle.ACCENT) 1f else 0f,
            easeOutSpec(200),
        )
    val restInk =
        when (style) {
            ArrowStyle.SURFACE -> BeakoColors.Text
            ArrowStyle.INK -> BeakoColors.InkOnFill
            ArrowStyle.ACCENT -> BeakoColors.AccentInk
        }
    val ink by
        animateColorAsState(
            if (filled) BeakoColors.AccentInk else restInk,
            if (LocalAppearance.current.reduceMotion) snap() else tween(300),
        )
    val shape = CircleShape
    val rest = if (style == ArrowStyle.INK) BeakoColors.InkFill else BeakoColors.Surface
    val fill = BeakoColors.AccentFill
    Row(
        modifier
            .press(interaction)
            .dropShadow(shape, ShadowSpec.droplet())
            .dropShadow(shape, ShadowSpec.accentGlow(glow))
            .defaultMinSize(minWidth = 208.dp)
            .height(Sizes.controlCta)
            .focusRing(interaction, shape)
            .clip(shape)
            .background(rest)
            .drawBehind {
                // The knob's fill grows leftwards from the knob until it covers the pill.
                val inset = 4.dp.toPx()
                val knob = size.height - inset * 2
                val width = knob + (size.width - inset * 2 - knob) * sweep
                drawRoundRect(
                    fill,
                    topLeft = Offset(size.width - inset - width, inset),
                    size = Size(width, knob),
                    cornerRadius = CornerRadius(knob / 2),
                )
            }
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(start = 22.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        // As wide as its label (at least 208), the knob pushed to the end: never fill the row.
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = ink)
        Spacer(Modifier.width(24.dp))
        Box(Modifier.size(Sizes.controlCta - 8.dp), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                null,
                tint = BeakoColors.AccentInk,
                modifier = Modifier.offset(x = nudge).size(Sizes.iconSm),
            )
        }
    }
}

// ───────────── ToggleIconButton ─────────────

/**
 * A round on/off button for one binary action beside a title (add to / remove from library). Off is
 * an accent circle with a plus; on, an ink circle **rises** from below and the plus turns into a
 * minus. The accessible name changes with the state.
 */
@Composable
fun ToggleIconButton(
    checked: Boolean,
    onClick: () -> Unit,
    offLabel: String,
    onLabel: String,
    modifier: Modifier = Modifier,
    /** Starts on `surface`, for places where an accent would compete. */
    ghost: Boolean = false,
    /** [Role.Switch] when a click flips it; [Role.Button] when it opens something instead. */
    role: Role = Role.Switch,
) {
    val interaction = remember { MutableInteractionSource() }
    val offInk = if (ghost) BeakoColors.Text else BeakoColors.AccentInk
    val ink by
        animateColorAsState(
            if (checked) BeakoColors.InkOnFill else offInk,
            if (LocalAppearance.current.reduceMotion) snap() else tween(300),
        )
    Box(
        modifier
            .press(interaction)
            .size(Sizes.controlCta)
            .dropShadow(CircleShape, ShadowSpec.droplet())
            .focusRing(interaction, CircleShape)
            .clip(CircleShape)
            .background(if (ghost) BeakoColors.Surface else BeakoColors.AccentFill)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = role,
                onClick = onClick,
            )
            .semantics {
                contentDescription = if (checked) onLabel else offLabel
                if (role == Role.Switch) stateDescription = if (checked) "On" else "Off"
            },
        contentAlignment = Alignment.Center,
    ) {
        RiseInk(checked, BeakoColors.InkFill)
        PlusMinusGlyph(minus = checked, color = ink)
    }
}

/**
 * A plus that turns into a minus: the vertical bar folds flat while the glyph turns half a circle
 * on the droplet spring. Used by ToggleIconButton and folding menu groups.
 */
@Composable
fun PlusMinusGlyph(minus: Boolean, color: Color, modifier: Modifier = Modifier) {
    val turn by animateFloatAsState(if (minus) 1f else 0f, dropletSpec(), label = "glyph")
    Box(
        modifier.size(14.dp).rotate(180f * turn).drawBehind {
            val bar = 2.dp.toPx()
            val y = (size.height - bar) / 2
            drawRoundRect(color, Offset(0f, y), Size(size.width, bar), CornerRadius(bar / 2))
            rotate(90f * (1f - turn)) {
                drawRoundRect(color, Offset(0f, y), Size(size.width, bar), CornerRadius(bar / 2))
            }
        },
    )
}

// ───────────── TriStateChip ─────────────

enum class TriState(val description: String) {
    NONE("Not selected"),
    INCLUDED("Included"),
    EXCLUDED("Excluded"),
}

/**
 * A tag chip with three states: not selected, included (✓) and excluded (✕ with a strikethrough),
 * so state is never shown by colour alone. The chosen fill **sweeps** in from the left and the mark
 * pops in on the droplet spring.
 */
@Composable
fun TriStateChip(
    label: String,
    state: TriState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = CircleShape
    val target =
        when (state) {
            TriState.NONE -> BeakoColors.TriNoneFill
            TriState.INCLUDED -> BeakoColors.TriIncludedFill
            TriState.EXCLUDED -> BeakoColors.TriExcludedFill
        }
    val rim by
        animateColorAsState(
            when (state) {
                TriState.NONE -> BeakoColors.Rim
                TriState.INCLUDED -> BeakoColors.TriIncludedRim
                TriState.EXCLUDED -> BeakoColors.TriExcludedRim
            },
            sweepSpec(),
        )
    // The fill being swept in, and the one it covers.
    var under by remember { mutableStateOf(target) }
    var over by remember { mutableStateOf(target) }
    val sweep = remember { Animatable(1f) }
    val spec = sweepSpec<Float>()
    LaunchedEffect(target) {
        if (over == target) return@LaunchedEffect
        under = over
        over = target
        sweep.snapTo(0f)
        sweep.animateTo(1f, spec)
        under = target
    }
    val mark by animateFloatAsState(if (state == TriState.NONE) 0f else 1f, dropletSpec())
    Row(
        modifier
            .press(interaction)
            .height(Sizes.controlSm)
            .focusRing(interaction, shape)
            .clip(shape)
            .background(under)
            .drawBehind { drawRect(over, size = Size(size.width * sweep.value, size.height)) }
            .border(1.dp, rim, shape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Checkbox,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = state.description
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (state != TriState.NONE) {
            Text(
                if (state == TriState.INCLUDED) "✓" else "✕",
                style = MaterialTheme.typography.labelMedium,
                color = if (state == TriState.INCLUDED) BeakoColors.Text else BeakoColors.Danger,
                modifier = Modifier.scale(mark),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (state == TriState.NONE) BeakoColors.TextMuted else BeakoColors.Text,
            textDecoration = if (state == TriState.EXCLUDED) TextDecoration.LineThrough else null,
        )
    }
}

// ───────────── RankChip ─────────────

/**
 * The position badge on ranked shelves: gold, silver and bronze for the top three, dark glass
 * below. The number carries the meaning.
 */
@Composable
fun RankChip(rank: Int, modifier: Modifier = Modifier) {
    val (fill, ink) =
        when (rank) {
            1 -> BeakoColors.RankGoldFill to BeakoColors.RankGoldInk
            2 -> BeakoColors.RankSilverFill to BeakoColors.RankSilverInk
            3 -> BeakoColors.RankBronzeFill to BeakoColors.RankBronzeInk
            else -> BeakoColors.RankPlainFill to BeakoColors.Text
        }
    val shape = RoundedCornerShape(Radius.field)
    Box(
        modifier
            .size(Sizes.rankChip)
            .clip(shape)
            .background(fill)
            .border(1.dp, BeakoColors.RankRim, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(rank.toString(), style = MaterialTheme.typography.titleMedium, color = ink)
    }
}

// ───────────── Tabs ─────────────

/**
 * Text tabs on a hairline for views inside one page. The current tab's accent underline
 * **travels**: its leading edge moves first and the trailing edge follows 90 ms later, so it
 * stretches across and settles. Left and right arrow keys move between tabs.
 */
@Composable
fun Tabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lefts = remember(tabs.size) { mutableStateListOf(*Array(tabs.size) { 0f }) }
    val rights = remember(tabs.size) { mutableStateListOf(*Array(tabs.size) { 0f }) }
    val left = remember { Animatable(0f) }
    val right = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    val spring = dropletSpec<Float>()
    val reduceMotion = LocalAppearance.current.reduceMotion
    val accent = BeakoColors.Accent
    val rim = BeakoColors.Rim
    LaunchedEffect(selectedIndex, lefts.toList(), rights.toList()) {
        val l = lefts.getOrNull(selectedIndex) ?: return@LaunchedEffect
        val r = rights.getOrNull(selectedIndex) ?: return@LaunchedEffect
        if (r <= l) return@LaunchedEffect
        if (!placed || reduceMotion) {
            left.snapTo(l)
            right.snapTo(r)
            placed = true
            return@LaunchedEffect
        }
        val movingRight = l > left.value
        launch {
            if (!movingRight) delay(0) else delay(90)
            left.animateTo(l, spring)
        }
        launch {
            if (movingRight) delay(0) else delay(90)
            right.animateTo(r, spring)
        }
    }
    // Scrolls sideways when the tabs don't fit (large interface sizes, narrow windows).
    Row(
        modifier
            .horizontalScroll(androidx.compose.foundation.rememberScrollState())
            .selectableGroup()
            .drawBehind {
                val line = 1.dp.toPx()
                drawRect(rim, Offset(0f, size.height - line), Size(size.width, line))
                if (placed) {
                    val h = 2.dp.toPx()
                    drawRoundRect(
                        accent,
                        Offset(left.value, size.height - h),
                        Size((right.value - left.value).coerceAtLeast(0f), h),
                        CornerRadius(h / 2),
                    )
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> {
                        onSelect((selectedIndex + 1).coerceAtMost(tabs.lastIndex))
                        true
                    }
                    Key.DirectionLeft -> {
                        onSelect((selectedIndex - 1).coerceAtLeast(0))
                        true
                    }
                    else -> false
                }
            },
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            val interaction = remember { MutableInteractionSource() }
            val selected = index == selectedIndex
            Box(
                Modifier.press(interaction)
                    .focusRing(interaction, RoundedCornerShape(6.dp))
                    .height(Sizes.controlLg)
                    .onGloballyPositioned {
                        val x = it.positionInParent().x
                        // Written only on change: these lists are read in composition.
                        val r = x + it.size.width
                        if (index < lefts.size && (lefts[index] != x || rights[index] != r)) {
                            lefts[index] = x
                            rights[index] = r
                        }
                    }
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Tab,
                    ) {
                        onSelect(index)
                    }
                    .semantics { selectedState(selected) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tab,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) BeakoColors.Text else BeakoColors.TextMuted,
                )
            }
        }
    }
}

// ───────────── Alert ─────────────

enum class AlertKind {
    /** Neutral information that will clear itself. */
    NEUTRAL,
    /** Needs action: glows orange. One visible at a time. */
    ACCENT,
    /** Low-priority status line, no fill. */
    QUIET,
    /** An error; always say in words what failed. */
    DANGER,
}

/**
 * A compact raised notice inside content: an icon, one line of text, an optional action. Banner
 * stays the screen-wide notice; Alert is for one item or section. Text is never orange.
 */
@Composable
fun Alert(
    message: String,
    modifier: Modifier = Modifier,
    kind: AlertKind = AlertKind.NEUTRAL,
    icon: ImageVector? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(Radius.field)
    val surface =
        when (kind) {
            AlertKind.QUIET -> Modifier
            AlertKind.ACCENT ->
                Modifier.dropShadow(shape, ShadowSpec.accentGlow())
                    .clip(shape)
                    .background(BeakoColors.Surface)
                    .background(BeakoColors.AccentSoft)
                    .border(1.dp, BeakoColors.AccentRim, shape)
            else ->
                Modifier.dropShadow(shape, ShadowSpec.droplet())
                    .clip(shape)
                    .background(BeakoColors.Surface)
                    .border(1.dp, BeakoColors.Rim, shape)
        }
    val tint =
        when (kind) {
            AlertKind.ACCENT -> BeakoColors.Accent
            AlertKind.DANGER -> BeakoColors.Danger
            else -> BeakoColors.TextMuted
        }
    Row(
        modifier
            .then(surface)
            .semantics {
                liveRegion =
                    if (kind == AlertKind.DANGER || kind == AlertKind.ACCENT)
                        LiveRegionMode.Assertive
                    else LiveRegionMode.Polite
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            icon
                ?: if (kind == AlertKind.DANGER || kind == AlertKind.ACCENT)
                    Icons.Outlined.ErrorOutline
                else Icons.Outlined.Info,
            null,
            tint = tint,
            modifier = Modifier.size(Sizes.iconMd),
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = BeakoColors.Text,
            modifier = Modifier.weight(1f, fill = false),
        )
        actions()
    }
}

// ───────────── SoftCard ─────────────

enum class SoftElevation {
    /** A card you can open: `surface`, rises 6 px on hover with a deeper shadow. */
    LIFT,
    /** 1 px rim, for busy grounds. */
    FLAT,
    /** Same fill as the ground, shaped by light and shadow: resting containers. */
    RAISED,
    /** `surface` with a high shadow: things that appear over others. */
    FLOAT,
    /** Pressed in: a selected or held state, never a resting card. */
    PRESSED,
}

/**
 * The soft elevations. Soft shadows only work when the card's fill equals the ground behind it;
 * over covers or glass use GlassSurface.
 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    elevation: SoftElevation = SoftElevation.RAISED,
    shape: Shape = RoundedCornerShape(Radius.panel),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val reduceMotion = LocalAppearance.current.reduceMotion
    val pressed by interaction.collectIsPressedAsState()
    val lifted = elevation == SoftElevation.LIFT && (hovered || focused)
    val shadow by
        animateFloatAsState(
            if (lifted && !pressed) 1f else 0f,
            easeOutSpec(if (pressed) 120 else 450),
        )
    // Rises 6 px on hover, sinks back to 2 px while pressed.
    val rise by
        animateFloatAsState(
            when {
                !lifted || reduceMotion -> 0f
                pressed -> 1f / 3f
                else -> 1f
            },
            dropletSpec(),
        )
    val look =
        when (elevation) {
            SoftElevation.LIFT ->
                Modifier.graphicsLayer { translationY = -6.dp.toPx() * rise }
                    .dropShadows(shape, ShadowSpec.card(shadow))
                    .clip(shape)
                    .background(BeakoColors.Surface)
                    .border(1.dp, BeakoColors.Rim, shape)
            SoftElevation.FLAT ->
                Modifier.clip(shape)
                    .background(BeakoColors.Ground)
                    .border(1.dp, BeakoColors.Rim, shape)
            SoftElevation.RAISED ->
                Modifier.dropShadows(shape, ShadowSpec.softRaised())
                    .clip(shape)
                    .background(BeakoColors.Ground)
            SoftElevation.FLOAT ->
                Modifier.dropShadows(shape, ShadowSpec.float())
                    .clip(shape)
                    .background(BeakoColors.Surface)
            SoftElevation.PRESSED ->
                Modifier.clip(shape)
                    .background(BeakoColors.Ground)
                    .innerShadows(shape, ShadowSpec.softInset())
        }
    Box(
        modifier
            .then(
                if (onClick != null) Modifier.press(interaction).focusRing(interaction, shape)
                else Modifier,
            )
            .then(look)
            .then(
                if (onClick != null)
                    Modifier.hoverable(interaction)
                        .clickable(
                            interactionSource = interaction,
                            indication = LocalIndication.current,
                            role = Role.Button,
                            onClick = onClick,
                        )
                else Modifier,
            ),
    ) {
        content()
    }
}

// ───────────── SoftField ─────────────

enum class SoftFieldStyle {
    /** Raised pill: main search on a plain ground. */
    RAISED,
    /** Pressed in: filters inside a sheet. */
    INSET,
    /** `surface` with a rim, for busy grounds where soft shadows vanish. */
    OUTLINE,
}

/**
 * A soft search field: the colour of the ground under it, shaped only by light and shadow. Only on
 * a flat ground (over covers or glass use SearchField). Focus adds an accent ring; the caret is
 * accent. The placeholder is also the accessible name.
 */
@Composable
fun SoftField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    style: SoftFieldStyle = SoftFieldStyle.RAISED,
    square: Boolean = false,
    /** A shortcut hint shown at the end, e.g. "Ctrl F". */
    shortcut: String? = null,
    onSearch: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
) {
    val shape = if (square) RoundedCornerShape(Radius.panel) else CircleShape
    var focused by remember { mutableStateOf(false) }
    val ring by
        animateColorAsState(
            if (focused) BeakoColors.AccentRim else Color.Transparent,
            easeOutSpec(200),
        )
    val look =
        when (style) {
            SoftFieldStyle.RAISED ->
                Modifier.dropShadows(shape, ShadowSpec.softRaised())
                    .clip(shape)
                    .background(BeakoColors.Ground)
            SoftFieldStyle.INSET ->
                Modifier.clip(shape)
                    .background(BeakoColors.Ground)
                    .innerShadows(shape, ShadowSpec.softInset())
            SoftFieldStyle.OUTLINE ->
                Modifier.clip(shape)
                    .background(BeakoColors.Surface)
                    .border(1.dp, if (focused) BeakoColors.AccentRim else BeakoColors.Rim, shape)
        }
    Row(
        modifier
            .height(Sizes.controlCta)
            .then(look)
            .border(2.dp, if (style == SoftFieldStyle.OUTLINE) Color.Transparent else ring, shape)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Outlined.Search,
            null,
            tint = BeakoColors.TextMuted,
            modifier = Modifier.size(Sizes.iconMd),
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = BeakoColors.TextMuted,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = BeakoColors.Text),
                cursorBrush = SolidColor(BeakoColors.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
                modifier =
                    Modifier.fillMaxWidth()
                        .then(
                            if (focusRequester != null) Modifier.focusRequester(focusRequester)
                            else Modifier,
                        )
                        .onFocusChanged { focused = it.isFocused }
                        .semantics { contentDescription = placeholder },
            )
        }
        if (value.isNotEmpty()) {
            Text(
                "✕",
                style = MaterialTheme.typography.labelLarge,
                color = BeakoColors.TextMuted,
                modifier =
                    Modifier.clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = "Clear search") {
                            onValueChange("")
                        }
                        .semantics { contentDescription = "Clear search" }
                        .padding(4.dp),
            )
        } else if (shortcut != null) {
            Kbd(shortcut)
        }
    }
}

/** A keyboard shortcut hint, e.g. "Ctrl K". */
@Composable
fun Kbd(text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = BeakoColors.TextMuted,
        modifier =
            modifier
                .clip(shape)
                .background(BeakoColors.ChipFill)
                .border(1.dp, BeakoColors.Rim, shape)
                .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

// ───────────── IconToggleGroup ─────────────

/** One option of an [IconToggleGroup]: its icon and its name (accessible name and tooltip). */
data class IconOption(val icon: ImageVector, val label: String)

/**
 * An icon-only single choice on a small `surface` card, for 2–4 options whose icons explain
 * themselves (page fit, reading direction). The chosen icon is full text; the others sit muted at
 * 70%. The ink **travels** to the new choice and the card dips 1 px under the click.
 */
@Composable
fun IconToggleGroup(
    options: List<IconOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radius.panel)
    // Travel: the ink moves to the chosen option (56 wide, 8 apart) edge by edge, the leading
    // edge first and the trailing one 45 ms behind, so it stretches across and settles.
    val inkLeft = remember { Animatable(64f * selectedIndex) }
    val inkRight = remember { Animatable(64f * selectedIndex + 56f) }
    val travel = dropletSpec<Float>()
    val reduceMotion = LocalAppearance.current.reduceMotion
    LaunchedEffect(selectedIndex) {
        val l = 64f * selectedIndex
        val r = l + 56f
        if (reduceMotion) {
            inkLeft.snapTo(l)
            inkRight.snapTo(r)
            return@LaunchedEffect
        }
        val right = l > inkLeft.value
        launch {
            if (right) delay(45)
            inkLeft.animateTo(l, travel)
        }
        launch {
            if (!right) delay(45)
            inkRight.animateTo(r, travel)
        }
    }
    val scope = rememberCoroutineScope()
    val pressDip = remember { Animatable(0f) }
    Box(
        modifier
            .graphicsLayer { translationY = pressDip.value * 1.dp.toPx() }
            .dropShadow(shape, ShadowSpec.glass())
            .clip(shape)
            .background(BeakoColors.Surface)
            .padding(10.dp)
            .selectableGroup()
            .semantics { contentDescription = label },
    ) {
        Box(
            Modifier.offset(x = inkLeft.value.dp)
                .size((inkRight.value - inkLeft.value).coerceAtLeast(0f).dp, 48.dp)
                .clip(RoundedCornerShape(Radius.field))
                .background(BeakoColors.ChipFill),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { index, option ->
                val interaction = remember { MutableInteractionSource() }
                val selected = index == selectedIndex
                val hovered by interaction.collectIsHoveredAsState()
                val tint by
                    animateColorAsState(
                        if (selected) BeakoColors.Text else BeakoColors.TextMuted,
                        easeOutSpec(240),
                    )
                val alpha by
                    animateFloatAsState(
                        when {
                            selected -> 1f
                            hovered -> 0.8f
                            else -> 0.7f
                        },
                        easeOutSpec(240),
                    )
                Tooltip(option.label) {
                    Box(
                        Modifier.press(interaction)
                            .size(56.dp, 48.dp)
                            .focusRing(interaction, RoundedCornerShape(Radius.field))
                            .clip(RoundedCornerShape(Radius.field))
                            .background(
                                if (hovered && !selected) BeakoColors.Hover else Color.Transparent,
                            )
                            .hoverable(interaction)
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                role = Role.RadioButton,
                            ) {
                                scope.launch {
                                    pressDip.snapTo(1f)
                                    pressDip.animateTo(0f, tween(220))
                                }
                                onSelect(index)
                            }
                            .semantics {
                                contentDescription = option.label
                                selectedState(selected)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            option.icon,
                            null,
                            tint = tint,
                            modifier = Modifier.size(26.dp).alpha(alpha),
                        )
                    }
                }
            }
        }
    }
}

// ───────────── MorphSelect ─────────────

/**
 * A small pill trigger ("This week ⌄") that **blooms** into its own popover — an optional action on
 * top, a two- or three-way choice below — and shrinks back into the pill showing the new value. Esc
 * or a click outside closes it.
 */
@Composable
fun MorphSelect(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    action: Pair<String, () -> Unit>? = null,
) {
    val open = remember { MutableTransitionState(false) }
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalAppearance.current.reduceMotion
    val interaction = remember { MutableInteractionSource() }
    val trigger = remember { FocusRequester() }
    var wasOpen by remember { mutableStateOf(false) }
    // Once the popover has closed, focus goes back to the trigger.
    LaunchedEffect(open.currentState, open.targetState) {
        if (open.currentState || open.targetState) wasOpen = true
        else if (wasOpen) {
            wasOpen = false
            runCatching { trigger.requestFocus() }
        }
    }
    val triggerAlpha by
        animateFloatAsState(if (open.targetState) 0f else 1f, easeOutSpec(120), label = "trigger")
    Box(modifier) {
        Row(
            Modifier.press(interaction)
                .alpha(triggerAlpha)
                .height(Sizes.controlSm)
                .focusRequester(trigger)
                .focusRing(interaction, CircleShape)
                .clip(CircleShape)
                .background(BeakoColors.Surface)
                .border(1.dp, BeakoColors.Rim, CircleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    role = Role.DropdownList,
                ) {
                    open.targetState = true
                }
                .semantics {
                    contentDescription = "$label: ${options.getOrElse(selectedIndex) { "" }}"
                }
                .padding(start = 14.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                options.getOrElse(selectedIndex) { "" },
                style = MaterialTheme.typography.titleSmall,
                color = BeakoColors.Text,
            )
            Icon(
                Icons.Outlined.ExpandMore,
                null,
                tint = BeakoColors.TextMuted,
                modifier = Modifier.size(Sizes.iconSm),
            )
        }
        if (open.currentState || open.targetState) {
            val width = 236.dp
            Popup(
                popupPositionProvider = remember { CenteredOverAnchor() },
                onDismissRequest = { open.targetState = false },
                properties = PopupProperties(focusable = true),
                onPreviewKeyEvent = {
                    if (it.key == Key.Escape && it.type == KeyEventType.KeyDown) {
                        open.targetState = false
                        true
                    } else false
                },
            ) {
                val transition = rememberTransition(open, label = "bloom")
                val bloomSpec =
                    if (reduceMotion) snap<Float>()
                    else spring(dampingRatio = 0.62f, stiffness = 400f)
                val grow by transition.animateFloat({ bloomSpec }) { if (it) 1f else 0f }
                val fade by
                    transition.animateFloat({ if (reduceMotion) snap() else tween(160) }) {
                        if (it) 1f else 0f
                    }
                // Bloom: the rows follow the popover, the action 70 ms in and the choice 130 ms.
                val rows by
                    transition.animateFloat(
                        {
                            if (reduceMotion) snap()
                            else if (targetState) tween(360, delayMillis = 70, easing = Motion.Out)
                            else tween(100)
                        },
                    ) {
                        if (it) 1f else 0f
                    }
                val choiceRow by
                    transition.animateFloat(
                        {
                            if (reduceMotion) snap()
                            else if (targetState) tween(360, delayMillis = 130, easing = Motion.Out)
                            else tween(100)
                        },
                    ) {
                        if (it) 1f else 0f
                    }
                val shape = RoundedCornerShape(Radius.menu)
                Column(
                    Modifier.graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 0.1f)
                            scaleX = 0.42f + 0.58f * grow
                            scaleY = 0.3f + 0.7f * grow
                            alpha = fade
                        }
                        .blur((8f * (1f - grow.coerceIn(0f, 1f))).dp)
                        .width(width)
                        .dropShadows(shape, ShadowSpec.float())
                        .clip(shape)
                        .background(BeakoColors.Surface)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (action != null) {
                        val (text, run) = action
                        val actionPress = remember { MutableInteractionSource() }
                        Box(
                            Modifier.graphicsLayer {
                                    alpha = rows
                                    translationY = 8.dp.toPx() * (1f - rows)
                                }
                                .press(actionPress)
                                .fillMaxWidth()
                                .height(Sizes.controlLg)
                                .focusRing(actionPress, CircleShape)
                                .clip(CircleShape)
                                .border(1.dp, BeakoColors.Rim, CircleShape)
                                .clickable(
                                    interactionSource = actionPress,
                                    indication = LocalIndication.current,
                                    role = Role.Button,
                                ) {
                                    run()
                                    open.targetState = false
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text,
                                style = MaterialTheme.typography.labelLarge,
                                color = BeakoColors.Text,
                            )
                        }
                    }
                    var chosen by remember { mutableStateOf(selectedIndex) }
                    val segWidth = (width - 20.dp) / options.size.coerceAtLeast(1)
                    val indicator by animateDpAsState(segWidth * chosen, dropletSpec())
                    Box(
                        Modifier.graphicsLayer {
                                alpha = choiceRow
                                translationY = 8.dp.toPx() * (1f - choiceRow)
                            }
                            .fillMaxWidth()
                            .selectableGroup(),
                    ) {
                        Box(
                            Modifier.offset(x = indicator)
                                .width(segWidth)
                                .height(Sizes.controlLg)
                                .clip(CircleShape)
                                .background(BeakoColors.ChipFill),
                        )
                        Row {
                            options.forEachIndexed { index, option ->
                                val selected = index == chosen
                                val optionPress = remember { MutableInteractionSource() }
                                Box(
                                    Modifier.press(optionPress)
                                        .width(segWidth)
                                        .height(Sizes.controlLg)
                                        .focusRing(optionPress, CircleShape)
                                        .clip(CircleShape)
                                        .clickable(
                                            interactionSource = optionPress,
                                            indication = LocalIndication.current,
                                            role = Role.RadioButton,
                                        ) {
                                            chosen = index
                                            onSelect(index)
                                            // Close once the choice has been seen.
                                            scope.launch {
                                                if (!reduceMotion) delay(380)
                                                open.targetState = false
                                            }
                                        }
                                        .semantics {
                                            contentDescription = option
                                            selectedState(selected)
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        option,
                                        style = MaterialTheme.typography.labelLarge,
                                        color =
                                            if (selected) BeakoColors.Text
                                            else BeakoColors.TextMuted,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Places a popup centred over its anchor, 4 px above its top, kept inside the window. */
private class CenteredOverAnchor : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x =
            (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(
                0,
                (windowSize.width - popupContentSize.width).coerceAtLeast(0),
            )
        val y =
            (anchorBounds.top - 4).coerceIn(
                0,
                (windowSize.height - popupContentSize.height).coerceAtLeast(0),
            )
        return IntOffset(x, y)
    }
}

/** Shows [text] as a tooltip on hover; with null [text] just the content. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun Tooltip(text: String?, content: @Composable () -> Unit) {
    if (text == null) {
        content()
        return
    }
    androidx.compose.foundation.TooltipArea(
        tooltip = {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = BeakoColors.Text,
                modifier =
                    Modifier.dropShadows(RoundedCornerShape(8.dp), ShadowSpec.float())
                        .clip(RoundedCornerShape(8.dp))
                        .background(BeakoColors.Surface)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        },
        delayMillis = 500,
    ) {
        content()
    }
}
