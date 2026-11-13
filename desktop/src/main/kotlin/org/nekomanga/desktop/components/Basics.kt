package org.nekomanga.desktop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.desktop.theme.glass

/** A pill button on glass. [primary] makes it the solid, high-emphasis action. */
@Composable
fun GlassButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
) {
    val shape = CircleShape
    val base =
        if (primary) Modifier.clip(shape).background(BeakoColors.Text)
        else Modifier.glass(shape, tint = BeakoColors.Rim)
    Row(
        modifier
            .defaultMinSize(minHeight = 36.dp)
            .then(base)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val ink = if (primary) BeakoColors.Ground else BeakoColors.Text
        if (icon != null) Icon(icon, null, tint = ink, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = ink)
    }
}

/** An icon-only button with an accessible name. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(36.dp)
            .glass(CircleShape, tint = BeakoColors.Rim)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = BeakoColors.Text, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    Row(
        modifier
            .height(44.dp)
            .glass(CircleShape, tint = BeakoColors.Rim)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Outlined.Search,
            null,
            tint = BeakoColors.TextMuted,
            modifier = Modifier.size(18.dp),
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
                cursorBrush = SolidColor(BeakoColors.Text),
                modifier =
                    Modifier.fillMaxWidth()
                        .then(
                            if (focusRequester != null) Modifier.focusRequester(focusRequester)
                            else Modifier,
                        )
                        .semantics { contentDescription = placeholder },
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Outlined.Close,
                "Clear search",
                tint = BeakoColors.TextMuted,
                modifier =
                    Modifier.size(18.dp).clip(CircleShape).clickable(role = Role.Button) {
                        onValueChange("")
                    },
            )
        }
    }
}

/** A notice across the top of a screen, with an optional action. */
@Composable
fun Banner(
    message: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(16.dp), tint = BeakoColors.Rim)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Outlined.ErrorOutline,
            null,
            tint = BeakoColors.TextMuted,
            modifier = Modifier.size(18.dp),
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = BeakoColors.Text,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** A centred message for empty and error states, with an optional action below. */
@Composable
fun StateMessage(
    title: String,
    detail: String = "",
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = BeakoColors.Text)
        if (detail.isNotEmpty()) {
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = BeakoColors.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(420.dp),
            )
        }
        if (action != null) {
            Spacer(Modifier.height(8.dp))
            action()
        }
    }
}

/** A grey block shown where content is still loading. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, corner: Int = 12) {
    Box(modifier.clip(RoundedCornerShape(corner.dp)).background(Color(0x14FFFFFF)))
}

/** A small label pill, e.g. "Manhwa" or "★ 8.5". */
@Composable
fun Chip(text: String, modifier: Modifier = Modifier, ink: Color = BeakoColors.Text) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = ink,
        modifier =
            modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x24FFFFFF))
                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(6.dp))
                .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}
