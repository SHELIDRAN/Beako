package org.nekomanga.desktop.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.util.Locale
import org.nekomanga.desktop.state.Loadable
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.desktop.theme.LocalAppearance
import org.nekomanga.desktop.theme.LocalHazeState
import org.nekomanga.desktop.theme.ShadowSpec
import org.nekomanga.desktop.theme.dropShadows
import org.nekomanga.desktop.theme.dropletSpec
import org.nekomanga.desktop.theme.easeOutSpec
import org.nekomanga.desktop.theme.focusRing
import org.nekomanga.desktop.theme.glass
import org.nekomanga.shared.model.Title

private val CardShape = RoundedCornerShape(18.dp)
private val TextShadow: Shadow
    get() = Shadow(BeakoColors.TextShadow, blurRadius = 6f)

fun formatScore(score: Double): String = String.format(Locale.US, "%.1f", score)

/** 15234 → "15.2k". */
fun formatCount(count: Int): String =
    when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fk", count / 1_000.0)
        else -> count.toString()
    }

/** A title's cover, or a neutral block while it loads or when there is none. */
@Composable
fun Cover(url: String?, modifier: Modifier = Modifier) {
    Box(modifier.background(BeakoColors.SurfaceRaised)) {
        if (url != null) {
            AsyncImage(
                // Local manga covers are files on this PC.
                model = org.nekomanga.desktop.state.pageModel(url),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The glass caption card: a cover with a frosted caption, an optional rank chip (gold, silver and
 * bronze for the top three) and a small lift on hover.
 */
@Composable
fun TitleCard(
    title: Title,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rank: Int? = null,
    /** A count shown in the top-right corner, e.g. unread chapters. */
    badge: String? = null,
) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val reduceMotion = LocalAppearance.current.reduceMotion
    val haze = remember { HazeState() }
    val focused by hover.collectIsFocusedAsState()
    val pressed by hover.collectIsPressedAsState()
    val raised = hovered || focused
    // Lifts 6 px on hover and sinks back to 2 px while pressed.
    val lift by
        animateFloatAsState(
            when {
                !raised || reduceMotion -> 0f
                pressed -> 1f / 3f
                else -> 1f
            },
            dropletSpec(),
        )
    // The shadow deepens even with Reduce motion; only the movement is dropped.
    val shadow by
        animateFloatAsState(
            if (raised && !pressed) 1f else 0f,
            easeOutSpec(if (pressed) 120 else 450),
        )
    Box(
        modifier
            .size(width = 172.dp, height = 258.dp)
            .graphicsLayer {
                translationY = -6.dp.toPx() * lift
                scaleX = 1f + 0.015f * lift
                scaleY = 1f + 0.015f * lift
            }
            .dropShadows(CardShape, ShadowSpec.card(shadow))
            .focusRing(hover, CardShape)
            .clip(CardShape)
            .border(1.dp, BeakoColors.Rim, CardShape)
            .hoverable(hover)
            .clickable(
                interactionSource = hover,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = title.title },
    ) {
        // The caption blurs this card's own cover.
        Cover(title.coverUrl, Modifier.fillMaxSize().hazeSource(haze))
        // A faint highlight along the top edge.
        Box(
            Modifier.fillMaxWidth()
                .height(56.dp)
                .background(
                    Brush.verticalGradient(listOf(BeakoColors.CardHighlight, Color.Transparent)),
                ),
        )
        if (rank != null) RankChip(rank, Modifier.align(Alignment.TopStart).padding(10.dp))
        // The country flag sits in the top corner the rank chip and badge leave free.
        CountryFlag(
            title.country,
            Modifier.align(if (rank == null) Alignment.TopStart else Alignment.TopEnd)
                .padding(10.dp)
                .then(
                    if (rank != null && badge != null) Modifier.padding(top = 26.dp) else Modifier,
                ),
        )
        if (badge != null) {
            Text(
                badge,
                style = MaterialTheme.typography.labelMedium,
                color = BeakoColors.Ground,
                modifier =
                    Modifier.align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(BeakoColors.Text)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        CompositionLocalProvider(LocalHazeState provides haze) {
            Box(Modifier.align(Alignment.BottomCenter).padding(8.dp).fillMaxWidth()) {
                // The frosted caption: blur plus saturation behind, the text above it.
                // Plain glass here: a colour layer on every card cost too much when scrolling
                // (the large menu panel keeps it).
                Box(Modifier.matchParentSize().glass(RoundedCornerShape(13.dp)))
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        title.title,
                        style = MaterialTheme.typography.titleSmall.copy(shadow = TextShadow),
                        color = BeakoColors.Text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        title.type?.let { Chip(it.label) }
                        title.rating?.let {
                            Text(
                                "★ ${formatScore(it)}",
                                style =
                                    MaterialTheme.typography.labelSmall.copy(shadow = TextShadow),
                                color = BeakoColors.Text,
                            )
                        }
                        title.follows?.let {
                            Text(
                                formatCount(it),
                                style =
                                    MaterialTheme.typography.labelSmall.copy(shadow = TextShadow),
                                color = BeakoColors.Text,
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A titled horizontal row of cards. [trailing] sits beside the title (e.g. the day switch). Loading
 * shows skeleton cards; a failure shows its message.
 */
@Composable
fun Shelf(
    name: String,
    titles: Loadable<List<Title>>,
    onOpen: (Title) -> Unit,
    modifier: Modifier = Modifier,
    ranked: Boolean = false,
    /** Side margin, so the row can scroll edge to edge inside padded screens. */
    inset: androidx.compose.ui.unit.Dp = 32.dp,
    badge: (Title) -> String? = { null },
    trailing: @Composable () -> Unit = {},
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            Modifier.padding(horizontal = inset),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(name, style = MaterialTheme.typography.headlineSmall, color = BeakoColors.Text)
            trailing()
        }
        when (titles) {
            is Loadable.Loading ->
                Row(
                    Modifier.padding(horizontal = inset),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    repeat(6) { Skeleton(Modifier.size(width = 172.dp, height = 258.dp), 18) }
                }
            is Loadable.Failed ->
                Text(
                    titles.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = BeakoColors.TextMuted,
                    modifier = Modifier.padding(horizontal = inset).height(60.dp),
                )
            is Loadable.Ready ->
                if (titles.value.isEmpty()) {
                    Text(
                        "Nothing here yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BeakoColors.TextMuted,
                        modifier = Modifier.padding(horizontal = inset).height(60.dp),
                    )
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = inset, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        items(titles.value.size) { index ->
                            val title = titles.value[index]
                            TitleCard(
                                title,
                                onClick = { onOpen(title) },
                                rank = if (ranked) index + 1 else null,
                                badge = badge(title),
                            )
                        }
                    }
                }
        }
    }
}

/** One search result: rank, cover, title, details and description, as a glass row. */
@Composable
fun ResultRow(
    rank: Int,
    title: Title,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Right-click actions (Add to library, Track on Comick, Copy link); none shows no menu. */
    menu: () -> List<ContextMenuItem> = { emptyList() },
) {
    androidx.compose.foundation.ContextMenuArea(items = menu) {
        ResultRowContent(rank, title, onClick, modifier)
    }
}

@Composable
private fun ResultRowContent(rank: Int, title: Title, onClick: () -> Unit, modifier: Modifier) {
    val hover = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .focusRing(hover, shape)
            .clip(shape)
            .hoverSweep(hover)
            .hoverable(hover)
            .clickable(
                interactionSource = hover,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = title.title }
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            rank.toString(),
            style = MaterialTheme.typography.headlineSmall,
            color = BeakoColors.TextMuted,
            modifier = Modifier.width(44.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Box {
            Cover(
                title.coverUrl,
                Modifier.size(width = 84.dp, height = 126.dp).clip(RoundedCornerShape(10.dp)),
            )
            CountryFlag(title.country, Modifier.align(Alignment.TopStart).padding(6.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                title.title,
                style = MaterialTheme.typography.titleLarge,
                color = BeakoColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta =
                listOfNotNull(
                    title.type?.label,
                    title.status?.label,
                    title.chapterCount?.let { if (it == 1) "1 chapter" else "$it chapters" },
                )
            if (meta.isNotEmpty()) {
                Text(
                    meta.joinToString("  •  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = BeakoColors.TextMuted,
                )
            }
            if (title.altTitles.isNotEmpty()) {
                Text(
                    title.altTitles.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = BeakoColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (title.description.isNotBlank()) {
                Text(
                    title.description.replace('\n', ' '),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BeakoColors.TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(
            Modifier.width(120.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            title.year?.let {
                Text(
                    it.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = BeakoColors.Text,
                )
            }
            title.demographic?.let {
                Text(
                    it.replaceFirstChar(Char::uppercase),
                    style = MaterialTheme.typography.bodySmall,
                    color = BeakoColors.TextMuted,
                )
            }
            title.follows?.let {
                Text(
                    "${formatCount(it)} follows",
                    style = MaterialTheme.typography.bodySmall,
                    color = BeakoColors.TextMuted,
                )
            }
            title.rating?.let {
                Text(
                    "★ ${formatScore(it)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = BeakoColors.Gold,
                )
            }
        }
    }
}
