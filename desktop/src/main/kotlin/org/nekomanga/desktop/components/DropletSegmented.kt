package org.nekomanga.desktop.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.desktop.theme.Droplet
import org.nekomanga.desktop.theme.dropletSpec
import org.nekomanga.desktop.theme.glass

/**
 * A segmented switch whose selection is a glass droplet that slides between the options, e.g. 7d ·
 * 1m · 3m or Hot · New.
 */
@Composable
fun DropletSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    optionWidth: Dp = 64.dp,
) {
    val dropletX by animateDpAsState(optionWidth * selectedIndex, dropletSpec())
    Box(
        modifier
            .glass(CircleShape, tint = BeakoColors.Rim, blur = 20.dp)
            .padding(4.dp)
            .selectableGroup()
            .semantics { contentDescription = label },
    ) {
        Droplet(Modifier.offset(x = dropletX).width(optionWidth).height(32.dp))
        Row {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                Box(
                    Modifier.width(optionWidth)
                        .height(32.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.RadioButton,
                            onClickLabel = option,
                        ) {
                            onSelect(index)
                        }
                        .semantics { this.selectedState(selected) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) BeakoColors.Text else BeakoColors.TextMuted,
                    )
                }
            }
        }
    }
}
