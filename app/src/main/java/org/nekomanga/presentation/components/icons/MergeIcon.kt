package org.nekomanga.presentation.components.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val MergeIcon: ImageVector
    get() {
        if (_IconName != null) {
            return _IconName!!
        }
        _IconName =
            ImageVector.Builder(
                    name = "IconName",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 24f,
                    viewportHeight = 24f,
                )
                .apply {
                    path(fill = SolidColor(Color.Black)) {
                        moveTo(13f, 14f)
                        curveTo(9.64f, 14f, 8.54f, 15.35f, 8.18f, 16.24f)
                        curveTo(9.25f, 16.7f, 10f, 17.76f, 10f, 19f)
                        arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 7f, 22f)
                        arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4f, 19f)
                        curveTo(4f, 17.69f, 4.83f, 16.58f, 6f, 16.17f)
                        verticalLineTo(7.83f)
                        curveTo(4.83f, 7.42f, 4f, 6.31f, 4f, 5f)
                        arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 7f, 2f)
                        arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 10f, 5f)
                        curveTo(10f, 6.31f, 9.17f, 7.42f, 8f, 7.83f)
                        verticalLineTo(13.12f)
                        curveTo(8.88f, 12.47f, 10.16f, 12f, 12f, 12f)
                        curveTo(14.67f, 12f, 15.56f, 10.66f, 15.85f, 9.77f)
                        curveTo(14.77f, 9.32f, 14f, 8.25f, 14f, 7f)
                        arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 17f, 4f)
                        arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 7f)
                        curveTo(20f, 8.34f, 19.12f, 9.5f, 17.91f, 9.86f)
                        curveTo(17.65f, 11.29f, 16.68f, 14f, 13f, 14f)
                        moveTo(7f, 18f)
                        arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 6f, 19f)
                        arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 7f, 20f)
                        arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 8f, 19f)
                        arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 7f, 18f)
                        moveTo(7f, 4f)
                        arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 6f, 5f)
                        arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 7f, 6f)
                        arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 8f, 5f)
                        arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 7f, 4f)
                        moveTo(17f, 6f)
                        arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 16f, 7f)
                        arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 17f, 8f)
                        arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 18f, 7f)
                        arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 17f, 6f)
                        close()
                    }
                }
                .build()

        return _IconName!!
    }

@Suppress("ObjectPropertyName") private var _IconName: ImageVector? = null
