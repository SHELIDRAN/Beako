package org.nekomanga.desktop.components

import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.selected

/** Marks an item as the selected one of its group, for screen readers and tests. */
fun SemanticsPropertyReceiver.selectedState(isSelected: Boolean) {
    selected = isSelected
}
