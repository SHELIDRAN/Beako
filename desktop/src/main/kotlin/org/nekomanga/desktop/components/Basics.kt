package org.nekomanga.desktop.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.desktop.theme.LocalAppearance
import org.nekomanga.desktop.theme.Motion
import org.nekomanga.desktop.theme.ShadowSpec
import org.nekomanga.desktop.theme.Sizes
import org.nekomanga.desktop.theme.dropletSpec
import org.nekomanga.desktop.theme.easeOutSpec
import org.nekomanga.desktop.theme.glass
import org.nekomanga.desktop.theme.press
import org.nekomanga.desktop.theme.sweepSpec

/**
 * A pill button on glass. [primary] makes it the solid, high-emphasis action (text fill, ground
 * ink); [accent] the orange one, for the single action a screen exists for. A view has a primary
 * *or* an accent, not both.
 */
@Composable
fun GlassButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    accent: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = CircleShape
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val glow by animateFloatAsState(if (accent && hovered && enabled) 1f else 0f, easeOutSpec(200))
    val base =
        when {
            accent ->
                Modifier.graphicsLayer { translationY = -1.dp.toPx() * glow }
                    .dropShadow(shape, ShadowSpec.accentGlow(glow))
                    .clip(shape)
                    .background(BeakoColors.AccentFill)
            primary -> Modifier.clip(shape).background(BeakoColors.Text)
            else -> Modifier.glass(shape, tint = BeakoColors.Rim)
        }
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .press(interaction, enabled)
            .defaultMinSize(minHeight = Sizes.controlMd)
            .then(base)
            .hoverable(interaction, enabled)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val ink =
            when {
                accent -> BeakoColors.AccentInk
                primary -> BeakoColors.Ground
                else -> BeakoColors.Text
            }
        if (icon != null) Icon(icon, null, tint = ink, modifier = Modifier.size(Sizes.iconSm))
        Text(label, style = MaterialTheme.typography.labelLarge, color = ink)
    }
}

/**
 * An icon-only button with an accessible name. With [checked] it is a toggle: an ink circle
 * **rises** from below when it turns on and leaves through the top when it turns off.
 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    checked: Boolean? = null,
    /**
     * Swing: on hover or focus the arrow drops in turned by this many degrees (90 for a forward
     * arrow, -90 for a back one) and swings to rest on the droplet spring. 0 keeps it still.
     */
    swingFrom: Float = 0f,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val swing = remember { Animatable(0f) }
    val swingSpec = dropletSpec<Float>(stiffness = Spring.StiffnessLow)
    val reduceMotion = LocalAppearance.current.reduceMotion
    LaunchedEffect(hovered || focused) {
        if ((hovered || focused) && swingFrom != 0f && !reduceMotion) {
            swing.snapTo(swingFrom)
            swing.animateTo(0f, swingSpec)
        }
    }
    Box(
        modifier
            .press(interaction)
            .size(Sizes.controlMd)
            .glass(CircleShape, tint = BeakoColors.Rim)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = if (checked == null) Role.Button else Role.Switch,
                onClick = onClick,
            )
            .semantics {
                contentDescription = description
                if (checked != null) stateDescription = if (checked) "On" else "Off"
            },
        contentAlignment = Alignment.Center,
    ) {
        val on = checked == true
        if (checked != null) RiseInk(on, BeakoColors.InkFill)
        Icon(
            icon,
            null,
            tint = if (on) BeakoColors.InkOnFill else BeakoColors.Text,
            modifier = Modifier.size(Sizes.iconMd).graphicsLayer { rotationZ = swing.value },
        )
    }
}

private class RiseMemory(var last: Boolean, var exitingUp: Boolean = false)

/**
 * Rise: a circle of [color] that fills its parent from below when [on], and carries on up and out
 * through the top when it turns off.
 */
@Composable
internal fun BoxScope.RiseInk(on: Boolean, color: Color) {
    // 0 = below (hidden), 1 = covering, 2 = gone through the top.
    val target = if (on) 1f else 0f
    // Which way the ink leaves depends on the change, so remember the last state (not as Compose
    // state: it only steers drawing and must not trigger recomposition).
    val memory = remember { RiseMemory(on) }
    if (memory.last != on) {
        memory.exitingUp = memory.last
        memory.last = on
    }
    val exitingUp = memory.exitingUp
    val progress by animateFloatAsState(target, sweepSpec(Motion.RISE), label = "rise")
    Box(
        Modifier.matchParentSize().clip(CircleShape).drawBehind {
            val y =
                if (on || !exitingUp) size.height * (1f - progress) // rising in, or dropping back
                else -size.height * (1f - progress) // leaving through the top
            if (progress > 0f) {
                drawCircle(
                    color,
                    radius = size.maxDimension * 0.75f,
                    center = Offset(size.width / 2, size.height / 2 + y),
                )
            }
        },
    )
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
    Box(modifier.clip(RoundedCornerShape(corner.dp)).background(BeakoColors.ChipRim))
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
                .background(BeakoColors.ChipFill)
                .border(1.dp, BeakoColors.ChipRim, RoundedCornerShape(6.dp))
                .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** A labelled single-line text box on glass. [secret] hides what is typed. */
@Composable
fun GlassTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = BeakoColors.TextMuted)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = BeakoColors.Text),
            cursorBrush = SolidColor(BeakoColors.Text),
            visualTransformation =
                if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation()
                else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier =
                Modifier.fillMaxWidth()
                    .height(40.dp)
                    .glass(RoundedCornerShape(12.dp), tint = BeakoColors.Rim)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .semantics { contentDescription = label },
        )
    }
}
