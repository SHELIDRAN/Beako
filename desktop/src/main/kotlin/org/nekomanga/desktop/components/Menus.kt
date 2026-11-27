package org.nekomanga.desktop.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuRepresentation
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.desktop.theme.Radius

/**
 * A dropdown in Beako's style: the menu radius, the raised surface with a rim and a soft float
 * shadow, instead of Material's square-cornered default. Use with [BeakoMenuItem].
 */
@Composable
fun BeakoDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 6.dp),
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.padding(horizontal = 6.dp),
        offset = offset,
        scrollState = scrollState,
        properties = properties,
        shape = RoundedCornerShape(Radius.menu),
        containerColor = BeakoColors.SurfaceRaised,
        tonalElevation = 0.dp,
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, BeakoColors.Rim),
        content = content,
    )
}

/** A row of a Beako menu: its hover and focus highlight is a rounded pill, not a full band. */
@Composable
fun BeakoMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier.clip(RoundedCornerShape(Radius.field)),
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        colors =
            MenuDefaults.itemColors(
                textColor = BeakoColors.Text,
                leadingIconColor = BeakoColors.TextMuted,
                trailingIconColor = BeakoColors.TextMuted,
            ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
    )
}

/**
 * Right-click menus (ContextMenuArea) in the same style as [BeakoDropdownMenu]. BeakoTheme provides
 * it for the whole app.
 */
object BeakoContextMenu : ContextMenuRepresentation {
    @Composable
    override fun Representation(state: ContextMenuState, items: () -> List<ContextMenuItem>) {
        val status = state.status
        if (status !is ContextMenuState.Status.Open) return
        val shape = RoundedCornerShape(Radius.menu)
        val close = { state.status = ContextMenuState.Status.Closed }
        Popup(
            offset = IntOffset(status.rect.left.toInt(), status.rect.top.toInt()),
            onDismissRequest = close,
            properties = PopupProperties(focusable = true),
        ) {
            Column(
                Modifier.padding(8.dp)
                    .shadow(16.dp, shape)
                    .clip(shape)
                    .background(BeakoColors.SurfaceRaised)
                    .border(1.dp, BeakoColors.Rim, shape)
                    .padding(6.dp)
                    .widthIn(min = 180.dp),
            ) {
                items().forEach { item ->
                    BeakoMenuItem(
                        text = { Text(item.label) },
                        onClick = {
                            close()
                            item.onClick()
                        },
                    )
                }
            }
        }
    }
}
