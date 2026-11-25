package com.clouddroidide.android.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom crisp Vector icons replacing any Unicode emojis or generic placeholders.
 */
object StudioIcons {
    val CloudDroidLogo: ImageVector by lazy {
        ImageVector.Builder(
            name = "CloudDroidLogo",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF58A6FF))) {
                moveTo(6.5f, 19f)
                horizontalLineTo(17.5f)
                curveTo(19.99f, 19f, 22f, 16.99f, 22f, 14.5f)
                curveTo(22f, 12.14f, 20.19f, 10.21f, 17.88f, 10.02f)
                curveTo(17.07f, 6.57f, 13.98f, 4f, 10.3f, 4f)
                curveTo(6.18f, 4f, 2.85f, 7.21f, 2.52f, 11.24f)
                curveTo(1.07f, 11.98f, 0.5f, 13.56f, 0.5f, 15f)
                curveTo(0.5f, 17.21f, 2.29f, 19f, 4.5f, 19f)
                horizontalLineTo(6.5f)
                close()
            }
            path(fill = SolidColor(Color(0xFF0E1116))) {
                moveTo(9.2f, 10.2f)
                lineTo(6.8f, 12.5f)
                lineTo(9.2f, 14.8f)
                lineTo(10.2f, 13.8f)
                lineTo(8.8f, 12.5f)
                lineTo(10.2f, 11.2f)
                close()
                moveTo(14.8f, 10.2f)
                lineTo(13.8f, 11.2f)
                lineTo(15.2f, 12.5f)
                lineTo(13.8f, 13.8f)
                lineTo(14.8f, 14.8f)
                lineTo(17.2f, 12.5f)
                close()
            }
        }.build()
    }

    val GitBranch: ImageVector by lazy {
        ImageVector.Builder(
            name = "GitBranch",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(6f, 3f)
                curveTo(4.34f, 3f, 3f, 4.34f, 3f, 6f)
                curveTo(3f, 7.3f, 3.84f, 8.4f, 5f, 8.82f)
                verticalLineTo(15.18f)
                curveTo(3.84f, 15.6f, 3f, 16.7f, 3f, 18f)
                curveTo(3f, 19.66f, 4.34f, 21f, 6f, 21f)
                curveTo(7.66f, 21f, 9f, 19.66f, 9f, 18f)
                curveTo(9f, 16.7f, 8.16f, 15.6f, 7f, 15.18f)
                verticalLineTo(13f)
                horizontalLineTo(14f)
                curveTo(16.21f, 13f, 18f, 11.21f, 18f, 9f)
                verticalLineTo(8.82f)
                curveTo(19.16f, 8.4f, 20f, 7.3f, 20f, 6f)
                curveTo(20f, 4.34f, 18.66f, 3f, 17f, 3f)
                curveTo(15.34f, 3f, 14f, 4.34f, 14f, 6f)
                curveTo(14f, 7.3f, 14.84f, 8.4f, 16f, 8.82f)
                verticalLineTo(9f)
                curveTo(16f, 10.1f, 15.1f, 11f, 14f, 11f)
                horizontalLineTo(7f)
                verticalLineTo(8.82f)
                curveTo(8.16f, 8.4f, 9f, 7.3f, 9f, 6f)
                curveTo(9f, 4.34f, 7.66f, 3f, 6f, 3f)
                close()
                moveTo(6f, 5f)
                curveTo(6.55f, 5f, 7f, 5.45f, 7f, 6f)
                reflectiveCurveTo(6.55f, 7f, 6f, 7f)
                reflectiveCurveTo(5f, 6.55f, 5f, 6f)
                reflectiveCurveTo(5.45f, 5f, 6f, 5f)
                close()
                moveTo(17f, 5f)
                curveTo(17.55f, 5f, 18f, 5.45f, 18f, 6f)
                reflectiveCurveTo(17.55f, 7f, 17f, 7f)
                reflectiveCurveTo(16f, 6.55f, 16f, 6f)
                reflectiveCurveTo(16.45f, 5f, 17f, 5f)
                close()
                moveTo(6f, 17f)
                curveTo(6.55f, 17f, 7f, 17.45f, 7f, 18f)
                reflectiveCurveTo(6.55f, 19f, 6f, 19f)
                reflectiveCurveTo(5f, 18.55f, 5f, 18f)
                reflectiveCurveTo(5.45f, 17f, 6f, 17f)
                close()
            }
        }.build()
    }
}
