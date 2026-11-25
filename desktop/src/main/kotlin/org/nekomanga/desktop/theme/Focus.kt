package org.nekomanga.desktop.theme

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp

/**
 * Whether the user is moving around with the keyboard (like a browser's :focus-visible): Tab and
 * the arrow keys turn it on, any mouse click turns it off. Focus rings and the menu's open-on-focus
 * only follow focus while it's on, so clicking a control never leaves a ring or keeps the menu
 * open.
 */
object KeyboardNavigation {
    var active by androidx.compose.runtime.mutableStateOf(false)
}

/**
 * The keyboard focus ring every Beako Glass control shows: 2 px of the `focus` colour, 2 px outside
 * the control's [shape]. It only appears with keyboard focus (a mouse click doesn't move focus).
 * Put it before any clip in the modifier chain, so the ring isn't clipped away.
 */
@Composable
fun Modifier.focusRing(interaction: InteractionSource, shape: Shape): Modifier {
    val focused by interaction.collectIsFocusedAsState()
    val color = BeakoColors.Focus
    val keyboard = KeyboardNavigation.active
    return drawWithContent {
        drawContent()
        if (focused && keyboard) {
            val width = 2.dp.toPx()
            // The stroke is centred on the outline: offset 2 px plus half the stroke.
            val out = 2.dp.toPx() + width / 2
            val outline =
                shape.createOutline(
                    Size(size.width + out * 2, size.height + out * 2),
                    layoutDirection,
                    this,
                )
            translate(-out, -out) { drawOutline(outline, color, style = Stroke(width)) }
        }
    }
}
