package org.nekomanga.desktop.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.desktop.theme.Droplet
import org.nekomanga.desktop.theme.GlassSurface
import org.nekomanga.desktop.theme.LocalAppearance
import org.nekomanga.desktop.theme.SidebarMode
import org.nekomanga.desktop.theme.dropletSpec

/** The app's top-level screens, in sidebar order. */
enum class Destination(val label: String, val icon: ImageVector) {
    LIBRARY("Library", Icons.Outlined.CollectionsBookmark),
    UPDATES("Latest updates", Icons.Outlined.Update),
    BROWSE("Browse", Icons.Outlined.Explore),
    HISTORY("Reading history", Icons.Outlined.History),
    MORE("More", Icons.Outlined.MoreHoriz),
}

/** Width of the collapsed, icons-only rail, including its outer margin. */
val SidebarRailWidth = 84.dp

private val CollapsedItem = 50.dp
private val ExpandedItem = 70.dp
private const val ExpandDelayMillis = 150L
private const val CollapseDelayMillis = 400L

/**
 * The floating glass navigation pill. Shows icons only until the pointer or keyboard focus is on
 * it, then expands to show labels (unless the sidebar setting says otherwise).
 */
@Composable
fun Sidebar(
    selected: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    onAccountClick: (String) -> Unit = {},
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
    LaunchedEffect(wantExpanded) {
        if (appearance.sidebarMode == SidebarMode.AUTO_COLLAPSE) {
            delay(if (wantExpanded) ExpandDelayMillis else CollapseDelayMillis)
        }
        expanded = wantExpanded
    }
    val sizeSpec = if (appearance.reduceMotion) snap<androidx.compose.ui.unit.Dp>() else tween(220)
    val width by animateDpAsState(if (expanded) 104.dp else 68.dp, sizeSpec)
    val itemHeight by animateDpAsState(if (expanded) ExpandedItem else CollapsedItem, sizeSpec)
    val dropletY by animateDpAsState(itemHeight * selected.ordinal, dropletSpec())

    GlassSurface(
        shape = RoundedCornerShape(30.dp),
        modifier = modifier.width(width).hoverable(hover).onFocusChanged { focused = it.hasFocus },
    ) {
        Column(
            Modifier.padding(vertical = 14.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AccountRing("Comick", onAccountClick)
                if (expanded) AccountRing("MangaDex", onAccountClick)
            }
            if (!expanded) {
                Spacer(Modifier.height(6.dp))
                AccountRing("MangaDex", onAccountClick)
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().selectableGroup()) {
                Droplet(
                    Modifier.align(Alignment.TopCenter)
                        .offset(y = dropletY + 4.dp)
                        .size(width = 48.dp, height = 36.dp),
                )
                Column(Modifier.fillMaxWidth()) {
                    Destination.entries.forEach { destination ->
                        SidebarItem(
                            destination = destination,
                            selected = destination == selected,
                            expanded = expanded,
                            height = itemHeight,
                            onClick = { onSelect(destination) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarItem(
    destination: Destination,
    selected: Boolean,
    expanded: Boolean,
    height: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val reduceMotion = LocalAppearance.current.reduceMotion
    Column(
        Modifier.fillMaxWidth()
            .height(height)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = destination.label
                selectedState(selected)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.padding(top = 4.dp).size(width = 48.dp, height = 36.dp), Alignment.Center) {
            Icon(
                destination.icon,
                contentDescription = null,
                tint = if (selected) BeakoColors.Text else BeakoColors.TextMuted,
                modifier = Modifier.size(22.dp),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = if (reduceMotion) fadeIn(snap()) else fadeIn(tween(160)) + expandVertically(),
            exit = if (reduceMotion) fadeOut(snap()) else fadeOut(tween(90)) + shrinkVertically(),
        ) {
            Text(
                destination.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) BeakoColors.Text else BeakoColors.TextMuted,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
}

/** A signed-out account slot: a "+" ring that opens Connect for [service]. */
@Composable
private fun AccountRing(service: String, onClick: (String) -> Unit) {
    Box(
        Modifier.size(34.dp)
            .clip(CircleShape)
            .border(1.dp, BeakoColors.RimBright, CircleShape)
            .clickable(role = Role.Button) { onClick(service) }
            .semantics { contentDescription = "Connect $service" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Add,
            null,
            tint = BeakoColors.TextMuted,
            modifier = Modifier.size(16.dp),
        )
    }
}
