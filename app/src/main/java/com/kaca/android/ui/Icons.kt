package com.kaca.android.ui

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@androidx.compose.runtime.Stable
private fun icon(
    name: String,
    block: PathBuilder.() -> Unit
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(name = "", pathBuilder = block)
}.build()

val PhoneIcon = icon("phone") {
    moveTo(7f, 2f); lineTo(17f, 2f)
    curveTo(17.55f, 2f, 18f, 2.45f, 18f, 3f); lineTo(18f, 21f)
    curveTo(18f, 21.55f, 17.55f, 22f, 17f, 22f); lineTo(7f, 22f)
    curveTo(6.45f, 22f, 6f, 21.55f, 6f, 21f); lineTo(6f, 3f)
    curveTo(6f, 2.45f, 6.45f, 2f, 7f, 2f); close()
    moveTo(7f, 4f); lineTo(7f, 17f); lineTo(17f, 17f); lineTo(17f, 4f); close()
    moveTo(12f, 19f)
    curveTo(11.45f, 19f, 11f, 18.55f, 11f, 18f)
    curveTo(11f, 17.45f, 11.45f, 17f, 12f, 17f)
    curveTo(12.55f, 17f, 13f, 17.45f, 13f, 18f)
    curveTo(13f, 18.55f, 12.55f, 19f, 12f, 19f); close()
}

val LaptopIcon = icon("laptop") {
    moveTo(4f, 6f); lineTo(20f, 6f)
    curveTo(20.55f, 6f, 21f, 6.45f, 21f, 7f); lineTo(21f, 16f)
    curveTo(21f, 16.55f, 20.55f, 17f, 20f, 17f); lineTo(4f, 17f)
    curveTo(3.45f, 17f, 3f, 16.55f, 3f, 16f); lineTo(3f, 7f)
    curveTo(3f, 6.45f, 3.45f, 6f, 4f, 6f); close()
    moveTo(5f, 8f); lineTo(5f, 15f); lineTo(19f, 15f); lineTo(19f, 8f); close()
    moveTo(2f, 20f); lineTo(22f, 20f); lineTo(22f, 19f)
    curveTo(22f, 18.45f, 21.55f, 18f, 21f, 18f); lineTo(3f, 18f)
    curveTo(2.45f, 18f, 2f, 18.45f, 2f, 19f); close()
}

val MonitorIcon = icon("monitor") {
    moveTo(4f, 5f); lineTo(20f, 5f)
    curveTo(21.1f, 5f, 22f, 5.9f, 22f, 7f); lineTo(22f, 17f)
    curveTo(22f, 18.1f, 21.1f, 19f, 20f, 19f); lineTo(4f, 19f)
    curveTo(2.9f, 19f, 2f, 18.1f, 2f, 17f); lineTo(2f, 7f)
    curveTo(2f, 5.9f, 2.9f, 5f, 4f, 5f); close()
    moveTo(4f, 7f); lineTo(4f, 17f); lineTo(20f, 17f); lineTo(20f, 7f); close()
    moveTo(7f, 19f); lineTo(17f, 19f); lineTo(17f, 21f); lineTo(7f, 21f); close()
}

val CameraIcon = icon("camera") {
    moveTo(12f, 9f)
    curveTo(9.79f, 9f, 8f, 10.79f, 8f, 13f)
    curveTo(8f, 15.21f, 9.79f, 17f, 12f, 17f)
    curveTo(14.21f, 17f, 16f, 15.21f, 16f, 13f)
    curveTo(16f, 10.79f, 14.21f, 9f, 12f, 9f); close()
    moveTo(20f, 7f); lineTo(17.41f, 7f); lineTo(17.11f, 6.41f)
    curveTo(16.93f, 6.07f, 16.57f, 5.85f, 16.17f, 5.83f); lineTo(7.83f, 5.83f)
    curveTo(7.43f, 5.85f, 7.07f, 6.07f, 6.89f, 6.41f); lineTo(6.59f, 7f); lineTo(4f, 7f)
    curveTo(2.9f, 7f, 2f, 7.9f, 2f, 9f); lineTo(2f, 17f)
    curveTo(2f, 18.1f, 2.9f, 19f, 4f, 19f); lineTo(20f, 19f)
    curveTo(21.1f, 19f, 22f, 18.1f, 22f, 17f); lineTo(22f, 9f)
    curveTo(22f, 7.9f, 21.1f, 7f, 20f, 7f); close()
    moveTo(4f, 17f); lineTo(4f, 9f); lineTo(7.05f, 9f); lineTo(7.4f, 8.41f)
    curveTo(7.56f, 8.16f, 7.84f, 8f, 8.15f, 8f); lineTo(15.85f, 8f)
    curveTo(16.16f, 8f, 16.44f, 8.16f, 16.6f, 8.41f); lineTo(16.95f, 9f); lineTo(20f, 9f)
    lineTo(20f, 17f); close()
}

val WifiIcon = icon("wifi") {
    moveTo(1f, 7f)
    curveTo(5.14f, 2.86f, 12.86f, 2.86f, 17f, 7f)
    lineTo(15.5f, 8.5f)
    curveTo(12.18f, 5.18f, 5.82f, 5.18f, 2.5f, 8.5f); close()
    moveTo(4f, 10f)
    curveTo(6.86f, 7.14f, 11.14f, 7.14f, 14f, 10f)
    lineTo(12.5f, 11.5f)
    curveTo(10.74f, 9.74f, 7.26f, 9.74f, 5.5f, 11.5f); close()
    moveTo(7f, 13f)
    curveTo(8.83f, 11.17f, 11.17f, 11.17f, 13f, 13f)
    lineTo(12f, 14f); lineTo(11f, 13f); close()
}

val ClockIcon = icon("clock") {
    moveTo(12f, 2f)
    curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
    curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
    curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
    curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f); close()
    moveTo(12f, 20f); curveTo(7.59f, 20f, 4f, 16.41f, 4f, 12f)
    curveTo(4f, 7.59f, 7.59f, 4f, 12f, 4f)
    curveTo(16.41f, 4f, 20f, 7.59f, 20f, 12f)
    curveTo(20f, 16.41f, 16.41f, 20f, 12f, 20f); close()
    moveTo(12.5f, 7f); lineTo(11f, 7f); lineTo(11f, 13f)
    lineTo(16.25f, 16.15f); lineTo(17f, 14.92f); lineTo(12.5f, 12.25f); close()
}

val GaugeIcon = icon("gauge") {
    moveTo(12f, 2f)
    curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
    curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
    curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
    curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f); close()
    moveTo(12f, 20f); curveTo(7.59f, 20f, 4f, 16.41f, 4f, 12f)
    curveTo(4f, 7.59f, 7.59f, 4f, 12f, 4f)
    curveTo(16.41f, 4f, 20f, 7.59f, 20f, 12f)
    curveTo(20f, 16.41f, 16.41f, 20f, 12f, 20f); close()
    moveTo(10f, 11f); curveTo(10f, 10.45f, 10.45f, 10f, 11f, 10f)
    curveTo(11.55f, 10f, 12f, 10.45f, 12f, 11f)
    curveTo(12f, 11.55f, 11.55f, 12f, 11f, 12f)
    curveTo(10.45f, 12f, 10f, 11.55f, 10f, 11f); close()
    moveTo(14f, 11f); curveTo(14f, 10.45f, 14.45f, 10f, 15f, 10f)
    curveTo(15.55f, 10f, 16f, 10.45f, 16f, 11f)
    curveTo(16f, 11.55f, 15.55f, 12f, 15f, 12f)
    curveTo(14.45f, 12f, 14f, 11.55f, 14f, 11f); close()
    moveTo(7f, 13f); curveTo(7f, 12.45f, 7.45f, 12f, 8f, 12f)
    curveTo(8.55f, 12f, 9f, 12.45f, 9f, 13f)
    curveTo(9f, 13.55f, 8.55f, 14f, 8f, 14f)
    curveTo(7.45f, 14f, 7f, 13.55f, 7f, 13f); close()
    moveTo(17f, 13f); curveTo(17f, 12.45f, 17.45f, 12f, 18f, 12f)
    curveTo(18.55f, 12f, 19f, 12.45f, 19f, 13f)
    curveTo(19f, 13.55f, 18.55f, 14f, 18f, 14f)
    curveTo(17.45f, 14f, 17f, 13.55f, 17f, 13f); close()
    moveTo(8.5f, 17f); curveTo(9.74f, 15.11f, 11.86f, 14f, 14f, 14f)
    curveTo(16.14f, 14f, 18.26f, 15.11f, 19.5f, 17f); lineTo(18f, 17f)
    curveTo(17.05f, 15.68f, 15.52f, 15f, 14f, 15f)
    curveTo(12.48f, 15f, 10.95f, 15.68f, 10f, 17f); close()
}

val FlashIcon = icon("flash") {
    moveTo(13f, 3f); lineTo(3f, 14f); lineTo(12f, 14f)
    lineTo(11f, 21f); lineTo(21f, 10f); lineTo(12f, 10f); close()
}
